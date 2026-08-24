package zw.ac.uz.emhare.communications.content.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.CategoryRequest;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.CategoryResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.CreateDraftRequest;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.DecisionRequest;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.EditVersionRequest;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.EditorialDetailResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.EditorialItemResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.EditorialPageResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.ExpectedVersionRequest;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.MediaResponse;
import zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.SchedulePublicationRequest;
import zw.ac.uz.emhare.communications.content.application.CommunicationApplicationService;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.CategoryCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.CreateDraftCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.EditVersionCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.EventDetailsCommand;
import zw.ac.uz.emhare.communications.content.application.command.CommunicationCommands.SchedulePublicationCommand;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.ContentKind;

/** Searchable editorial queue and governed Communications actions. @author Tinashe K */
@Validated
@RestController
@RequestMapping("/api/communications/editorial")
@PreAuthorize(
    "hasAnyAuthority('ROLE_system-admin','ROLE_communications-author','ROLE_communications-approver')")
public class EditorialCommunicationController {

  private static final String AUTHOR =
      "hasAnyAuthority('ROLE_system-admin','ROLE_communications-author')";
  private static final String APPROVER =
      "hasAnyAuthority('ROLE_system-admin','ROLE_communications-approver')";

  private final CommunicationApplicationService service;
  private final EmhareCurrentUserResolver currentUserResolver;

  public EditorialCommunicationController(
      CommunicationApplicationService service, EmhareCurrentUserResolver currentUserResolver) {
    this.service = service;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/items")
  public EditorialPageResponse queue(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "kind", required = false) ContentKind kind,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
    return EditorialPageResponse.from(service.editorialQueue(query, kind, page, size));
  }

  @GetMapping("/items/{itemId}")
  public EditorialDetailResponse detail(@PathVariable("itemId") UUID itemId) {
    return EditorialDetailResponse.from(service.editorialDetail(itemId));
  }

  @PostMapping("/items")
  @PreAuthorize(AUTHOR)
  public EditorialItemResponse createDraft(
      Authentication authentication, @Valid @RequestBody CreateDraftRequest request) {
    CreateDraftCommand command =
        new CreateDraftCommand(
            request.kind(),
            request.slug(),
            request.categoryId(),
            request.title(),
            request.summary(),
            request.structuredContent().toString(),
            request.heroMediaAssetId(),
            request.externalUrl(),
            eventCommand(request.event()));
    return EditorialItemResponse.from(service.createDraft(command, actor(authentication)));
  }

  @PutMapping("/versions/{versionId}")
  @PreAuthorize(AUTHOR)
  public EditorialItemResponse editVersion(
      Authentication authentication,
      @PathVariable("versionId") UUID versionId,
      @Valid @RequestBody EditVersionRequest request) {
    EditVersionCommand command =
        new EditVersionCommand(
            request.title(),
            request.summary(),
            request.structuredContent().toString(),
            request.heroMediaAssetId(),
            request.externalUrl(),
            eventCommand(request.event()),
            request.expectedVersion());
    return EditorialItemResponse.from(
        service.editVersion(versionId, command, actor(authentication)));
  }

  @PostMapping("/items/{itemId}/corrections")
  @PreAuthorize(AUTHOR)
  public EditorialItemResponse createCorrection(
      Authentication authentication, @PathVariable("itemId") UUID itemId) {
    return EditorialItemResponse.from(service.createCorrection(itemId, actor(authentication)));
  }

  @PostMapping("/versions/{versionId}/submit")
  @PreAuthorize(AUTHOR)
  public EditorialItemResponse submit(
      Authentication authentication,
      @PathVariable("versionId") UUID versionId,
      @Valid @RequestBody ExpectedVersionRequest request) {
    return EditorialItemResponse.from(
        service.submit(versionId, request.expectedVersion(), actor(authentication)));
  }

  @PostMapping("/versions/{versionId}/approve")
  @PreAuthorize(APPROVER)
  public EditorialItemResponse approve(
      Authentication authentication,
      @PathVariable("versionId") UUID versionId,
      @Valid @RequestBody ExpectedVersionRequest request) {
    return EditorialItemResponse.from(
        service.approve(versionId, request.expectedVersion(), actor(authentication)));
  }

  @PostMapping("/versions/{versionId}/reject")
  @PreAuthorize(APPROVER)
  public EditorialItemResponse reject(
      Authentication authentication,
      @PathVariable("versionId") UUID versionId,
      @Valid @RequestBody DecisionRequest request) {
    return EditorialItemResponse.from(
        service.reject(
            versionId, request.expectedVersion(), request.reason(), actor(authentication)));
  }

  @PostMapping("/versions/{versionId}/publications")
  @PreAuthorize(APPROVER)
  public EditorialItemResponse schedule(
      Authentication authentication,
      @PathVariable("versionId") UUID versionId,
      @Valid @RequestBody SchedulePublicationRequest request) {
    SchedulePublicationCommand command =
        new SchedulePublicationCommand(
            request.publishFrom(),
            request.publishUntil(),
            request.pinned(),
            request.featured(),
            request.displayOrder());
    return EditorialItemResponse.from(service.schedule(versionId, command, actor(authentication)));
  }

  @PostMapping("/publications/{publicationId}/withdraw")
  @PreAuthorize(APPROVER)
  public EditorialItemResponse withdraw(
      Authentication authentication,
      @PathVariable("publicationId") UUID publicationId,
      @Valid @RequestBody DecisionRequest request) {
    return EditorialItemResponse.from(
        service.withdraw(
            publicationId, request.expectedVersion(), request.reason(), actor(authentication)));
  }

  @GetMapping("/categories")
  public List<CategoryResponse> categories() {
    return service.categories().stream().map(CategoryResponse::from).toList();
  }

  @PostMapping("/categories")
  @PreAuthorize(APPROVER)
  public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
    return CategoryResponse.from(service.createCategory(categoryCommand(request)));
  }

  @PutMapping("/categories/{categoryId}")
  @PreAuthorize(APPROVER)
  public CategoryResponse updateCategory(
      @PathVariable("categoryId") UUID categoryId, @Valid @RequestBody CategoryRequest request) {
    return CategoryResponse.from(
        service.updateCategory(categoryId, categoryCommand(request), request.expectedVersion()));
  }

  @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public MediaResponse uploadMedia(
      Authentication authentication,
      @RequestPart("file") MultipartFile file,
      @RequestPart("alternativeText") @NotBlank String alternativeText)
      throws IOException {
    return MediaResponse.from(
        service.uploadMedia(
            file.getOriginalFilename() == null ? "media" : file.getOriginalFilename(),
            file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
            file.getBytes(),
            alternativeText,
            actor(authentication)));
  }

  private UUID actor(Authentication authentication) {
    return currentUserResolver
        .fromAuthentication(authentication)
        .orElseThrow(() -> new IllegalStateException("Authenticated user is required."))
        .auditUserId();
  }

  private EventDetailsCommand eventCommand(
      zw.ac.uz.emhare.communications.content.api.model.CommunicationApiModels.EventDetailsRequest
          event) {
    return event == null
        ? null
        : new EventDetailsCommand(
            event.startsAt(),
            event.endsAt(),
            event.timezone(),
            event.attendanceMode(),
            event.venueName(),
            event.address(),
            event.onlineUrl());
  }

  private CategoryCommand categoryCommand(CategoryRequest request) {
    return new CategoryCommand(
        request.code(),
        request.name(),
        request.description(),
        request.displayOrder(),
        request.active());
  }
}
