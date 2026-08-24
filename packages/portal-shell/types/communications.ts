export type CommunicationContentKind = "NEWS" | "NOTICE" | "ALERT" | "CAMPAIGN" | "LINK" | "EVENT";
export type CommunicationWorkflowStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "REJECTED";
export type CommunicationPublicationStatus = "SCHEDULED" | "LIVE" | "EXPIRED" | "WITHDRAWN";
export type EventAttendanceMode = "IN_PERSON" | "ONLINE" | "HYBRID";

export type StructuredContentBlock = {
  type: "HEADING" | "PARAGRAPH" | "LIST" | "QUOTE" | "CALLOUT" | "IMAGE" | "LINKS";
  [key: string]: unknown;
};

export type PublicCommunicationEvent = {
  startsAt: string;
  endsAt: string;
  timezone: string;
  attendanceMode: EventAttendanceMode;
  venueName?: string;
  address?: string;
  onlineUrl?: string;
};

export type PublicCommunicationItem = {
  publicationId: string;
  itemId: string;
  versionId: string;
  kind: CommunicationContentKind;
  slug: string;
  title: string;
  summary: string;
  schemaVersion: number;
  structuredContent: StructuredContentBlock[];
  heroMediaAssetId?: string;
  mediaUrl?: string;
  externalUrl?: string;
  publishFrom: string;
  publishUntil?: string;
  pinned: boolean;
  featured: boolean;
  event?: PublicCommunicationEvent;
};

export type PublicCommunicationHome = {
  urgentNotices: PublicCommunicationItem[];
  importantLinks: PublicCommunicationItem[];
  featuredCampaign?: PublicCommunicationItem;
  upcomingEvents: PublicCommunicationItem[];
  latestNews: PublicCommunicationItem[];
};

export type EditorialCommunicationItem = {
  itemId: string;
  versionId: string;
  kind: CommunicationContentKind;
  slug: string;
  title: string;
  summary: string;
  workflowStatus: CommunicationWorkflowStatus;
  versionNumber: number;
  expectedVersion: number;
  authoredByUserId: string;
  updatedAt: string;
  publicationStatus?: CommunicationPublicationStatus;
  publicationId?: string;
  publicationExpectedVersion?: number;
};

export type EditorialCommunicationPage = {
  items: EditorialCommunicationItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type EditorialCommunicationDetail = {
  item: EditorialCommunicationItem;
  categoryId?: string;
  structuredContent: StructuredContentBlock[];
  heroMediaAssetId?: string;
  externalUrl?: string;
  event?: PublicCommunicationEvent;
};

export type CommunicationCategory = {
  id: string;
  code: string;
  name: string;
  description?: string;
  displayOrder: number;
  active: boolean;
  expectedVersion: number;
};

export type CommunicationMediaAsset = {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  alternativeText: string;
  publicUrl: string;
};
