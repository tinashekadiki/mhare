package zw.ac.uz.emhare.admissions.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * @author Tinashe K
 */
class AdmissionRequirementSetContractTest {

  @Test
  void acceptsRelationalSubjectRequirementsForGovernedAuthoring() {
    assertThat(
            Arrays.stream(CreateAdmissionRequirementSetRequest.class.getRecordComponents())
                .map(component -> component.getName()))
        .contains("subjectRequirements");
  }
}
