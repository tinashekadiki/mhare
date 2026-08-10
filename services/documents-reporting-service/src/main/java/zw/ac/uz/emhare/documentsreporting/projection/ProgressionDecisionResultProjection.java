package zw.ac.uz.emhare.documentsreporting.projection;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "progression_decision_result_projections")
@SQLRestriction("deleted_at IS NULL")
public class ProgressionDecisionResultProjection extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "progression_decision_projection_id", nullable = false)
    private ProgressionDecisionProjection progressionDecision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_result_projection_id", nullable = false)
    private PublishedResultProjection publishedResult;
    @Column(name = "source_published_result_id", nullable = false)
    private UUID sourcePublishedResultId;

    protected ProgressionDecisionResultProjection() {
    }

    public ProgressionDecisionResultProjection(
            ProgressionDecisionProjection progressionDecision,
            PublishedResultProjection publishedResult) {
        this.progressionDecision = progressionDecision;
        this.publishedResult = publishedResult;
        this.sourcePublishedResultId = publishedResult.getSourcePublishedResultId();
    }

    public PublishedResultProjection getPublishedResult() { return publishedResult; }
}
