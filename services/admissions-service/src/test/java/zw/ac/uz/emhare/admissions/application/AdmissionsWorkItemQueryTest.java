package zw.ac.uz.emhare.admissions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.persistence.criteria.*;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.*;

/**
 * Exercises emitted JPA query predicates, including fail-closed academic scope. @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class AdmissionsWorkItemQueryTest {
  @Mock private ApplicationRepository applications;
  @Mock private ApplicationProgrammeChoiceRepository choices;
  @Mock private AcademicReviewRepository reviews;
  @Mock private AcademicRecommendationRepository recommendations;
  @Mock private ProgrammeChoiceDecisionRepository decisions;
  @Mock private AdmissionOfferRepository offers;
  @Mock private OfferConditionRepository conditions;
  @Mock private OfferResponseRepository responses;
  @Mock private OfferDocumentVersionRepository documents;
  @Mock private OfferPublicationRepository publications;
  @Mock private ApplicationStatusEventRepository events;
  @Mock private ApplicantApplicationWorkspaceService workspace;
  @Mock private AdmissionsApplicationWorkflowProgressService progress;
  @Mock private ApplicationDuplicateCheckService duplicates;
  @Mock private Root<Application> root;
  @Mock private CriteriaQuery<?> query;
  @Mock private CriteriaBuilder builder;
  @Mock private Path<Object> path;
  @Mock private Predicate predicate;
  @InjectMocks private AdmissionsWorkItemService service;
  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient().when(root.get(anyString())).thenReturn(path);
    lenient().when(path.get(anyString())).thenReturn(path);
    lenient().when(builder.isNull(any())).thenReturn(predicate);
    lenient().when(builder.conjunction()).thenReturn(predicate);
    lenient().when(builder.disjunction()).thenReturn(predicate);
    lenient().when(builder.and(any(Expression.class), any(Expression.class))).thenReturn(predicate);
    lenient().when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
    lenient().when(path.in(any(Collection.class))).thenReturn(predicate);
    when(applications.findAll(any(Specification.class), any(Pageable.class)))
        .thenAnswer(
            invocation -> {
              Specification<Application> specification = invocation.getArgument(0);
              assertThat(specification.toPredicate(root, query, builder)).isNotNull();
              return new PageImpl<Application>(List.of(), invocation.getArgument(1), 0);
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void emptyFiltersKeepSoftDeletePredicateWithoutRestrictingGlobalReviewer(String empty) {
    service.list(
        empty,
        empty,
        null,
        null,
        null,
        null,
        empty,
        0,
        10,
        profile(List.of(), List.of("ADMISSIONS_APPLICATION_REVIEW")));
    verify(root).get("deletedAt");
    verify(builder).isNull(path);
    verify(builder, never()).disjunction();
    verify(root, never()).join(anyString(), any(JoinType.class));
  }

  @ParameterizedTest
  @CsvSource({
    "VERIFICATION,4,DRAFT",
    "ELIGIBILITY,3,UNDER_REVIEW",
    "ACADEMIC_REVIEW,1,UNDER_ACADEMIC_REVIEW",
    "ADMISSION_DECISION,3,ADMITTED",
    "OFFER,2,OFFERED",
    "RESPONSE,4,CONVERTED"
  })
  void stageFilterUsesTheCorrespondingPersistedApplicationStatuses(
      String stage, int count, ApplicationStatus included) {
    service.list(
        null, " " + stage.toLowerCase() + " ", null, null, null, null, null, 0, 10, global());
    ArgumentCaptor<Collection> statuses = ArgumentCaptor.forClass(Collection.class);
    verify(path).in(statuses.capture());
    assertThat(statuses.getValue()).hasSize(count).contains(included);
    verify(root).get("status");
  }

  @Test
  void unknownStageProducesFalsePredicateRatherThanUnfilteredResults() {
    service.list(null, "UNKNOWN", null, null, null, null, null, 0, 10, global());
    verify(builder).disjunction();
    verify(path, never()).in(any(Collection.class));
  }

  @Test
  void outcomeFilterNormalizesCaseAndConstrainsPersistedStatus() {
    service.list(null, null, null, null, null, null, " admitted ", 0, 10, global());
    verify(builder).equal(path, ApplicationStatus.ADMITTED);
  }

  @Test
  void unsupportedOutcomeProducesFalsePredicate() {
    service.list(null, null, null, null, null, null, "UNKNOWN", 0, 10, global());
    verify(builder).disjunction();
  }

  @Test
  void calendarAndRouteFiltersBindExactIdentifiers() {
    UUID year = UUID.randomUUID(), intake = UUID.randomUUID(), route = UUID.randomUUID();
    service.list(null, null, year, intake, route, null, null, 0, 10, global());
    verify(root, times(2)).get("admissionCycle");
    verify(path).get("academicYearId");
    verify(path).get("intakeId");
    verify(root).get("applicationType");
    verify(path).get("id");
    verify(builder).equal(path, year);
    verify(builder).equal(path, intake);
    verify(builder).equal(path, route);
  }

  @Test
  void searchUsesApplicantIdentityEmailAndApplicationNumberCaseInsensitively() {
    Join<Application, Applicant> applicant = mock(Join.class);
    Expression<String> text = mock(Expression.class);
    doReturn(applicant).when(root).join("applicant", JoinType.INNER);
    when(applicant.get(anyString())).thenReturn(path);
    when(builder.concat(any(Expression.class), anyString())).thenReturn(text);
    when(builder.concat(any(Expression.class), any(Expression.class))).thenReturn(text);
    when(builder.lower(any())).thenReturn(text);
    when(builder.like(text, "%tariro%")).thenReturn(predicate);
    when(builder.or(any(Predicate[].class))).thenReturn(predicate);
    service.list("  TARIRO ", null, null, null, null, null, null, 0, 10, global());
    verify(root).get("applicationNumber");
    verify(applicant).get("applicantNumber");
    verify(applicant).get("primaryEmail");
    verify(applicant).get("firstName");
    verify(applicant).get("lastName");
    verify(builder, times(4)).like(text, "%tariro%");
  }

  @Test
  void programmeFilterUsesNonDeletedChoiceSubquery() {
    UUID programme = UUID.randomUUID();
    Subquery<UUID> subquery = mock(Subquery.class);
    Root<ApplicationProgrammeChoice> choiceRoot = mock(Root.class);
    when(query.subquery(UUID.class)).thenReturn(subquery);
    when(subquery.from(ApplicationProgrammeChoice.class)).thenReturn(choiceRoot);
    when(choiceRoot.get(anyString())).thenReturn(path);
    when(subquery.select(any())).thenReturn(subquery);
    when(path.in(subquery)).thenReturn(predicate);
    service.list(null, null, null, null, null, programme, null, 0, 10, global());
    verify(builder).equal(path, programme);
    verify(choiceRoot).get("deletedAt");
    verify(choiceRoot).get("application");
    verify(subquery).where(any(Predicate.class), any(Predicate.class));
    verify(path).in(subquery);
  }

  @ParameterizedTest
  @ValueSource(strings = {"nullRoles", "emptyRoles", "wrongRole", "nullUnit"})
  void unassignedStaffReceiveFailClosedQuery(String kind) {
    List<CoreRoleAssignmentSummary> roles =
        switch (kind) {
          case "nullRoles" -> null;
          case "emptyRoles" -> List.of();
          case "wrongRole" -> List.of(role("OTHER", UUID.randomUUID()));
          default -> List.of(role("ACADEMIC_UNIT_STAFF", null));
        };
    service.list(null, null, null, null, null, null, null, 0, 10, profile(roles, null));
    verify(builder).disjunction();
    verifyNoInteractions(query);
  }

  @Test
  void scopedStaffUseDistinctExactRecommendationUnitsInNonDeletedReviewSubquery() {
    UUID unit = UUID.randomUUID();
    Subquery<UUID> subquery = mock(Subquery.class);
    Root<AcademicReview> reviewRoot = mock(Root.class);
    when(query.subquery(UUID.class)).thenReturn(subquery);
    when(subquery.from(AcademicReview.class)).thenReturn(reviewRoot);
    when(reviewRoot.get(anyString())).thenReturn(path);
    when(subquery.select(any())).thenReturn(subquery);
    when(path.in(subquery)).thenReturn(predicate);
    service.list(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        10,
        profile(
            List.of(
                role("ACADEMIC_UNIT_STAFF", unit),
                role("ACADEMIC_UNIT_STAFF", unit),
                role("OTHER", UUID.randomUUID())),
            List.of()));
    verify(path).in(List.of(unit));
    verify(reviewRoot).get("recommendationAcademicUnitId");
    verify(reviewRoot).get("deletedAt");
    verify(subquery).where(any(Predicate.class), any(Predicate.class));
  }

  private CoreCurrentUserProfile global() {
    return profile(List.of(), List.of("ADMISSIONS_APPLICATION_REVIEW"));
  }

  private CoreCurrentUserProfile profile(
      List<CoreRoleAssignmentSummary> roles, List<String> permissions) {
    return new CoreCurrentUserProfile(
        new CoreUserSummary(
            actor, UUID.randomUUID(), "staff", "staff@example.test", "Staff", "ACTIVE"),
        roles,
        List.of(),
        permissions,
        true);
  }

  private CoreRoleAssignmentSummary role(String role, UUID unit) {
    return new CoreRoleAssignmentSummary(UUID.randomUUID(), UUID.randomUUID(), role, "Staff", unit);
  }
}
