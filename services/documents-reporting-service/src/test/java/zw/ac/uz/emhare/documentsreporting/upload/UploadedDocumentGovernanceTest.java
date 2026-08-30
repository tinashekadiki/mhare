package zw.ac.uz.emhare.documentsreporting.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.documentsreporting.document.DocumentsStorageProperties;
import zw.ac.uz.emhare.documentsreporting.integration.DocumentVerificationOutboxService;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.UploadedDocument;
import zw.ac.uz.emhare.documentsreporting.upload.infrastructure.persistence.UploadedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.upload.ocr.DocumentOcrService;

/** Upload ownership, replacement and storage rollback boundaries. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class UploadedDocumentGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-12T08:00:00Z");
  @Mock private UploadedDocumentRepository repository;
  @Mock private DocumentContentInspector inspector;
  @Mock private MalwareScanner scanner;
  @Mock private DocumentVerificationOutboxService outbox;
  @Mock private EmhareCurrentUserResolver users;
  @Mock private S3Client s3;
  @Mock private S3Presigner presigner;
  @Mock private DocumentOcrService ocr;
  private UploadedDocumentService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();
  private EmhareCurrentUser user;
  private UploadedDocument document;

  @BeforeEach
  void setUp() {
    user = user(actor, "applicant");
    service = service(300);
    document =
        new UploadedDocument(
            UploadedDocument.OwnerType.APPLICATION,
            owner,
            "IDENTITY",
            "identity.pdf",
            "documents",
            "uploads/identity.pdf",
            "v1",
            "application/pdf",
            3,
            "checksum",
            actor,
            NOW,
            null);
    ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
    lenient().when(users.requireCurrentUser()).thenAnswer(invocation -> user);
    lenient()
        .when(repository.findByIdAndDeletedAtIsNull(document.getId()))
        .thenReturn(Optional.of(document));
    lenient()
        .when(repository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              UploadedDocument row = invocation.getArgument(0);
              if (row.getId() == null) ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
              return row;
            });
    lenient()
        .when(inspector.inspect(any()))
        .thenReturn(new DocumentContentInspector.InspectedContent("application/pdf", "pdf"));
    lenient().when(scanner.scan(any())).thenReturn(MalwareScanner.ScanResult.safe());
    lenient()
        .when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().versionId("object-v1").build());
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.clearSynchronization();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void documentRegisterSelectsCallerOwnedOrAdministratorScope(boolean admin, boolean scoped) {
    if (admin) user = user(UUID.randomUUID(), "admissions-officer");
    if (scoped && admin)
      when(repository.findAllByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(
              UploadedDocument.OwnerType.APPLICATION, owner))
          .thenReturn(List.of(document));
    if (scoped && !admin)
      when(repository
              .findAllByOwnerTypeAndOwnerIdAndUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(
                  UploadedDocument.OwnerType.APPLICATION, owner, actor))
          .thenReturn(List.of(document));
    if (!scoped && admin)
      when(repository.findAllByDeletedAtIsNullOrderByUploadedAtDesc())
          .thenReturn(List.of(document));
    if (!scoped && !admin)
      when(repository.findAllByUploadedByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(actor))
          .thenReturn(List.of(document));
    var listed = service.documents(scoped ? "application" : null, scoped ? owner : null);
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).id()).isEqualTo(document.getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"type", "id"})
  void ownerFilteringRequiresBothPartsOfTheOwnerKey(String missing) {
    assertThatThrownBy(
            () ->
                service.documents(
                    missing.equals("type") ? null : "APPLICATION",
                    missing.equals("id") ? null : owner))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supplied together");
  }

  @Test
  void callerWithoutStableIdentityCannotListOrUploadDocuments() {
    user =
        new EmhareCurrentUser(
            null, null, "applicant@example.test", "applicant", "Applicant", Set.of("applicant"));
    assertThatThrownBy(() -> service.documents(null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stable identifier");
    assertThatThrownBy(() -> service.upload("APPLICATION", owner, "IDENTITY", null, file()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stable identifier");
    verifyNoInteractions(s3);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "UNKNOWN"})
  void unsupportedOwnerTypeCannotUploadEvidence(String type) {
    assertThatThrownBy(() -> service.upload(type, owner, "IDENTITY", null, file()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner type");
    verifyNoInteractions(s3);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void documentTypeCannotBeMissing(String code) {
    assertThatThrownBy(() -> service.upload("APPLICATION", owner, code, null, file()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type code is required");
    verifyNoInteractions(s3);
  }

  @Test
  void oversizedDocumentTypeCannotBeStored() {
    assertThatThrownBy(() -> service.upload("APPLICATION", owner, "A".repeat(81), null, file()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too long");
    verifyNoInteractions(s3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "blank", "long", "path"})
  void uploadedFilenameIsBoundedAndCannotInjectPathsOrResponseHeaders(String variant)
      throws IOException {
    MultipartFile upload = mock(MultipartFile.class);
    String fileName =
        switch (variant) {
          case "null" -> null;
          case "blank" -> " ";
          case "long" -> "x".repeat(300);
          default -> "C:\\folder\\identity\"\n.pdf";
        };
    when(upload.getOriginalFilename()).thenReturn(fileName);
    when(upload.getSize()).thenReturn(3L);
    when(upload.getBytes()).thenReturn(new byte[] {1, 2, 3});
    var saved = service.upload("APPLICATION", owner, " identity proof ", null, upload);
    assertThat(saved.originalFileName())
        .isEqualTo(
            switch (variant) {
              case "null", "blank" -> "document";
              case "long" -> "x".repeat(255);
              default -> "identity__.pdf";
            });
    assertThat(saved.documentTypeCode()).isEqualTo("IDENTITY_PROOF");
    assertThat(saved.mimeType()).isEqualTo("application/pdf");
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "zeroBytes", "unreadable", "oversized"})
  void invalidMultipartContentCannotReachInspectionOrStorage(String invalid) throws IOException {
    MultipartFile upload = mock(MultipartFile.class);
    when(upload.isEmpty()).thenReturn(invalid.equals("empty"));
    if (!invalid.equals("empty"))
      when(upload.getSize()).thenReturn(invalid.equals("oversized") ? 1000001L : 1L);
    if (invalid.equals("zeroBytes")) when(upload.getBytes()).thenReturn(new byte[0]);
    if (invalid.equals("unreadable"))
      when(upload.getBytes()).thenThrow(new IOException("Read failed"));
    assertThatThrownBy(() -> service.upload("APPLICATION", owner, "IDENTITY", null, upload))
        .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);
    verifyNoInteractions(inspector, scanner, s3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ownerType", "ownerId", "documentType", "verified"})
  void replacementMustMatchThePriorDocumentScopeAndCannotReplaceVerifiedEvidence(String invalid) {
    if (invalid.equals("verified")) document.verify(actor, "Verified", 0, NOW);
    assertThatThrownBy(
            () ->
                service.upload(
                    invalid.equals("ownerType") ? "APPLICANT" : "APPLICATION",
                    invalid.equals("ownerId") ? UUID.randomUUID() : owner,
                    invalid.equals("documentType") ? "PASSPORT" : "IDENTITY",
                    document.getId(),
                    file()))
        .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class);
    verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void rejectedEvidenceCanBeReplacedAndKeepsItsPredecessorIdentifier() {
    document.reject(actor, "Identity is unreadable", 0, NOW);
    var replacement = service.upload("APPLICATION", owner, "IDENTITY", document.getId(), file());
    assertThat(replacement.replacesDocumentId()).isEqualTo(document.getId());
    assertThat(replacement.verificationStatus())
        .isEqualTo(UploadedDocument.VerificationStatus.PENDING);
  }

  @Test
  void foreignApplicantCannotReadDownloadReplaceOrVerifyAnotherApplicantsDocument() {
    user = user(UUID.randomUUID(), "applicant");
    assertThatThrownBy(() -> service.document(document.getId()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.download(document.getId(), "inline"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> service.upload("APPLICATION", owner, "IDENTITY", document.getId(), file()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.verify(document.getId(), 0, "Verified"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.reject(document.getId(), 0, "Unreadable"))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(presigner, outbox);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "system-admin",
        "academic-admin",
        "admissions-officer",
        "registry-officer",
        "finance-officer"
      })
  void designatedDocumentAdministratorsCanReadAndRecordVerificationEvidence(String role) {
    UUID verifier = UUID.randomUUID();
    user = user(verifier, role);
    assertThat(service.document(document.getId()).id()).isEqualTo(document.getId());
    var verified = service.verify(document.getId(), 0, "Identity checked");
    assertThat(verified.verifiedByUserId()).isEqualTo(verifier);
    assertThat(verified.verificationStatus())
        .isEqualTo(UploadedDocument.VerificationStatus.VERIFIED);
    verify(outbox).enqueue(document);
  }

  @Test
  void unknownDocumentIsNotDisclosedAsAnAccessibleRecord() {
    assertThatThrownBy(() -> service.document(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  @ParameterizedTest
  @CsvSource({"1,60", "300,300", "10000,3600"})
  void signedDownloadsUseBoundedExpiryAndExplicitInlineDisposition(
      long configuredSeconds, long expectedSeconds) throws Exception {
    service = service(configuredSeconds);
    PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
    when(signed.url())
        .thenReturn(URI.create("https://storage.example.test/signed-download").toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
    var download = service.download(document.getId(), "INLINE");
    ArgumentCaptor<GetObjectPresignRequest> request =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(presigner).presignGetObject(request.capture());
    assertThat(request.getValue().signatureDuration())
        .isEqualTo(Duration.ofSeconds(expectedSeconds));
    assertThat(request.getValue().getObjectRequest().responseContentDisposition())
        .isEqualTo("inline; filename=\"identity.pdf\"");
    assertThat(download.expiresAt()).isEqualTo(NOW.plusSeconds(expectedSeconds));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "ATTACHMENT"})
  void defaultDownloadDispositionIsAttachment(String disposition) throws Exception {
    PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
    when(signed.url())
        .thenReturn(URI.create("https://storage.example.test/signed-download").toURL());
    when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
    service.download(document.getId(), disposition);
    ArgumentCaptor<GetObjectPresignRequest> request =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(presigner).presignGetObject(request.capture());
    assertThat(request.getValue().getObjectRequest().responseContentDisposition())
        .startsWith("attachment;");
  }

  @Test
  void unsupportedDownloadDispositionCannotReachSigner() {
    assertThatThrownBy(() -> service.download(document.getId(), "javascript"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(presigner);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 409, 503})
  void missingBucketCanBeCreatedConcurrentlyButOtherCreationFailuresStopUpload(int createStatus) {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).message("Not found").build());
    if (createStatus != 200)
      when(s3.createBucket(any(CreateBucketRequest.class)))
          .thenThrow(
              S3Exception.builder().statusCode(createStatus).message("Create response").build());
    if (createStatus == 503) {
      assertThatThrownBy(() -> service.upload("APPLICATION", owner, "IDENTITY", null, file()))
          .isInstanceOf(S3Exception.class);
      verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    } else
      assertThat(
              service.upload("APPLICATION", owner, "IDENTITY", null, file()).verificationStatus())
          .isEqualTo(UploadedDocument.VerificationStatus.PENDING);
  }

  @Test
  void bucketAccessDeniedDoesNotTryToCreateOrUploadObjects() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(403).message("Denied").build());
    assertThatThrownBy(() -> service.upload("APPLICATION", owner, "IDENTITY", null, file()))
        .isInstanceOf(S3Exception.class);
    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
    verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void committedUploadIsRetainedAndRollbackCleanupFailureDoesNotMaskTheTransactionOutcome() {
    service.upload("APPLICATION", owner, "IDENTITY", null, file());
    var synchronizations = TransactionSynchronizationManager.getSynchronizations();
    synchronizations.forEach(
        item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    doThrow(new IllegalStateException("Object store unavailable"))
        .when(s3)
        .deleteObject(any(DeleteObjectRequest.class));
    synchronizations.forEach(
        item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verify(s3).deleteObject(any(DeleteObjectRequest.class));
  }

  private MockMultipartFile file() {
    return new MockMultipartFile("file", "identity.pdf", "application/pdf", new byte[] {1, 2, 3});
  }

  private EmhareCurrentUser user(UUID id, String role) {
    return new EmhareCurrentUser(
        id, id, "applicant@example.test", "applicant", "Applicant", Set.of(role));
  }

  private UploadedDocumentService service(long validity) {
    return new UploadedDocumentService(
        repository,
        inspector,
        scanner,
        outbox,
        users,
        s3,
        presigner,
        new DocumentsStorageProperties(
            "http://storage.test",
            "us-east-1",
            "test-access",
            "test-secret",
            "documents",
            true,
            validity,
            1000000),
        ocr,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
