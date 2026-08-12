package zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowDecision;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface WorkflowDecisionRepository extends JpaRepository<WorkflowDecision, UUID> {
    List<WorkflowDecision> findAllByWorkflowTaskIdOrderByDecidedAtAsc(UUID workflowTaskId);
}
