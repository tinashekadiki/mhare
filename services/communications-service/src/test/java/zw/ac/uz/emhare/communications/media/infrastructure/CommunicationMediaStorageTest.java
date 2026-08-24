package zw.ac.uz.emhare.communications.media.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Covers the S3-compatible public media adapter. @author Tinashe K */
class CommunicationMediaStorageTest {

  @Test
  void createsMissingBucketAndStoresAndReadsValidatedMedia() {
    S3Client s3Client = mock(S3Client.class);
    doThrow(S3Exception.builder().statusCode(404).message("missing").build())
        .when(s3Client)
        .headBucket(any(HeadBucketRequest.class));
    byte[] bytes = "image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
    CommunicationMediaStorage storage = new CommunicationMediaStorage(s3Client, properties(10));

    storage.ensureBucket();
    storage.store("public/poster.png", "image/png", bytes);
    assertArrayEquals(bytes, storage.read("public/poster.png"));
    verify(s3Client)
        .putObject(
            any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store("public/large.png", "image/png", new byte[11]));
  }

  @Test
  void toleratesUnavailableStartupStorageButSurfacesWriteErrorsAndUsesDefaultLimit() {
    S3Client s3Client = mock(S3Client.class);
    doThrow(S3Exception.builder().statusCode(503).message("offline").build())
        .when(s3Client)
        .headBucket(any(HeadBucketRequest.class));
    CommunicationMediaStorage storage = new CommunicationMediaStorage(s3Client, properties(0));

    storage.ensureBucket();
    assertEquals(10_485_760L, storage.maximumBytes());
    assertThrows(
        S3Exception.class, () -> storage.store("public/poster.png", "image/png", new byte[] {1}));
  }

  private CommunicationMediaStorageProperties properties(long maximumBytes) {
    return new CommunicationMediaStorageProperties(
        "emhare-communications",
        URI.create("http://localhost:9000"),
        "us-east-1",
        "access",
        "secret",
        true,
        maximumBytes);
  }
}
