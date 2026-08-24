package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;

/**
 * @author Tinashe K
 */
class PermissionCategoryTest {

  @Test
  void supportsTheCommunicationsCategorySeededByFlyway() {
    assertEquals(PermissionCategory.COMMUNICATIONS, PermissionCategory.valueOf("COMMUNICATIONS"));
  }
}
