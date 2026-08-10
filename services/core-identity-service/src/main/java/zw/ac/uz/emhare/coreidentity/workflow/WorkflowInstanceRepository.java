package zw.ac.uz.emhare.coreidentity.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByIdAndDeletedAtIsNull(UUID id);
    Optional<WorkflowInstance> findFirstByWorkflowCodeAndSubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            String workflowCode, UUID subjectId, WorkflowStatus status);
}
