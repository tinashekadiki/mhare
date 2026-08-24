package zw.ac.uz.emhare.communications.content.domain.model;

/** Communications domain value types. @author Tinashe K */
public final class CommunicationValues {

  private CommunicationValues() {}

  public enum ContentKind {
    NEWS,
    NOTICE,
    ALERT,
    CAMPAIGN,
    LINK,
    EVENT
  }

  public enum ItemLifecycleStatus {
    ACTIVE,
    ARCHIVED
  }

  public enum WorkflowStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED
  }

  public enum PublicationStatus {
    SCHEDULED,
    LIVE,
    EXPIRED,
    WITHDRAWN
  }

  public enum AttendanceMode {
    IN_PERSON,
    ONLINE,
    HYBRID
  }

  public enum MediaStatus {
    READY,
    QUARANTINED,
    RETIRED
  }
}
