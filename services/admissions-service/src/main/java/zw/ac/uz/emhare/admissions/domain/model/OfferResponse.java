package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "offer_responses")
public class OfferResponse extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "offer_id", nullable = false)
  private AdmissionOffer offer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "offer_publication_id")
  private OfferPublication offerPublication;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OfferResponseType response;

  @Column(name = "responded_at", nullable = false)
  private Instant respondedAt;

  @Column(name = "responded_by_user_id", nullable = false)
  private UUID respondedByUserId;

  @Column(length = 1000)
  private String notes;

  protected OfferResponse() {}

  public OfferResponse(
      AdmissionOffer offer,
      OfferPublication offerPublication,
      OfferResponseType response,
      Instant respondedAt,
      UUID respondedByUserId,
      String notes) {
    this.offer = offer;
    this.offerPublication = offerPublication;
    this.response = response;
    this.respondedAt = respondedAt;
    this.respondedByUserId = respondedByUserId;
    this.notes = notes == null || notes.isBlank() ? null : notes.trim();
  }

  public OfferResponseType getResponse() {
    return response;
  }

  public OfferPublication getOfferPublication() {
    return offerPublication;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public UUID getRespondedByUserId() {
    return respondedByUserId;
  }

  public String getNotes() {
    return notes;
  }
}
