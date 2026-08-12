package zw.ac.uz.emhare.examstimetabling.timetable.domain.model;

import zw.ac.uz.emhare.examstimetabling.timetable.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSessionSlot;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ModuleExamRequirement;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_master_timetable_entries") @SQLRestriction("deleted_at IS NULL")
public class ExamMasterTimetableEntry extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="generation_run_id") private ExamTimetableGenerationRun generationRun;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="exam_session_slot_id") private ExamSessionSlot examSessionSlot;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="module_exam_requirement_id") private ModuleExamRequirement moduleExamRequirement;
    @Column(name="module_id",nullable=false) private UUID moduleId; @Column(name="module_code",nullable=false,length=50) private String moduleCode;
    @Column(name="module_name",nullable=false,length=200) private String moduleName; @Column(name="candidate_count",nullable=false) private int candidateCount;
    @Column(name="scheduled_starts_at",nullable=false) private Instant scheduledStartsAt; @Column(name="scheduled_ends_at",nullable=false) private Instant scheduledEndsAt;
    protected ExamMasterTimetableEntry() {}
    public ExamMasterTimetableEntry(ExamTimetableGenerationRun run,ExamSessionSlot slot,ModuleExamRequirement requirement,int candidateCount,Instant endsAt){
        generationRun=run;examSessionSlot=slot;moduleExamRequirement=requirement;moduleId=requirement.getModuleId();moduleCode=requirement.getModuleCode();
        moduleName=requirement.getModuleName();this.candidateCount=candidateCount;scheduledStartsAt=slot.getStartsAt();scheduledEndsAt=endsAt;}
    public ExamTimetableGenerationRun getGenerationRun(){return generationRun;} public ExamSessionSlot getExamSessionSlot(){return examSessionSlot;}
    public ModuleExamRequirement getModuleExamRequirement(){return moduleExamRequirement;} public UUID getModuleId(){return moduleId;}
    public String getModuleCode(){return moduleCode;} public String getModuleName(){return moduleName;} public int getCandidateCount(){return candidateCount;}
    public Instant getScheduledStartsAt(){return scheduledStartsAt;} public Instant getScheduledEndsAt(){return scheduledEndsAt;}
}
