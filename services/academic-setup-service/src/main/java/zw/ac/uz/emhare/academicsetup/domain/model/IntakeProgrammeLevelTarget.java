package zw.ac.uz.emhare.academicsetup.domain.model;


import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "intake_programme_level_targets")
@SQLRestriction("deleted_at IS NULL")
public class IntakeProgrammeLevelTarget extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intake_id", nullable = false)
    private Intake intake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_level_id", nullable = false)
    private ProgrammeLevel programmeLevel;

    protected IntakeProgrammeLevelTarget() {
    }

    public IntakeProgrammeLevelTarget(Intake intake, ProgrammeLevel programmeLevel) {
        this.intake = intake;
        this.programmeLevel = programmeLevel;
    }

    public Intake getIntake() {
        return intake;
    }

    public ProgrammeLevel getProgrammeLevel() {
        return programmeLevel;
    }
}
