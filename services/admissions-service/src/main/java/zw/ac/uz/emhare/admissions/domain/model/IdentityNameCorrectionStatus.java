package zw.ac.uz.emhare.admissions.domain.model;

/** Applicant identity-name correction lifecycle. @author Tinashe K */
public enum IdentityNameCorrectionStatus {
  OCR_REVIEWED,
  REQUESTED,
  APPROVED,
  REJECTED,
  SUPERSEDED
}
