package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * @author Tinashe K
 */
class LegacyAdmissionsWriteApiQuarantineTest {

  @Test
  void selectionOfferServiceDoesNotExposeSupersededRoundOrBatchWriteMethods() {
    Set<String> publicMethodNames =
        Arrays.stream(AdmissionsSelectionOfferService.class.getMethods())
            .map(method -> method.getName())
            .collect(java.util.stream.Collectors.toSet());

    assertFalse(publicMethodNames.contains("createSelectionRound"));
    assertFalse(publicMethodNames.contains("createOfferBatch"));
    assertFalse(publicMethodNames.contains("createOffer"));
  }
}
