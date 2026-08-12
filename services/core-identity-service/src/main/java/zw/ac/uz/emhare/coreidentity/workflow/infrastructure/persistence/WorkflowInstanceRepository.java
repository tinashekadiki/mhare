package zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowInstance;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowStatus;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByIdAndDeletedAtIsNull(UUID id);
    Optional<WorkflowInstance> findFirstByWorkflowCodeAndSubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            String workflowCode, UUID subjectId, WorkflowStatus status);
}
