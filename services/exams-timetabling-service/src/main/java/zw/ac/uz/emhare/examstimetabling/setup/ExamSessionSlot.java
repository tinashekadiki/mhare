package zw.ac.uz.emhare.examstimetabling.setup;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_session_slots") @SQLRestriction("deleted_at IS NULL")
public class ExamSessionSlot extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="exam_session_id") private ExamSession examSession;
    @Column(nullable=false,length=40) private String code; @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    protected ExamSessionSlot() {}
    public ExamSessionSlot(ExamSession examSession,String code,Instant startsAt,Instant endsAt){
        if(examSession.getStatus()!=ExamSession.Status.DRAFT)throw new IllegalStateException("Slots can only be added to a draft exam session.");
        if(startsAt==null||endsAt==null||!endsAt.isAfter(startsAt))throw new IllegalArgumentException("Exam slot requires a valid time window.");
        this.examSession=examSession;this.code=ExamVenueType.text(code,"Exam slot code").toUpperCase();this.startsAt=startsAt;this.endsAt=endsAt;
    }
    public ExamSession getExamSession(){return examSession;} public String getCode(){return code;} public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;}
}
