package zw.ac.uz.emhare.examstimetabling.timetable;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_timetable_run_events") @SQLRestriction("deleted_at IS NULL")
public class ExamTimetableRunEvent extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="generation_run_id") private ExamTimetableGenerationRun generationRun;
    @Enumerated(EnumType.STRING) @Column(name="previous_status",length=20) private ExamTimetableGenerationRun.Status previousStatus;
    @Enumerated(EnumType.STRING) @Column(name="new_status",nullable=false,length=20) private ExamTimetableGenerationRun.Status newStatus;
    @Column(nullable=false,length=1000) private String reason; @Column(name="actor_user_id",nullable=false) private UUID actorUserId;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected ExamTimetableRunEvent() {}
    public ExamTimetableRunEvent(ExamTimetableGenerationRun run,ExamTimetableGenerationRun.Status previous,String reason,UUID actor,Instant now){generationRun=run;previousStatus=previous;newStatus=run.getStatus();this.reason=reason.trim();actorUserId=actor;occurredAt=now;}
}
