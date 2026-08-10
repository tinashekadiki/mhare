package zw.ac.uz.emhare.coreidentity.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface WorkflowDecisionRepository extends JpaRepository<WorkflowDecision, UUID> {
    List<WorkflowDecision> findAllByWorkflowTaskIdOrderByDecidedAtAsc(UUID workflowTaskId);
}
