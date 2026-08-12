package zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowAssigneeType;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowTask;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowTaskStatus;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {
    Optional<WorkflowTask> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByWorkflowInstanceIdAndStatusIn(
            UUID workflowInstanceId,
            List<WorkflowTaskStatus> statuses);

    @Query("""
            select distinct task
            from WorkflowTask task
            where task.status in :statuses
              and (
                (task.assigneeType = zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowAssigneeType.USER
                  and task.assignedUser.id = :userId)
                or
                (task.assigneeType = zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowAssigneeType.ROLE
                  and exists (
                    select assignment.id
                    from UserRoleAssignment assignment
                    where assignment.user.id = :userId
                      and assignment.role = task.assignedRole
                      and assignment.deletedAt is null
                      and assignment.startsAt <= :asOf
                      and (assignment.endsAt is null or assignment.endsAt > :asOf)
                      and (
                        task.scopeType = zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType.INSTITUTION
                        or assignment.academicUnitId is null
                        or assignment.academicUnitId = task.academicUnitId
                      )
                  )
                )
              )
            order by case when task.dueAt is null then 1 else 0 end, task.dueAt, task.createdAt
            """)
    List<WorkflowTask> findAccessibleQueue(
            @Param("userId") UUID userId,
            @Param("statuses") List<WorkflowTaskStatus> statuses,
            @Param("asOf") Instant asOf);

    List<WorkflowTask> findAllByOrderByCreatedAtDesc();

    List<WorkflowTask> findAllByWorkflowInstanceIdOrderByCreatedAtAsc(UUID workflowInstanceId);
}
