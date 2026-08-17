package zw.ac.uz.emhare.testsupport.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Regression tests for the shared business-entity architecture rules. @author Tinashe K */
class EmhareArchitectureRulesTest {

  @Test
  void acceptsTheCanonicalSoftDeleteRestriction() {
    var importedClasses = new ClassFileImporter().importClasses(CanonicalSoftDeleteEntity.class);

    var result =
        EmhareArchitectureRules.auditableEntitiesMustEnforceSoftDelete().evaluate(importedClasses);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void rejectsAnAuditableEntityWithoutTheCanonicalRestriction() {
    var importedClasses = new ClassFileImporter().importClasses(UnrestrictedEntity.class);

    var result =
        EmhareArchitectureRules.auditableEntitiesMustEnforceSoftDelete().evaluate(importedClasses);

    assertThat(result.getFailureReport().toString())
        .contains("must declare @SQLRestriction(\"deleted_at IS NULL\")");
  }

  @Test
  void rejectsANonStandardSoftDeleteRestriction() {
    var importedClasses = new ClassFileImporter().importClasses(NonStandardSoftDeleteEntity.class);

    var result =
        EmhareArchitectureRules.auditableEntitiesMustEnforceSoftDelete().evaluate(importedClasses);

    assertThat(result.getFailureReport().toString())
        .contains("uses a non-standard soft-delete restriction: deleted_at is null");
  }

  @Entity
  @SQLRestriction("deleted_at IS NULL")
  private static class CanonicalSoftDeleteEntity extends AuditableEntity {}

  @Entity
  private static class UnrestrictedEntity extends AuditableEntity {}

  @Entity
  @SQLRestriction("deleted_at is null")
  private static class NonStandardSoftDeleteEntity extends AuditableEntity {}
}
