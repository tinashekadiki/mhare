package zw.ac.uz.emhare.academicsetup.domain;

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
@Table(name = "intake_programme_targets")
@SQLRestriction("deleted_at IS NULL")
public class IntakeProgrammeTarget extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intake_id", nullable = false)
    private Intake intake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private Programme programme;

    protected IntakeProgrammeTarget() {
    }

    public IntakeProgrammeTarget(Intake intake, Programme programme) {
        this.intake = intake;
        this.programme = programme;
    }

    public Intake getIntake() {
        return intake;
    }

    public Programme getProgramme() {
        return programme;
    }
}
