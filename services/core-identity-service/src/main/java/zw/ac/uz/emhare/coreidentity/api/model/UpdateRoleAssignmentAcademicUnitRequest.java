package zw.ac.uz.emhare.coreidentity.api.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Updates the governed academic unit on an active role assignment.
 *
 * @author Tinashe K
 */
public record UpdateRoleAssignmentAcademicUnitRequest(
        @NotNull UUID academicUnitId) {
}
