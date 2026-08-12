package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicProgrammeOption;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable eligible-programme catalogue captured when an application draft starts. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_programme_option_snapshots")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationProgrammeOptionSnapshot extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false) private UUID programmeVersionId;
    @Column(name = "programme_code", nullable = false, length = 50) private String programmeCode;
    @Column(name = "programme_name", nullable = false, length = 200) private String programmeName;
    @Column(name = "award_name", nullable = false, length = 200) private String awardName;
    @Column(name = "owning_academic_unit_id", nullable = false) private UUID owningAcademicUnitId;
    @Column(name = "owning_academic_unit_name", nullable = false, length = 180) private String owningAcademicUnitName;
    @Column(name = "programme_version_code", nullable = false, length = 40) private String programmeVersionCode;
    @Column(name = "programme_type_id") private UUID programmeTypeId;
    @Column(name = "programme_type_code", length = 40) private String programmeTypeCode;
    @Column(name = "programme_type_name", length = 120) private String programmeTypeName;
    @Column(name = "programme_level_id") private UUID programmeLevelId;
    @Column(name = "programme_level_code", length = 40) private String programmeLevelCode;
    @Column(name = "programme_level_name", length = 120) private String programmeLevelName;
    @Column(name = "minimum_entry_option_selections", nullable = false) private int minimumEntryOptionSelections;
    @Column(name = "maximum_entry_option_selections", nullable = false) private int maximumEntryOptionSelections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entry_options_json", nullable = false, columnDefinition = "jsonb")
    private String entryOptionsJson;

    protected ApplicationProgrammeOptionSnapshot() {
    }

    public ApplicationProgrammeOptionSnapshot(Application application, AcademicProgrammeOption option, String entryOptionsJson) {
        this.application = application;
        this.programmeId = option.programmeId();
        this.programmeVersionId = option.programmeVersionId();
        this.programmeCode = option.programmeCode();
        this.programmeName = option.programmeName();
        this.awardName = option.awardName();
        this.owningAcademicUnitId = option.owningAcademicUnitId();
        this.owningAcademicUnitName = option.owningAcademicUnitName();
        this.programmeVersionCode = option.programmeVersionCode();
        this.programmeTypeId = option.programmeTypeId();
        this.programmeTypeCode = option.programmeTypeCode();
        this.programmeTypeName = option.programmeTypeName();
        this.programmeLevelId = option.programmeLevelId();
        this.programmeLevelCode = option.programmeLevelCode();
        this.programmeLevelName = option.programmeLevelName();
        this.minimumEntryOptionSelections = option.minimumEntryOptionSelections();
        this.maximumEntryOptionSelections = option.maximumEntryOptionSelections();
        this.entryOptionsJson = entryOptionsJson == null ? "[]" : entryOptionsJson;
    }

    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public String getAwardName() { return awardName; }
    public UUID getOwningAcademicUnitId() { return owningAcademicUnitId; }
    public String getOwningAcademicUnitName() { return owningAcademicUnitName; }
    public String getProgrammeVersionCode() { return programmeVersionCode; }
    public UUID getProgrammeTypeId() { return programmeTypeId; }
    public String getProgrammeTypeCode() { return programmeTypeCode; }
    public String getProgrammeTypeName() { return programmeTypeName; }
    public UUID getProgrammeLevelId() { return programmeLevelId; }
    public String getProgrammeLevelCode() { return programmeLevelCode; }
    public String getProgrammeLevelName() { return programmeLevelName; }
    public int getMinimumEntryOptionSelections() { return minimumEntryOptionSelections; }
    public int getMaximumEntryOptionSelections() { return maximumEntryOptionSelections; }
    public String getEntryOptionsJson() { return entryOptionsJson; }

    public ProgrammeSelectionSnapshot toProgrammeSelectionSnapshot() {
        return new ProgrammeSelectionSnapshot(
                programmeId, programmeVersionId, programmeCode, programmeName, awardName,
                owningAcademicUnitId, owningAcademicUnitName, programmeVersionCode);
    }
}
