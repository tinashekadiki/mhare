<script setup lang="ts">
import type { AcademicUnitSummary } from "@emhare/portal-shell/types/academic";

definePageMeta({
  layout: "dashboard",
  requiredAnyPermissions: [
    "CORE_INSTITUTION_MANAGE",
    "CORE_USER_MANAGE",
    "CORE_ROLE_MANAGE",
    "CORE_PERMISSION_MANAGE",
    "CORE_ROLE_ASSIGN",
    "CORE_REFERENCE_MANAGE",
    "CORE_AUDIT_READ",
    "CORE_WORKFLOW_MANAGE",
    "CORE_WORKFLOW_TASK",
  ],
});

type TableState = {
  page: number;
  pageSize: number;
  search?: string;
  sort?: Array<{ key: string; direction: "asc" | "desc" }>;
  selectedKeys?: Array<string | number>;
  visibleColumns?: string[];
};

type CoreUser = {
  id: string;
  keycloakUserId?: string;
  username: string;
  email: string;
  phoneNumber?: string;
  displayName: string;
  status: string;
  lastLoginAt?: string;
};

type CoreRole = {
  id: string;
  code: string;
  name: string;
  scope: "SYSTEM" | "ACADEMIC_UNIT";
  systemManaged: boolean;
};

type CorePermission = {
  id: string;
  code: string;
  name: string;
  category: string;
  description?: string;
};

type RolePermissionGrant = {
  id: string;
  roleId: string;
  permissionId: string;
  permissionCode: string;
  permissionName: string;
  category: string;
};

type UserRoleAssignment = {
  id: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  academicUnitId?: string | null;
  academicUnitName?: string;
  startsAt: string;
  endsAt?: string;
};

type ProvisionedUserAccess = {
  user: CoreUser;
  roleAssignments: UserRoleAssignment[];
  keycloakIdentityCreated: boolean;
  temporaryPassword?: string;
};

type UserAccessDraft = {
  key: string;
  assignmentId?: string;
  roleId: string;
  academicUnitId: string;
  startsAt: string;
};

type InstitutionProfile = {
  id?: string;
  code: string;
  name: string;
  legalName: string;
  registrarName: string;
  defaultCurrencyCode: string;
  countryCode: string;
  timezone: string;
  contactDetailsJson?: string;
  brandingJson?: string;
  bankDetailsJson?: string;
  legacyCode?: string;
};

type ProfileBankAccount = {
  currencyCode: "USD" | "ZWG";
  bankName: string;
  branchName: string;
  accountName: string;
  accountNumber: string;
  branchSortCode: string;
  swiftCode: string;
  paymentReferenceInstructions: string;
};

type CoreStatistics = {
  userCount: number;
  roleCount: number;
  permissionCount: number;
  lookupSetCount: number;
};

type UploadedDocumentSummary = {
  id: string;
  ownerType: string;
  ownerId: string;
  documentTypeCode: string;
  originalFileName: string;
  mimeType: string;
  fileSizeBytes: number;
  uploadedAt: string;
};

type UploadedDocumentDownload = {
  documentId: string;
  originalFileName: string;
  mimeType: string;
  downloadUrl: string;
  expiresAt: string;
};

type Country = {
  id: string;
  iso2Code: string;
  iso3Code: string;
  name: string;
  nationalityName: string;
};

type LookupSet = {
  id: string;
  code: string;
  name: string;
  description?: string;
};

type LookupValue = {
  id: string;
  lookupSetId: string;
  lookupSetCode: string;
  code: string;
  name: string;
  sortOrder: number;
  active: boolean;
};

type QualificationSubjectReference = {
  id: string;
  code: string;
  name: string;
  subjectGroupCode: string;
  scienceSubject: boolean;
  mathematicsSubject: boolean;
  englishSubject: boolean;
  active: boolean;
  version: number;
};

type QualificationGradeReference = {
  id: string;
  grade: string;
  points?: number | null;
  pass: boolean;
  sortOrder: number;
  version: number;
};

type QualificationReferenceData = {
  oLevelSubjects: QualificationSubjectReference[];
  aLevelSubjects: QualificationSubjectReference[];
  oLevelGrades: QualificationGradeReference[];
  aLevelGrades: QualificationGradeReference[];
};

type LoginEvent = {
  id: string;
  userId?: string;
  keycloakUserId?: string;
  username?: string;
  email?: string;
  occurredAt: string;
  ipAddress?: string;
  userAgent?: string;
  outcome: string;
};

type AuditEvent = {
  id: string;
  actorUserId?: string;
  eventType: string;
  subjectType: string;
  subjectId?: string;
  summary: string;
  beforeJson?: string;
  afterJson?: string;
  occurredAt: string;
};

type CoreOperationalReport = {
  generatedAt: string;
  inventory: CoreStatistics;
  loginSessionsLast24Hours: number;
  auditEventsLast24Hours: number;
};

type WorkflowDecision = {
  id: string;
  decisionCode: string;
  comment: string;
  actorUserId: string;
  actorName: string;
  decidedAt: string;
};

type WorkflowTask = {
  id: string;
  workflowInstanceId: string;
  workflowCode: string;
  subjectType: string;
  subjectId: string;
  subjectReference: string;
  taskReference: string;
  title: string;
  description: string;
  assigneeType: "USER" | "ROLE";
  assignedUserId?: string;
  assignedUserName?: string;
  assignedRoleId?: string;
  assignedRoleName?: string;
  scopeType: "INSTITUTION" | "ACADEMIC_UNIT";
  academicUnitId?: string;
  status: "OPEN" | "CLAIMED" | "COMPLETED" | "CANCELLED";
  dueAt?: string;
  claimedByUserId?: string;
  claimedByUserName?: string;
  claimedAt?: string;
  completedByUserId?: string;
  completedByUserName?: string;
  completedAt?: string;
  version: number;
  decisions: WorkflowDecision[];
};

type CoreDrawer =
  | "profile"
  | "user"
  | "role"
  | "permission"
  | "grant"
  | "assignment"
  | "country"
  | "lookup-set"
  | "lookup-value"
  | "qualification-subject"
  | "qualification-grade"
  | "workflow-task";

const api = useEmhareApi();
const auth = useEmhareAuth();
const { confirmAction, showSuccess, showError } = useEmhareConfirm();
const academicSetup = useAcademicSetup();

const coreWorkspaceTabs = [
  {
    id: "profile",
    label: "Institution Profile",
    icon: "i-lucide-building-2",
    requiredPermissions: ["CORE_INSTITUTION_MANAGE"],
  },
  {
    id: "users",
    label: "Users",
    icon: "i-lucide-users",
    requiredPermissions: ["CORE_USER_MANAGE"],
  },
  {
    id: "rbac",
    label: "RBAC",
    icon: "i-lucide-shield-check",
    requiredPermissions: [
      "CORE_USER_MANAGE",
      "CORE_ROLE_MANAGE",
      "CORE_PERMISSION_MANAGE",
      "CORE_ROLE_ASSIGN",
    ],
  },
  {
    id: "workflow",
    label: "Workflow Tasks",
    icon: "i-lucide-list-todo",
    requiredPermissions: [
      "CORE_USER_MANAGE",
      "CORE_ROLE_MANAGE",
      "CORE_WORKFLOW_MANAGE",
      "CORE_WORKFLOW_TASK",
    ],
  },
  {
    id: "reference",
    label: "Reference Data",
    icon: "i-lucide-list-checks",
    requiredPermissions: ["CORE_REFERENCE_MANAGE"],
  },
  {
    id: "audit",
    label: "Audit & Reports",
    icon: "i-lucide-clipboard-list",
    requiredPermissions: ["CORE_AUDIT_READ"],
  },
];
const tabs = computed(() =>
  coreWorkspaceTabs.filter((tab) => tab.requiredPermissions.every(auth.hasPermission)),
);

const activeTab = ref(tabs.value[0]?.id ?? "profile");
const activeRbacDataset = ref("roles");
const activeReferenceDataset = ref("lookup-values");
const loading = ref(false);
const error = ref("");
const drawerOpen = ref(false);
const drawerSaving = ref(false);
const drawerKind = ref<CoreDrawer | null>(null);

const users = ref<CoreUser[]>([]);
const roles = ref<CoreRole[]>([]);
const permissions = ref<CorePermission[]>([]);
const rolePermissions = ref<RolePermissionGrant[]>([]);
const assignments = ref<UserRoleAssignment[]>([]);
const countries = ref<Country[]>([]);
const lookupSets = ref<LookupSet[]>([]);
const lookupValues = ref<LookupValue[]>([]);
const oLevelSubjects = ref<QualificationSubjectReference[]>([]);
const aLevelSubjects = ref<QualificationSubjectReference[]>([]);
const oLevelGrades = ref<QualificationGradeReference[]>([]);
const aLevelGrades = ref<QualificationGradeReference[]>([]);
const loginEvents = ref<LoginEvent[]>([]);
const auditEvents = ref<AuditEvent[]>([]);
const operationalReport = ref<CoreOperationalReport>({
  generatedAt: "",
  inventory: {
    userCount: 0,
    roleCount: 0,
    permissionCount: 0,
    lookupSetCount: 0,
  },
  auditEventsLast24Hours: 0,
  loginSessionsLast24Hours: 0,
});
const workflowTasks = ref<WorkflowTask[]>([]);
const selectedWorkflowTask = ref<WorkflowTask | null>(null);

const selectedRoleId = ref("");
const selectedUserId = ref("");
const selectedLookupSetId = ref("");
const countryReferenceSelectionId = "__countries__";

const defaultProfile = {
  code: "UZ",
  name: "University of Zimbabwe",
  legalName: "University of Zimbabwe",
  registrarName: "Registrar",
  defaultCurrencyCode: "USD",
  countryCode: "ZW",
  timezone: "Africa/Harare",
  contactDetailsJson: "{}",
  brandingJson: '{"primaryColor":"#001f6e","secondaryColor":"#cb920e"}',
  bankDetailsJson: "{}",
  legacyCode: "UZ",
};
const registrationNumberPaymentInstruction =
  "After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference.";

const institutionProfile = ref<InstitutionProfile>({ ...defaultProfile });
const coreStatistics = ref<CoreStatistics>({
  userCount: 0,
  roleCount: 0,
  permissionCount: 0,
  lookupSetCount: 0,
});
const profileForm = reactive<InstitutionProfile>({ ...defaultProfile });
const profileContactForm = reactive({
  email: "",
  phone: "",
  website: "",
});
const profileBrandingForm = reactive({
  documentHeader: "",
  primaryColor: "#001f6e",
  secondaryColor: "#cb920e",
  logoDocumentId: "",
  registrarSignatureDocumentId: "",
});
const profileBankAccounts = ref<ProfileBankAccount[]>([]);
const profileLogoFile = shallowRef<File | null>(null);
const profileLogoPreviewUrl = ref("");
const storedProfileLogo = ref<UploadedDocumentSummary | null>(null);
const storedProfileLogoUrl = ref("");
const profileLogoLoading = ref(false);
const maximumLogoSizeBytes = 2 * 1024 * 1024;
const profileRegistrarSignatureFile = shallowRef<File | null>(null);
const profileRegistrarSignaturePreviewUrl = ref("");
const storedProfileRegistrarSignature = ref<UploadedDocumentSummary | null>(null);
const storedProfileRegistrarSignatureUrl = ref("");
const profileRegistrarSignatureLoading = ref(false);
const maximumRegistrarSignatureSizeBytes = 2 * 1024 * 1024;
const userForm = reactive({
  id: "",
  username: "",
  email: "",
  phoneNumber: "",
  displayName: "",
  status: "INVITED",
});
const userProvisioningStep = ref("identity");
const userAccessDrafts = ref<UserAccessDraft[]>([]);
const editingUserRoleAssignments = ref<UserRoleAssignment[]>([]);
const rolePermissionCatalog = ref<Record<string, RolePermissionGrant[]>>({});
const rolePermissionLoadingIds = ref<Set<string>>(new Set());
let userAccessDraftSequence = 0;
const roleForm = reactive({
  id: "",
  code: "",
  name: "",
  scope: "SYSTEM" as "SYSTEM" | "ACADEMIC_UNIT",
  systemManaged: false,
});
const permissionForm = reactive({
  id: "",
  code: "",
  name: "",
  category: "CORE",
  description: "",
});
const assignmentForm = reactive({
  userId: "",
  roleId: "",
  academicUnitId: "",
  startsAt: "",
});
const grantForm = reactive({ permissionId: "" });
const countryForm = reactive({
  id: "",
  iso2Code: "",
  iso3Code: "",
  name: "",
  nationalityName: "",
});
const lookupSetForm = reactive({ id: "", code: "", name: "", description: "" });
const lookupValueForm = reactive({
  id: "",
  code: "",
  name: "",
  sortOrder: 0,
  active: true,
});
const qualificationSubjectForm = reactive({
  id: "",
  level: "O_LEVEL",
  code: "",
  name: "",
  subjectGroupCode: "HUMANITIES",
  scienceSubject: false,
  mathematicsSubject: false,
  englishSubject: false,
  active: true,
  version: 0,
});
const qualificationGradeForm = reactive({
  id: "",
  level: "O_LEVEL",
  grade: "",
  points: undefined as number | undefined,
  pass: false,
  sortOrder: 0,
  version: 0,
});
const workflowDecisionForm = reactive({
  decisionCode: "COMPLETED",
  comment: "",
});

const userTableState = ref<TableState>({ page: 1, pageSize: 10 });
const roleTableState = ref<TableState>({ page: 1, pageSize: 8 });
const permissionTableState = ref<TableState>({ page: 1, pageSize: 8 });
const countryTableState = ref<TableState>({ page: 1, pageSize: 8 });
const lookupSetTableState = ref<TableState>({ page: 1, pageSize: 8 });
const lookupValueTableState = ref<TableState>({ page: 1, pageSize: 8 });
const oLevelSubjectTableState = ref<TableState>({ page: 1, pageSize: 8 });
const aLevelSubjectTableState = ref<TableState>({ page: 1, pageSize: 8 });
const oLevelGradeTableState = ref<TableState>({ page: 1, pageSize: 8 });
const aLevelGradeTableState = ref<TableState>({ page: 1, pageSize: 8 });
const loginTableState = ref<TableState>({ page: 1, pageSize: 10 });
const auditTableState = ref<TableState>({ page: 1, pageSize: 10 });
const grantTableState = ref<TableState>({ page: 1, pageSize: 10 });
const assignmentTableState = ref<TableState>({ page: 1, pageSize: 10 });
const workflowTableState = ref<TableState>({ page: 1, pageSize: 10 });

const userStatuses = ["INVITED", "ACTIVE", "LOCKED", "DISABLED"];
const roleScopes = ["SYSTEM", "ACADEMIC_UNIT"];
const qualificationSubjectGroups = [
  "ARTS",
  "COMMERCIAL",
  "ENGLISH",
  "HUMANITIES",
  "MATHEMATICS",
  "SCIENCE",
  "TECHNICAL",
];
const permissionCategories = [
  "CORE",
  "ACADEMIC_SETUP",
  "ADMISSIONS",
  "FINANCE",
  "STUDENT_RECORDS",
  "ASSESSMENT_RESULTS",
  "EXAMS",
  "ACCOMMODATION",
  "DINING",
  "DOCUMENTS",
  "NOTIFICATIONS",
];

const userColumns = [
  { key: "displayName", label: "Name", sortable: true, frozen: true },
  { key: "email", label: "Email", sortable: true },
  { key: "phoneNumber", label: "Phone" },
  { key: "status", label: "Status", sortable: true },
  { key: "lastLoginAt", label: "Last login", sortable: true },
];

const roleColumns = [
  { key: "name", label: "Role", sortable: true, frozen: true },
  { key: "code", label: "Code", sortable: true },
  { key: "scope", label: "Scope", sortable: true },
  { key: "systemManaged", label: "Managed" },
];

const permissionColumns = [
  { key: "name", label: "Permission", sortable: true, frozen: true },
  { key: "code", label: "Code", sortable: true },
  { key: "category", label: "Category", sortable: true },
  { key: "description", label: "Description" },
];

const countryColumns = [
  { key: "name", label: "Country", sortable: true, frozen: true },
  { key: "iso2Code", label: "ISO2", sortable: true },
  { key: "iso3Code", label: "ISO3", sortable: true },
  { key: "nationalityName", label: "Nationality", sortable: true },
];

const lookupSetColumns = [
  { key: "code", label: "Set code", sortable: true, frozen: true },
  { key: "name", label: "Name", sortable: true },
  { key: "description", label: "Description" },
];

const lookupValueColumns = [
  { key: "code", label: "Value code", sortable: true, frozen: true },
  { key: "name", label: "Name", sortable: true },
  { key: "sortOrder", label: "Sort", sortable: true, align: "right" as const },
  { key: "active", label: "Active", sortable: true },
];

const qualificationSubjectColumns = [
  { key: "code", label: "ZIMSEC code", sortable: true, frozen: true },
  { key: "name", label: "Subject", sortable: true },
  { key: "subjectGroupCode", label: "Group", sortable: true },
  { key: "scienceSubject", label: "Science", sortable: true },
  { key: "mathematicsSubject", label: "Mathematics", sortable: true },
  { key: "englishSubject", label: "English", sortable: true },
  { key: "active", label: "Status", sortable: true },
];

const qualificationGradeColumns = [
  { key: "grade", label: "Grade", sortable: true, frozen: true },
  { key: "points", label: "Points", sortable: true, align: "right" as const },
  { key: "pass", label: "Outcome", sortable: true },
  { key: "sortOrder", label: "Order", sortable: true, align: "right" as const },
];

const loginColumns = [
  { key: "occurredAt", label: "Occurred", sortable: true, frozen: true },
  { key: "email", label: "Email", sortable: true },
  { key: "username", label: "Username" },
  { key: "outcome", label: "Outcome", sortable: true },
  { key: "ipAddress", label: "IP address" },
];

const auditColumns = [
  { key: "occurredAt", label: "Occurred", sortable: true, frozen: true },
  { key: "summary", label: "Activity", sortable: true },
  { key: "subjectType", label: "Record type", sortable: true },
  { key: "eventType", label: "Event", sortable: true },
  { key: "actorUserId", label: "Actor" },
];

const grantColumns = [
  { key: "permissionName", label: "Permission", sortable: true, frozen: true },
  { key: "permissionCode", label: "Code", sortable: true },
  { key: "category", label: "Category", sortable: true },
];

const assignmentColumns = [
  { key: "roleName", label: "Role", sortable: true, frozen: true },
  { key: "roleCode", label: "Code", sortable: true },
  { key: "academicUnitName", label: "Academic unit", sortable: true },
  { key: "startsAt", label: "Starts", sortable: true },
  { key: "endsAt", label: "Ends", sortable: true },
];

const workflowColumns = [
  { key: "taskReference", label: "Task", sortable: true, frozen: true },
  { key: "title", label: "Title", sortable: true },
  { key: "subjectReference", label: "Subject", sortable: true },
  { key: "assignedTo", label: "Assigned to", sortable: true },
  { key: "scopeLabel", label: "Scope", sortable: true },
  { key: "dueAt", label: "Due", sortable: true },
  { key: "status", label: "Status", sortable: true },
];

const roleOptions = computed(() =>
  roles.value.map((role) => ({ label: role.name, value: role.id })),
);
const userOptions = computed(() =>
  users.value.map((user) => ({
    label: `${user.displayName} (${user.email})`,
    value: user.id,
  })),
);
const permissionOptions = computed(() =>
  permissions.value.map((permission) => ({
    label: `${permission.name} · ${permission.code}`,
    value: permission.id,
  })),
);
const managedLookupSetOptions = computed(() =>
  lookupSets.value.map((lookupSet) => ({
    label: lookupSet.name,
    value: lookupSet.id,
  })),
);
const lookupSetOptions = computed(() => [
  { label: "Countries", value: countryReferenceSelectionId },
  ...managedLookupSetOptions.value,
]);
const countryCodeOptions = computed(() =>
  countries.value.map((country) => ({
    label: `${country.iso2Code} · ${country.name}`,
    value: country.iso2Code,
  })),
);
const baseCurrencyOptions = [{ label: "USD · transaction base currency", value: "USD" }];
const paymentCurrencyOptions = [
  { label: "USD · Nostro account", value: "USD" },
  { label: "ZWG · Local-currency account", value: "ZWG" },
];
const timezoneOptions = [{ label: "Africa/Harare · Zimbabwe", value: "Africa/Harare" }];
const academicUnitOptions = computed(() =>
  (academicSetup.overview.value?.academicUnits ?? [])
    .filter((unit: AcademicUnitSummary) => unit.status === "ACTIVE")
    .sort((left: AcademicUnitSummary, right: AcademicUnitSummary) =>
      left.name.localeCompare(right.name),
    )
    .map((unit: AcademicUnitSummary) => ({
      label: `${unit.code} · ${unit.name} · ${unit.academicUnitTypeCode}`,
      value: unit.id,
    })),
);

const selectedRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value));
const selectedUser = computed(() => users.value.find((user) => user.id === selectedUserId.value));
const countryLookupSelected = computed(
  () => selectedLookupSetId.value === countryReferenceSelectionId,
);
const selectedLookupSet = computed(() => {
  if (countryLookupSelected.value) {
    return {
      id: countryReferenceSelectionId,
      code: "COUNTRIES",
      name: "Countries",
      description: "Authoritative country codes and nationality labels.",
    };
  }
  return lookupSets.value.find((lookupSet) => lookupSet.id === selectedLookupSetId.value);
});
const selectedAssignmentRole = computed(() =>
  roles.value.find((role) => role.id === assignmentForm.roleId),
);
const assignmentRequiresAcademicUnit = computed(
  () => selectedAssignmentRole.value?.scope === "ACADEMIC_UNIT",
);
const userProvisioningSteps = computed(() => [
  {
    id: "identity",
    title: "Identity",
    description: "Profile details",
    icon: "i-lucide-user-round",
    status:
      userProvisioningStep.value === "identity" ? ("current" as const) : ("complete" as const),
    disabled: false,
  },
  {
    id: "access",
    title: "Access roles",
    description: "Sections and scope",
    icon: "i-lucide-shield-check",
    status:
      userProvisioningStep.value === "access"
        ? ("current" as const)
        : userProvisioningStep.value === "review"
          ? ("complete" as const)
          : ("pending" as const),
    disabled: !userIdentityStepComplete(),
  },
  {
    id: "review",
    title: "Review",
    description: "Activate profile",
    icon: "i-lucide-clipboard-check",
    status: userProvisioningStep.value === "review" ? ("current" as const) : ("pending" as const),
    disabled: !userIdentityStepComplete() || !userAccessStepComplete(),
  },
]);
const userProvisioningSubmitLabel = computed(() => {
  if (userProvisioningStep.value === "identity") return "Continue to access";
  if (userProvisioningStep.value === "access") return "Review profile";
  return "Create and activate user";
});
const activeProfileLogoUrl = computed(
  () => profileLogoPreviewUrl.value || storedProfileLogoUrl.value,
);
const activeProfileRegistrarSignatureUrl = computed(
  () => profileRegistrarSignaturePreviewUrl.value || storedProfileRegistrarSignatureUrl.value,
);
const assignmentRows = computed(() =>
  assignments.value.map((assignment) => ({
    ...assignment,
    academicUnitName: assignment.academicUnitId
      ? (academicSetup.overview.value?.academicUnits.find(
          (unit) => unit.id === assignment.academicUnitId,
        )?.name ?? assignment.academicUnitId)
      : "Institution-wide",
  })),
);
const workflowRows = computed(() =>
  workflowTasks.value.map((task) => ({
    ...task,
    assignedTo: task.assigneeType === "USER" ? task.assignedUserName : task.assignedRoleName,
    scopeLabel:
      task.scopeType === "INSTITUTION"
        ? "Institution-wide"
        : (academicSetup.overview.value?.academicUnits.find(
            (unit) => unit.id === task.academicUnitId,
          )?.name ?? "Academic unit"),
  })),
);

const rbacDatasets = computed(() => [
  {
    label: "Roles",
    value: "roles",
    icon: "i-lucide-shield-check",
    badge: roles.value.length,
  },
  {
    label: "Permissions",
    value: "permissions",
    icon: "i-lucide-list-checks",
    badge: permissions.value.length,
  },
  {
    label: "Role grants",
    value: "grants",
    icon: "i-lucide-check-circle",
    badge: rolePermissions.value.length,
  },
  {
    label: "User assignments",
    value: "assignments",
    icon: "i-lucide-user-plus",
    badge: assignments.value.length,
  },
]);

const referenceDatasets = computed(() => [
  {
    label: "Lookup sets",
    value: "lookup-sets",
    icon: "i-lucide-bookmark",
    badge: lookupSets.value.length,
  },
  {
    label: "Lookup values",
    value: "lookup-values",
    icon: "i-lucide-list-checks",
    badge: countryLookupSelected.value ? countries.value.length : lookupValues.value.length,
  },
  {
    label: "O Level subjects",
    value: "o-level-subjects",
    icon: "i-lucide-book-open-check",
    badge: oLevelSubjects.value.length,
  },
  {
    label: "A Level subjects",
    value: "a-level-subjects",
    icon: "i-lucide-book-open-check",
    badge: aLevelSubjects.value.length,
  },
  {
    label: "O Level grades",
    value: "o-level-grades",
    icon: "i-lucide-graduation-cap",
    badge: oLevelGrades.value.length,
  },
  {
    label: "A Level grades",
    value: "a-level-grades",
    icon: "i-lucide-graduation-cap",
    badge: aLevelGrades.value.length,
  },
]);

const profileDetails = computed(() => [
  { label: "Institution code", value: institutionProfile.value.code },
  { label: "Operating name", value: institutionProfile.value.name },
  { label: "Legal name", value: institutionProfile.value.legalName },
  { label: "Registrar", value: institutionProfile.value.registrarName },
  {
    label: "Base currency",
    value: institutionProfile.value.defaultCurrencyCode,
  },
  { label: "Country", value: institutionProfile.value.countryCode },
  { label: "Timezone", value: institutionProfile.value.timezone },
  { label: "Legacy code", value: institutionProfile.value.legacyCode },
]);

const drawerTitle = computed(() => {
  switch (drawerKind.value) {
    case "profile":
      return "Edit institution profile";
    case "user":
      return userForm.id ? "Edit user" : "Provision user access";
    case "role":
      return roleForm.id ? "Edit role" : "Create role";
    case "permission":
      return permissionForm.id ? "Edit permission" : "Create permission";
    case "grant":
      return "Grant permission to role";
    case "assignment":
      return "Assign role to user";
    case "country":
      return countryForm.id ? "Edit country" : "Create country";
    case "lookup-set":
      return lookupSetForm.id ? "Edit lookup set" : "Create lookup set";
    case "lookup-value":
      return lookupValueForm.id ? "Edit lookup value" : "Create lookup value";
    case "qualification-subject":
      return qualificationSubjectForm.id
        ? "Edit qualification subject"
        : "Create qualification subject";
    case "qualification-grade":
      return qualificationGradeForm.id ? "Edit qualification grade" : "Create qualification grade";
    case "workflow-task":
      return selectedWorkflowTask?.value?.taskReference ?? "Workflow task";
    default:
      return "Maintain record";
  }
});

const drawerDescription = computed(() => {
  switch (drawerKind.value) {
    case "profile":
      return "Maintain the single-institution identity and official document defaults.";
    case "user":
      return userForm.id
        ? "Maintain the Core identity record and its academic-unit-scoped role assignments. Authentication credentials remain governed by Keycloak."
        : "Complete the identity and role assignments before the user is activated.";
    case "role":
      return "Define a reusable responsibility boundary for operational access.";
    case "permission":
      return "Maintain a named business capability in the permission catalogue.";
    case "grant":
      return "Attach one permission to the selected role.";
    case "assignment":
      return "Grant the selected user a role, optionally scoped to an academic unit.";
    case "country":
      return "Maintain authoritative country and nationality reference data.";
    case "lookup-set":
      return "Create or update a governed reference-data collection.";
    case "lookup-value":
      return "Maintain one value within the selected lookup set.";
    case "qualification-subject":
      return "Maintain the subject code, classification, and availability used during applicant qualification capture.";
    case "qualification-grade":
      return "Maintain the grade outcome and points used during eligibility and A Level points calculation.";
    case "workflow-task":
      return "Claim the authorised queue item, then record an immutable decision and evidence comment.";
    default:
      return undefined;
  }
});

const drawerSubmitDisabled = computed(() => {
  switch (drawerKind.value) {
    case "profile":
      return (
        !profileForm.code.trim() ||
        !profileForm.name.trim() ||
        !profileForm.legalName.trim() ||
        !profileForm.registrarName.trim() ||
        !profileForm.defaultCurrencyCode.trim() ||
        !profileForm.countryCode.trim() ||
        !profileForm.timezone.trim() ||
        profileBankAccounts.value.some((account) => !profileBankAccountComplete(account)) ||
        !profileBankAccounts.value.some((account) => account.currencyCode === "USD") ||
        !profileBankAccounts.value.some((account) => account.currencyCode === "ZWG")
      );
    case "user":
      if (!userForm.id) {
        if (userProvisioningStep.value === "identity") {
          return !userIdentityStepComplete();
        }
        if (userProvisioningStep.value === "access") {
          return !userAccessStepComplete();
        }
        return !userIdentityStepComplete() || !userAccessStepComplete();
      }
      return (
        !userForm.username.trim() ||
        !userForm.email.trim() ||
        !userForm.displayName.trim() ||
        !userAccessAssignmentsComplete()
      );
    case "role":
      return !roleForm.code.trim() || !roleForm.name.trim();
    case "permission":
      return !permissionForm.code.trim() || !permissionForm.name.trim();
    case "grant":
      return !selectedRoleId.value || !grantForm.permissionId;
    case "assignment":
      return (
        !selectedUserId.value ||
        !assignmentForm.roleId ||
        (assignmentRequiresAcademicUnit.value && !assignmentForm.academicUnitId)
      );
    case "country":
      return (
        !countryForm.iso2Code.trim() ||
        !countryForm.iso3Code.trim() ||
        !countryForm.name.trim() ||
        !countryForm.nationalityName.trim()
      );
    case "lookup-set":
      return !lookupSetForm.code.trim() || !lookupSetForm.name.trim();
    case "lookup-value":
      return (
        !selectedLookupSetId.value || !lookupValueForm.code.trim() || !lookupValueForm.name.trim()
      );
    case "qualification-subject":
      return (
        !qualificationSubjectForm.code.trim() ||
        !qualificationSubjectForm.name.trim() ||
        !qualificationSubjectForm.subjectGroupCode
      );
    case "qualification-grade":
      return !qualificationGradeForm.grade.trim() || qualificationGradeForm.sortOrder < 0;
    case "workflow-task":
      return (
        !selectedWorkflowTask.value ||
        selectedWorkflowTask.value.status === "COMPLETED" ||
        selectedWorkflowTask.value.status === "CANCELLED" ||
        (selectedWorkflowTask.value.status === "CLAIMED" && !workflowDecisionForm.comment.trim())
      );
    default:
      return true;
  }
});

onMounted(loadCoreData);

onBeforeUnmount(() => {
  revokeProfileLogoPreview();
  revokeProfileRegistrarSignaturePreview();
});

watch(activeTab, loadCoreData);

watch(
  () => assignmentForm.roleId,
  () => {
    if (selectedAssignmentRole.value?.scope === "SYSTEM") {
      assignmentForm.academicUnitId = "";
    }
  },
);

watch(selectedRoleId, async (roleId) => {
  rolePermissions.value = roleId
    ? await api.request<RolePermissionGrant[]>(`/api/core/roles/${roleId}/permissions`)
    : [];
});

watch(selectedUserId, async (userId) => {
  assignments.value = userId
    ? await api.request<UserRoleAssignment[]>(`/api/core/users/${userId}/role-assignments`)
    : [];
  assignmentForm.userId = userId;
});

watch(selectedLookupSetId, async (lookupSetId) => {
  if (!lookupSetId || lookupSetId === countryReferenceSelectionId) {
    lookupValues.value = [];
    return;
  }
  lookupValues.value = await api.request<LookupValue[]>(
    `/api/core/lookup-sets/${lookupSetId}/values`,
  );
});

function parseProfileJson(value?: string) {
  if (!value?.trim()) {
    return {} as Record<string, unknown>;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {} as Record<string, unknown>;
  }
}

function profileJsonString(source: Record<string, unknown>, values: Record<string, string>) {
  const result = { ...source };
  for (const [key, value] of Object.entries(values)) {
    const normalizedValue = value.trim();
    if (normalizedValue) {
      result[key] = normalizedValue;
    } else {
      delete result[key];
    }
  }
  return JSON.stringify(result);
}

function profileJsonValue(source: Record<string, unknown>, key: string) {
  return typeof source[key] === "string" ? String(source[key]) : "";
}

function emptyProfileBankAccount(
  currencyCode: ProfileBankAccount["currencyCode"] = "USD",
): ProfileBankAccount {
  return {
    currencyCode,
    bankName: "",
    branchName: "",
    accountName: "",
    accountNumber: "",
    branchSortCode: "",
    swiftCode: "",
    paymentReferenceInstructions: registrationNumberPaymentInstruction,
  };
}

function parsedProfileBankAccounts(bankDetails: Record<string, unknown>): ProfileBankAccount[] {
  if (Array.isArray(bankDetails.accounts)) {
    const accounts = bankDetails.accounts.flatMap((value) => {
      if (!value || typeof value !== "object" || Array.isArray(value)) {
        return [];
      }
      const account = value as Record<string, unknown>;
      const currencyCode = profileJsonValue(account, "currencyCode").toUpperCase();
      if (currencyCode !== "USD" && currencyCode !== "ZWG") {
        return [];
      }
      return [
        {
          currencyCode,
          bankName: profileJsonValue(account, "bankName"),
          branchName: profileJsonValue(account, "branchName"),
          accountName: profileJsonValue(account, "accountName"),
          accountNumber: profileJsonValue(account, "accountNumber"),
          branchSortCode: profileJsonValue(account, "branchSortCode"),
          swiftCode: profileJsonValue(account, "swiftCode"),
          paymentReferenceInstructions: profileJsonValue(account, "paymentReferenceInstructions"),
        } satisfies ProfileBankAccount,
      ];
    });
    if (accounts.length) {
      return accounts;
    }
  }
  const legacyAccountNumber = profileJsonValue(bankDetails, "accountNumber");
  if (legacyAccountNumber) {
    return [
      {
        currencyCode: "USD",
        bankName: profileJsonValue(bankDetails, "bankName"),
        branchName: profileJsonValue(bankDetails, "branchName"),
        accountName: profileJsonValue(bankDetails, "accountName"),
        accountNumber: legacyAccountNumber,
        branchSortCode: profileJsonValue(bankDetails, "branchSortCode"),
        swiftCode: profileJsonValue(bankDetails, "swiftCode"),
        paymentReferenceInstructions: profileJsonValue(bankDetails, "paymentReferenceInstructions"),
      },
      emptyProfileBankAccount("ZWG"),
    ];
  }
  return [emptyProfileBankAccount("USD"), emptyProfileBankAccount("ZWG")];
}

function profileBankDetailsJson(source: Record<string, unknown>, accounts: ProfileBankAccount[]) {
  const result = { ...source };
  for (const legacyKey of [
    "bankName",
    "branchName",
    "accountName",
    "accountNumber",
    "branchSortCode",
    "swiftCode",
    "paymentReferenceInstructions",
  ]) {
    delete result[legacyKey];
  }
  result.accounts = accounts.map((account) =>
    Object.fromEntries(
      Object.entries(account)
        .map(([key, value]) => [key, value.trim()])
        .filter(([, value]) => Boolean(value)),
    ),
  );
  return JSON.stringify(result);
}

function addProfileBankAccount() {
  const usdCount = profileBankAccounts.value.filter(
    (account) => account.currencyCode === "USD",
  ).length;
  const zwgCount = profileBankAccounts.value.filter(
    (account) => account.currencyCode === "ZWG",
  ).length;
  profileBankAccounts.value.push(emptyProfileBankAccount(usdCount <= zwgCount ? "USD" : "ZWG"));
}

function removeProfileBankAccount(index: number) {
  profileBankAccounts.value.splice(index, 1);
}

function profileBankAccountComplete(account: ProfileBankAccount) {
  return Boolean(account.currencyCode && account.bankName.trim() && account.accountNumber.trim());
}

function resetProfileForm() {
  Object.assign(profileForm, institutionProfile.value);
  const contactDetails = parseProfileJson(institutionProfile.value.contactDetailsJson);
  const branding = parseProfileJson(institutionProfile.value.brandingJson);
  const bankDetails = parseProfileJson(institutionProfile.value.bankDetailsJson);
  Object.assign(profileContactForm, {
    email: profileJsonValue(contactDetails, "email"),
    phone: profileJsonValue(contactDetails, "phone"),
    website: profileJsonValue(contactDetails, "website"),
  });
  Object.assign(profileBrandingForm, {
    documentHeader:
      profileJsonValue(branding, "documentHeader") || institutionProfile.value.legalName,
    primaryColor: profileJsonValue(branding, "primaryColor") || "#001f6e",
    secondaryColor: profileJsonValue(branding, "secondaryColor") || "#cb920e",
    logoDocumentId: profileJsonValue(branding, "logoDocumentId"),
    registrarSignatureDocumentId: profileJsonValue(branding, "registrarSignatureDocumentId"),
  });
  profileBankAccounts.value = parsedProfileBankAccounts(bankDetails);
  profileLogoFile.value = null;
  profileRegistrarSignatureFile.value = null;
  revokeProfileLogoPreview();
  revokeProfileRegistrarSignaturePreview();
}

function revokeProfileLogoPreview() {
  if (profileLogoPreviewUrl.value) {
    URL.revokeObjectURL(profileLogoPreviewUrl.value);
    profileLogoPreviewUrl.value = "";
  }
}

async function loadStoredProfileLogo() {
  storedProfileLogo.value = null;
  storedProfileLogoUrl.value = "";
  const documentId = profileBrandingForm.logoDocumentId;
  if (!documentId) {
    return;
  }
  profileLogoLoading.value = true;
  try {
    const [document, download] = await Promise.all([
      api.request<UploadedDocumentSummary>(`/api/documents/uploads/${documentId}`),
      api.request<UploadedDocumentDownload>(`/api/documents/uploads/${documentId}/download`),
    ]);
    storedProfileLogo.value = document;
    storedProfileLogoUrl.value = download.downloadUrl;
  } catch {
    storedProfileLogo.value = null;
    storedProfileLogoUrl.value = "";
  } finally {
    profileLogoLoading.value = false;
  }
}

async function selectProfileLogo(value: unknown) {
  const file = Array.isArray(value) ? value[0] : value;
  revokeProfileLogoPreview();
  if (!(file instanceof File)) {
    profileLogoFile.value = null;
    return;
  }
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    profileLogoFile.value = null;
    await showError("Logo not accepted", "Choose a genuine PNG or JPEG image.");
    return;
  }
  if (file.size > maximumLogoSizeBytes) {
    profileLogoFile.value = null;
    await showError("Logo is too large", "Choose an image smaller than 2 MB.");
    return;
  }
  profileLogoFile.value = file;
  profileLogoPreviewUrl.value = URL.createObjectURL(file);
}

function removeProfileLogo() {
  profileLogoFile.value = null;
  revokeProfileLogoPreview();
  profileBrandingForm.logoDocumentId = "";
  storedProfileLogo.value = null;
  storedProfileLogoUrl.value = "";
}

function revokeProfileRegistrarSignaturePreview() {
  if (profileRegistrarSignaturePreviewUrl.value) {
    URL.revokeObjectURL(profileRegistrarSignaturePreviewUrl.value);
    profileRegistrarSignaturePreviewUrl.value = "";
  }
}

async function loadStoredProfileRegistrarSignature() {
  storedProfileRegistrarSignature.value = null;
  storedProfileRegistrarSignatureUrl.value = "";
  const documentId = profileBrandingForm.registrarSignatureDocumentId;
  if (!documentId) return;
  profileRegistrarSignatureLoading.value = true;
  try {
    const [document, download] = await Promise.all([
      api.request<UploadedDocumentSummary>(`/api/documents/uploads/${documentId}`),
      api.request<UploadedDocumentDownload>(
        `/api/documents/uploads/${documentId}/download?disposition=inline`,
      ),
    ]);
    storedProfileRegistrarSignature.value = document;
    storedProfileRegistrarSignatureUrl.value = download.downloadUrl;
  } catch {
    storedProfileRegistrarSignature.value = null;
    storedProfileRegistrarSignatureUrl.value = "";
  } finally {
    profileRegistrarSignatureLoading.value = false;
  }
}

async function selectProfileRegistrarSignature(value: unknown) {
  const file = Array.isArray(value) ? value[0] : value;
  revokeProfileRegistrarSignaturePreview();
  if (!(file instanceof File)) {
    profileRegistrarSignatureFile.value = null;
    return;
  }
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    profileRegistrarSignatureFile.value = null;
    await showError("Signature not accepted", "Choose a genuine PNG or JPEG signature image.");
    return;
  }
  if (file.size > maximumRegistrarSignatureSizeBytes) {
    profileRegistrarSignatureFile.value = null;
    await showError("Signature is too large", "Choose a signature image smaller than 2 MB.");
    return;
  }
  profileRegistrarSignatureFile.value = file;
  profileRegistrarSignaturePreviewUrl.value = URL.createObjectURL(file);
}

function removeProfileRegistrarSignature() {
  profileRegistrarSignatureFile.value = null;
  revokeProfileRegistrarSignaturePreview();
  profileBrandingForm.registrarSignatureDocumentId = "";
  storedProfileRegistrarSignature.value = null;
  storedProfileRegistrarSignatureUrl.value = "";
}

async function loadCoreData() {
  loading.value = true;
  error.value = "";
  try {
    switch (activeTab.value) {
      case "profile": {
        const [profileResult, countryResult, statisticsResult] = await Promise.all([
          api.request<InstitutionProfile | null>("/api/core/institution-profile"),
          api.request<Country[]>("/api/core/countries"),
          api.request<CoreStatistics>("/api/core/statistics"),
        ]);
        institutionProfile.value = { ...(profileResult ?? defaultProfile) };
        countries.value = countryResult;
        coreStatistics.value = statisticsResult;
        resetProfileForm();
        await Promise.all([loadStoredProfileLogo(), loadStoredProfileRegistrarSignature()]);
        break;
      }
      case "users":
        users.value = await api.request<CoreUser[]>("/api/core/users");
        break;
      case "rbac": {
        const [userResult, roleResult, permissionResult] = await Promise.all([
          api.request<CoreUser[]>("/api/core/users"),
          api.request<CoreRole[]>("/api/core/roles"),
          api.request<CorePermission[]>("/api/core/permissions"),
          academicSetup.ensureOverview(),
        ]);
        users.value = userResult;
        roles.value = roleResult;
        permissions.value = permissionResult;
        selectedRoleId.value ||= roles.value[0]?.id ?? "";
        selectedUserId.value ||= users.value[0]?.id ?? "";
        break;
      }
      case "workflow": {
        const [userResult, roleResult, workflowTaskResult] = await Promise.all([
          api.request<CoreUser[]>("/api/core/users"),
          api.request<CoreRole[]>("/api/core/roles"),
          api.request<WorkflowTask[]>("/api/core/workflows/tasks"),
          academicSetup.ensureOverview(),
        ]);
        users.value = userResult;
        roles.value = roleResult;
        workflowTasks.value = workflowTaskResult;
        break;
      }
      case "reference": {
        const [countryResult, lookupSetResult, qualificationReferenceResult] = await Promise.all([
          api.request<Country[]>("/api/core/countries"),
          api.request<LookupSet[]>("/api/core/lookup-sets"),
          api.request<QualificationReferenceData>(
            "/api/admissions/qualification-reference-data/manage",
          ),
        ]);
        countries.value = countryResult;
        lookupSets.value = lookupSetResult;
        oLevelSubjects.value = qualificationReferenceResult.oLevelSubjects ?? [];
        aLevelSubjects.value = qualificationReferenceResult.aLevelSubjects ?? [];
        oLevelGrades.value = qualificationReferenceResult.oLevelGrades ?? [];
        aLevelGrades.value = qualificationReferenceResult.aLevelGrades ?? [];
        if (
          !selectedLookupSetId.value ||
          (selectedLookupSetId.value !== countryReferenceSelectionId &&
            !lookupSets.value.some((lookupSet) => lookupSet.id === selectedLookupSetId.value))
        ) {
          selectedLookupSetId.value = countryReferenceSelectionId;
        }
        break;
      }
      case "audit": {
        const [auditResult, loginResult, reportResult] = await Promise.all([
          api.request<AuditEvent[]>("/api/core/audit-events"),
          api.request<LoginEvent[]>("/api/core/login-events"),
          api.request<CoreOperationalReport>("/api/core/reports/overview"),
        ]);
        auditEvents.value = auditResult;
        loginEvents.value = loginResult;
        operationalReport.value = reportResult;
        break;
      }
    }
  } catch (caught) {
    error.value = api.errorMessage(
      caught,
      `${tabs.value.find((tab) => tab.id === activeTab.value)?.label ?? "Core"} data could not be loaded.`,
    );
    await showError("Workspace could not be loaded", error.value);
  } finally {
    loading.value = false;
  }
}

function normalizeSearch(value: unknown) {
  return String(value ?? "").toLowerCase();
}

function tableRows<T extends Record<string, unknown>>(rows: T[], state: TableState) {
  let result = [...rows];
  const search = state.search?.trim().toLowerCase();
  if (search) {
    result = result.filter((row) =>
      Object.values(row).some((value) => normalizeSearch(value).includes(search)),
    );
  }
  const sort = state.sort?.[0];
  if (sort) {
    result.sort((left, right) => {
      const leftValue = normalizeSearch(left[sort.key]);
      const rightValue = normalizeSearch(right[sort.key]);
      return sort.direction === "asc"
        ? leftValue.localeCompare(rightValue)
        : rightValue.localeCompare(leftValue);
    });
  }
  const start = (state.page - 1) * state.pageSize;
  return result.slice(start, start + state.pageSize);
}

function tableTotal<T extends Record<string, unknown>>(rows: T[], state: TableState) {
  const search = state.search?.trim().toLowerCase();
  if (!search) {
    return rows.length;
  }
  return rows.filter((row) =>
    Object.values(row).some((value) => normalizeSearch(value).includes(search)),
  ).length;
}

function openDrawer(kind: CoreDrawer) {
  if (kind === "profile") {
    resetProfileForm();
  }
  drawerKind.value = kind;
  drawerOpen.value = true;
}

async function createUser() {
  resetUserForm();
  try {
    await ensureUserProvisioningOptions();
    openDrawer("user");
  } catch (caught) {
    await showError(
      "Access options could not be loaded",
      api.errorMessage(caught, "Roles and academic units are unavailable."),
    );
  }
}

async function ensureUserProvisioningOptions() {
  const [roleResult] = await Promise.all([
    roles.value.length ? Promise.resolve(roles.value) : api.request<CoreRole[]>("/api/core/roles"),
    academicSetup.ensureOverview(),
  ]);
  roles.value = roleResult;
}

function createUserAccessDraft(): UserAccessDraft {
  userAccessDraftSequence += 1;
  return {
    key: `user-access-${userAccessDraftSequence}`,
    roleId: "",
    academicUnitId: "",
    startsAt: "",
  };
}

function userRoleAssignmentDraft(assignment: UserRoleAssignment): UserAccessDraft {
  userAccessDraftSequence += 1;
  return {
    key: `user-access-${userAccessDraftSequence}`,
    assignmentId: assignment.id,
    roleId: assignment.roleId,
    academicUnitId: assignment.academicUnitId ?? "",
    startsAt: assignment.startsAt,
  };
}

function addUserAccessDraft() {
  userAccessDrafts.value.push(createUserAccessDraft());
}

function removeUserAccessDraft(key: string) {
  if (userAccessDrafts.value.length === 1) {
    Object.assign(userAccessDrafts.value[0]!, createUserAccessDraft());
    return;
  }
  userAccessDrafts.value = userAccessDrafts.value.filter((assignment) => assignment.key !== key);
}

function userProvisioningRole(assignment: UserAccessDraft) {
  return roles.value.find((role) => role.id === assignment.roleId);
}

function userProvisioningRoleNeedsScope(assignment: UserAccessDraft) {
  return userProvisioningRole(assignment)?.scope === "ACADEMIC_UNIT";
}

function userIdentityStepComplete() {
  return Boolean(userForm.username.trim() && userForm.email.trim() && userForm.displayName.trim());
}

function roleProvidesUsableAccess(roleId: string) {
  const role = roles.value.find((candidate) => candidate.id === roleId);
  if (!role) return false;
  if (["APPLICANT", "STUDENT"].includes(role.code)) return true;
  return Boolean(rolePermissionCatalog.value[roleId]?.length);
}

function userAccessStepComplete() {
  if (!userAccessAssignmentsComplete()) return false;
  return userAccessDrafts.value.every((assignment) => {
    const role = userProvisioningRole(assignment);
    if (!role || rolePermissionLoadingIds.value.has(role.id)) return false;
    return roleProvidesUsableAccess(role.id);
  });
}

function userAccessAssignmentsComplete() {
  if (!userAccessDrafts.value.length) return false;
  const assignmentKeys = new Set<string>();
  return userAccessDrafts.value.every((assignment) => {
    const role = userProvisioningRole(assignment);
    if (!role) return false;
    if (role.scope === "ACADEMIC_UNIT" && !assignment.academicUnitId) {
      return false;
    }
    const assignmentKey = `${role.id}:${assignment.academicUnitId}`;
    if (assignmentKeys.has(assignmentKey)) return false;
    assignmentKeys.add(assignmentKey);
    return true;
  });
}

async function updateUserProvisioningRole(assignment: UserAccessDraft, roleId: unknown) {
  assignment.roleId = String(roleId ?? "");
  if (!userProvisioningRoleNeedsScope(assignment)) {
    assignment.academicUnitId = "";
  }
  if (!assignment.roleId || rolePermissionCatalog.value[assignment.roleId]) {
    return;
  }
  const selectedRoleId = assignment.roleId;
  rolePermissionLoadingIds.value = new Set([...rolePermissionLoadingIds.value, selectedRoleId]);
  try {
    rolePermissionCatalog.value = {
      ...rolePermissionCatalog.value,
      [selectedRoleId]: await api.request<RolePermissionGrant[]>(
        `/api/core/roles/${selectedRoleId}/permissions`,
      ),
    };
  } catch (caught) {
    rolePermissionCatalog.value = {
      ...rolePermissionCatalog.value,
      [selectedRoleId]: [],
    };
    await showError(
      "Role permissions could not be loaded",
      api.errorMessage(caught, "Choose the role again or try later."),
    );
  } finally {
    const loadingRoleIds = new Set(rolePermissionLoadingIds.value);
    loadingRoleIds.delete(selectedRoleId);
    rolePermissionLoadingIds.value = loadingRoleIds;
  }
}

function roleAccessAreas(roleId: string) {
  const role = roles.value.find((candidate) => candidate.id === roleId);
  if (!role) return [];
  const primaryAccessAreas: Record<string, string[]> = {
    SYSTEM_ADMIN: ["All operational sections"],
    ACADEMIC_ADMIN: ["Academic Setup", "Teaching and Assessment"],
    ADMISSIONS_OFFICER: ["Admissions"],
    FINANCE_OFFICER: ["Finance"],
    REGISTRY_OFFICER: ["Student Records and Registration"],
    ASSESSMENT_OFFICER: ["Teaching and Assessment"],
    EXAMS_OFFICER: ["Exams and Timetabling"],
    EXAM_INVIGILATOR: ["Exams and Timetabling"],
    ACCOMMODATION_OFFICER: ["Accommodation"],
    DINING_OFFICER: ["Dining"],
    DOCUMENTS_OFFICER: ["Documents and Reporting"],
    REPORTING_OFFICER: ["Documents and Reporting"],
    NOTIFICATIONS_OFFICER: ["Notifications"],
    APPLICANT: ["Applicant portal"],
    STUDENT: ["Student portal"],
  };
  return [
    ...new Set([
      ...(primaryAccessAreas[role.code] ?? []),
      ...(rolePermissionCatalog.value[roleId] ?? []).map((grant) =>
        permissionCategoryLabel(grant.category),
      ),
    ]),
  ];
}

function permissionCategoryLabel(category: string) {
  return category
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function academicUnitLabel(academicUnitId: string) {
  if (!academicUnitId) return "Institution-wide";
  return (
    academicUnitOptions.value.find((option) => option.value === academicUnitId)?.label ??
    academicUnitId
  );
}

function advanceUserProvisioning() {
  if (userProvisioningStep.value === "identity") {
    userProvisioningStep.value = "access";
  } else if (userProvisioningStep.value === "access") {
    userProvisioningStep.value = "review";
  }
}

function returnToPreviousUserProvisioningStep() {
  if (userProvisioningStep.value === "review") {
    userProvisioningStep.value = "access";
  } else if (userProvisioningStep.value === "access") {
    userProvisioningStep.value = "identity";
  }
}

function createRole() {
  resetRoleForm();
  openDrawer("role");
}

function createPermission() {
  resetPermissionForm();
  openDrawer("permission");
}

function createCountry() {
  resetCountryForm();
  openDrawer("country");
}

function createLookupSet() {
  resetLookupSetForm();
  openDrawer("lookup-set");
}

function createLookupValue() {
  resetLookupValueForm();
  openDrawer("lookup-value");
}

function createQualificationSubject(level: "O_LEVEL" | "A_LEVEL") {
  resetQualificationSubjectForm(level);
  openDrawer("qualification-subject");
}

function createQualificationGrade(level: "O_LEVEL" | "A_LEVEL") {
  resetQualificationGradeForm(level);
  openDrawer("qualification-grade");
}

function openWorkflowTask(row: Record<string, unknown>) {
  selectedWorkflowTask.value = row as unknown as WorkflowTask;
  Object.assign(workflowDecisionForm, {
    decisionCode: "COMPLETED",
    comment: "",
  });
  openDrawer("workflow-task");
}

function handleDrawerClosed() {
  switch (drawerKind.value) {
    case "profile":
      resetProfileForm();
      void loadStoredProfileLogo();
      break;
    case "user":
      resetUserForm();
      break;
    case "role":
      resetRoleForm();
      break;
    case "permission":
      resetPermissionForm();
      break;
    case "country":
      resetCountryForm();
      break;
    case "lookup-set":
      resetLookupSetForm();
      break;
    case "lookup-value":
      resetLookupValueForm();
      break;
    case "qualification-subject":
      resetQualificationSubjectForm();
      break;
    case "qualification-grade":
      resetQualificationGradeForm();
      break;
    case "workflow-task":
      selectedWorkflowTask.value = null;
      Object.assign(workflowDecisionForm, {
        decisionCode: "COMPLETED",
        comment: "",
      });
      break;
  }
  drawerKind.value = null;
}

async function submitDrawer() {
  if (!drawerKind.value || drawerSubmitDisabled.value) {
    return;
  }
  if (drawerKind.value === "user" && !userForm.id && userProvisioningStep.value !== "review") {
    advanceUserProvisioning();
    return;
  }
  drawerSaving.value = true;
  try {
    switch (drawerKind.value) {
      case "profile":
        await saveProfile();
        break;
      case "user":
        await saveUser();
        break;
      case "role":
        await saveRole();
        break;
      case "permission":
        await savePermission();
        break;
      case "grant":
        await grantPermission();
        break;
      case "assignment":
        await assignRole();
        break;
      case "country":
        await saveCountry();
        break;
      case "lookup-set":
        await saveLookupSet();
        break;
      case "lookup-value":
        await saveLookupValue();
        break;
      case "qualification-subject":
        await saveQualificationSubject();
        break;
      case "qualification-grade":
        await saveQualificationGrade();
        break;
      case "workflow-task":
        await progressWorkflowTask();
        break;
    }
    drawerOpen.value = false;
  } catch (caught) {
    const message = caught instanceof Error ? caught.message : "The record could not be saved.";
    await showError("Save failed", message);
  } finally {
    drawerSaving.value = false;
  }
}

async function saveProfile() {
  let savedProfile = await api.request<InstitutionProfile>("/api/core/institution-profile", {
    method: "PUT",
    body: profilePayload(),
  });
  let brandingAssetsChanged = false;
  if (profileLogoFile.value) {
    if (!savedProfile.id) {
      throw new Error("The institution profile must be saved before its logo can be uploaded.");
    }
    const logoUpload = new FormData();
    logoUpload.set("ownerType", "INSTITUTION");
    logoUpload.set("ownerId", savedProfile.id);
    logoUpload.set("documentTypeCode", "INSTITUTION_LOGO");
    logoUpload.set("file", profileLogoFile.value);
    const uploadedLogo = await api.request<UploadedDocumentSummary>("/api/documents/uploads", {
      method: "POST",
      body: logoUpload,
    });
    profileBrandingForm.logoDocumentId = uploadedLogo.id;
    brandingAssetsChanged = true;
  }
  if (profileRegistrarSignatureFile.value) {
    if (!savedProfile.id) {
      throw new Error(
        "The institution profile must be saved before its registrar signature can be uploaded.",
      );
    }
    const signatureUpload = new FormData();
    signatureUpload.set("ownerType", "INSTITUTION");
    signatureUpload.set("ownerId", savedProfile.id);
    signatureUpload.set("documentTypeCode", "INSTITUTION_REGISTRAR_SIGNATURE");
    signatureUpload.set("file", profileRegistrarSignatureFile.value);
    const uploadedSignature = await api.request<UploadedDocumentSummary>("/api/documents/uploads", {
      method: "POST",
      body: signatureUpload,
    });
    profileBrandingForm.registrarSignatureDocumentId = uploadedSignature.id;
    brandingAssetsChanged = true;
  }
  if (brandingAssetsChanged) {
    savedProfile = await api.request<InstitutionProfile>("/api/core/institution-profile", {
      method: "PUT",
      body: profilePayload(),
    });
  }
  institutionProfile.value = savedProfile;
  await auth.syncCoreUser();
  profileLogoFile.value = null;
  profileRegistrarSignatureFile.value = null;
  revokeProfileLogoPreview();
  revokeProfileRegistrarSignaturePreview();
  await showSuccess(
    "Institution profile saved",
    profileBrandingForm.logoDocumentId || profileBrandingForm.registrarSignatureDocumentId
      ? "Institution details and brand assets are updated."
      : "Institution details are updated.",
  );
  await loadCoreData();
}

function profilePayload() {
  const existingContactDetails = parseProfileJson(institutionProfile.value.contactDetailsJson);
  const existingBranding = parseProfileJson(institutionProfile.value.brandingJson);
  const existingBankDetails = parseProfileJson(institutionProfile.value.bankDetailsJson);
  return {
    code: profileForm.code,
    name: profileForm.name,
    legalName: profileForm.legalName,
    registrarName: profileForm.registrarName,
    defaultCurrencyCode: profileForm.defaultCurrencyCode,
    countryCode: profileForm.countryCode,
    timezone: profileForm.timezone,
    legacyCode: profileForm.legacyCode,
    contactDetailsJson: profileJsonString(existingContactDetails, {
      email: profileContactForm.email,
      phone: profileContactForm.phone,
      website: profileContactForm.website,
    }),
    brandingJson: profileJsonString(existingBranding, {
      documentHeader: profileBrandingForm.documentHeader,
      primaryColor: profileBrandingForm.primaryColor,
      secondaryColor: profileBrandingForm.secondaryColor,
      logoDocumentId: profileBrandingForm.logoDocumentId,
      registrarSignatureDocumentId: profileBrandingForm.registrarSignatureDocumentId,
    }),
    bankDetailsJson: profileBankDetailsJson(existingBankDetails, profileBankAccounts.value),
  };
}

async function saveUser() {
  const editingExistingUser = Boolean(userForm.id);
  const body = {
    username: userForm.username,
    email: userForm.email,
    displayName: userForm.displayName,
    phoneNumber: userForm.phoneNumber,
    status: userForm.status,
  };
  if (userForm.id) {
    await saveEditedUserAccessAssignments(userForm.id);
    await api.request(`/api/core/users/${userForm.id}`, {
      method: "PUT",
      body,
    });
  } else {
    const provisionedAccess = await api.request<ProvisionedUserAccess>(
      "/api/core/users/provisioned-access",
      {
        method: "POST",
        body: {
          username: userForm.username,
          email: userForm.email,
          displayName: userForm.displayName,
          phoneNumber: userForm.phoneNumber,
          roleAssignments: userAccessDrafts.value.map((assignment) => ({
            roleId: assignment.roleId,
            academicUnitId: assignment.academicUnitId || undefined,
            startsAt: assignment.startsAt || undefined,
          })),
        },
      },
    );
    await showSuccess(
      provisionedAccess.keycloakIdentityCreated ? "User provisioned" : "User access linked",
      provisionedAccess.temporaryPassword
        ? `The Keycloak account and local access are active. Sign in with ${provisionedAccess.user.email}. Temporary password: ${provisionedAccess.temporaryPassword} The user must change it at first sign-in.`
        : "The existing Keycloak account is linked to the active local access profile.",
    );
  }
  resetUserForm();
  if (editingExistingUser) {
    await showSuccess("User saved", "The Core user catalogue is updated.");
  }
  await loadCoreData();
}

async function saveEditedUserAccessAssignments(userId: string) {
  const originalAssignments = new Map(
    editingUserRoleAssignments.value.map((assignment) => [assignment.id, assignment]),
  );
  for (const assignment of userAccessDrafts.value) {
    if (!assignment.assignmentId) {
      await api.request(`/api/core/users/${userId}/role-assignments`, {
        method: "POST",
        body: {
          roleId: assignment.roleId,
          academicUnitId: assignment.academicUnitId || undefined,
          startsAt: assignment.startsAt || undefined,
        },
      });
      continue;
    }
    const originalAssignment = originalAssignments.get(assignment.assignmentId);
    if ((originalAssignment?.academicUnitId ?? "") !== assignment.academicUnitId) {
      await api.request(
        `/api/core/users/${userId}/role-assignments/${assignment.assignmentId}/academic-unit`,
        {
          method: "PUT",
          body: { academicUnitId: assignment.academicUnitId },
        },
      );
    }
  }
  const retainedAssignmentIds = new Set(
    userAccessDrafts.value
      .map((assignment) => assignment.assignmentId)
      .filter((assignmentId): assignmentId is string => Boolean(assignmentId)),
  );
  for (const originalAssignment of editingUserRoleAssignments.value) {
    if (!retainedAssignmentIds.has(originalAssignment.id)) {
      await api.request(`/api/core/users/${userId}/role-assignments/${originalAssignment.id}`, {
        method: "DELETE",
      });
    }
  }
}

async function editUser(row: Record<string, unknown>) {
  try {
    const [, roleAssignments] = await Promise.all([
      ensureUserProvisioningOptions(),
      api.request<UserRoleAssignment[]>(`/api/core/users/${String(row.id)}/role-assignments`),
    ]);
    Object.assign(userForm, {
      id: row.id,
      username: row.username,
      email: row.email,
      phoneNumber: row.phoneNumber ?? "",
      displayName: row.displayName,
      status: row.status,
    });
    editingUserRoleAssignments.value = roleAssignments;
    userAccessDrafts.value = roleAssignments.length
      ? roleAssignments.map(userRoleAssignmentDraft)
      : [createUserAccessDraft()];
    activeTab.value = "users";
    openDrawer("user");
  } catch (caught) {
    await showError(
      "User access could not be loaded",
      api.errorMessage(caught, "Roles and academic units are unavailable."),
    );
  }
}

async function disableUser(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Disable user?",
      text: `Disable ${row.displayName}?`,
      confirmButtonText: "Disable user",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/users/${row.id}`, { method: "DELETE" });
  await showSuccess("User disabled", "The user was soft-deleted and marked disabled.");
  await loadCoreData();
}

function resetUserForm() {
  Object.assign(userForm, {
    id: "",
    username: "",
    email: "",
    phoneNumber: "",
    displayName: "",
    status: "INVITED",
  });
  userProvisioningStep.value = "identity";
  userAccessDrafts.value = [createUserAccessDraft()];
  editingUserRoleAssignments.value = [];
  rolePermissionCatalog.value = {};
  rolePermissionLoadingIds.value = new Set();
}

async function saveRole() {
  const body = {
    code: roleForm.code,
    name: roleForm.name,
    scope: roleForm.scope,
    systemManaged: roleForm.systemManaged,
  };
  if (roleForm.id) {
    await api.request(`/api/core/roles/${roleForm.id}`, {
      method: "PUT",
      body,
    });
  } else {
    await api.request("/api/core/roles", { method: "POST", body });
  }
  resetRoleForm();
  await showSuccess("Role saved", "RBAC roles are updated.");
  await loadCoreData();
}

function editRole(row: Record<string, unknown>) {
  Object.assign(roleForm, row);
  selectedRoleId.value = String(row.id);
  openDrawer("role");
}

async function deleteRole(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete role?",
      text: `Soft delete ${row.name}?`,
      confirmButtonText: "Delete role",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/roles/${row.id}`, { method: "DELETE" });
  await showSuccess("Role deleted", "The role was soft-deleted.");
  await loadCoreData();
}

function resetRoleForm() {
  Object.assign(roleForm, {
    id: "",
    code: "",
    name: "",
    scope: "SYSTEM",
    systemManaged: false,
  });
}

async function savePermission() {
  const body = {
    code: permissionForm.code,
    name: permissionForm.name,
    category: permissionForm.category,
    description: permissionForm.description,
  };
  if (permissionForm.id) {
    await api.request(`/api/core/permissions/${permissionForm.id}`, {
      method: "PUT",
      body,
    });
  } else {
    await api.request("/api/core/permissions", { method: "POST", body });
  }
  resetPermissionForm();
  await showSuccess("Permission saved", "Permission catalogue is updated.");
  await loadCoreData();
}

function editPermission(row: Record<string, unknown>) {
  Object.assign(permissionForm, row);
  openDrawer("permission");
}

async function deletePermission(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete permission?",
      text: `Soft delete ${row.name}?`,
      confirmButtonText: "Delete permission",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/permissions/${row.id}`, { method: "DELETE" });
  await showSuccess("Permission deleted", "The permission was soft-deleted.");
  await loadCoreData();
}

function resetPermissionForm() {
  Object.assign(permissionForm, {
    id: "",
    code: "",
    name: "",
    category: "CORE",
    description: "",
  });
}

async function grantPermission() {
  if (!selectedRoleId.value || !grantForm.permissionId) {
    return;
  }
  await api.request(`/api/core/roles/${selectedRoleId.value}/permissions`, {
    method: "POST",
    body: { permissionId: grantForm.permissionId },
  });
  grantForm.permissionId = "";
  rolePermissions.value = await api.request<RolePermissionGrant[]>(
    `/api/core/roles/${selectedRoleId.value}/permissions`,
  );
  await showSuccess("Permission granted", "The role permission matrix is updated.");
}

async function revokePermission(grant: RolePermissionGrant) {
  if (
    !(await confirmAction({
      title: "Revoke permission?",
      text: grant.permissionName,
      confirmButtonText: "Revoke",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/roles/${grant.roleId}/permissions/${grant.permissionId}`, {
    method: "DELETE",
  });
  rolePermissions.value = await api.request<RolePermissionGrant[]>(
    `/api/core/roles/${selectedRoleId.value}/permissions`,
  );
}

async function assignRole() {
  const userId = assignmentForm.userId || selectedUserId.value;
  if (!userId || !assignmentForm.roleId) {
    return;
  }
  await api.request(`/api/core/users/${userId}/role-assignments`, {
    method: "POST",
    body: {
      roleId: assignmentForm.roleId,
      academicUnitId: assignmentForm.academicUnitId || undefined,
      startsAt: assignmentForm.startsAt || undefined,
    },
  });
  Object.assign(assignmentForm, {
    userId,
    roleId: "",
    academicUnitId: "",
    startsAt: "",
  });
  assignments.value = await api.request<UserRoleAssignment[]>(
    `/api/core/users/${userId}/role-assignments`,
  );
  await showSuccess("Role assigned", "The assignment is active.");
}

async function expireAssignment(assignment: UserRoleAssignment) {
  const userId = assignmentForm.userId || selectedUserId.value;
  if (
    !userId ||
    assignment.endsAt ||
    !(await confirmAction({
      title: "Expire assignment?",
      text: assignment.roleName,
      confirmButtonText: "Expire",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/users/${userId}/role-assignments/${assignment.id}`, {
    method: "DELETE",
  });
  assignments.value = await api.request<UserRoleAssignment[]>(
    `/api/core/users/${userId}/role-assignments`,
  );
}

async function saveCountry() {
  await api.request("/api/core/countries", {
    method: "POST",
    body: { ...countryForm },
  });
  resetCountryForm();
  await showSuccess("Country saved", "Reference country data is updated.");
  await loadCoreData();
}

function editCountry(row: Record<string, unknown>) {
  Object.assign(countryForm, row);
  openDrawer("country");
}

async function deleteCountry(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete country?",
      text: String(row.name),
      confirmButtonText: "Delete country",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/countries/${row.id}`, { method: "DELETE" });
  await loadCoreData();
}

function resetCountryForm() {
  Object.assign(countryForm, {
    id: "",
    iso2Code: "",
    iso3Code: "",
    name: "",
    nationalityName: "",
  });
}

async function saveLookupSet() {
  await api.request("/api/core/lookup-sets", {
    method: "POST",
    body: { ...lookupSetForm },
  });
  resetLookupSetForm();
  await showSuccess("Lookup set saved", "Lookup configuration is updated.");
  await loadCoreData();
}

function editLookupSet(row: Record<string, unknown>) {
  Object.assign(lookupSetForm, row);
  selectedLookupSetId.value = String(row.id);
  openDrawer("lookup-set");
}

async function deleteLookupSet(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete lookup set?",
      text: String(row.name),
      confirmButtonText: "Delete set",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/lookup-sets/${row.id}`, { method: "DELETE" });
  await loadCoreData();
}

function resetLookupSetForm() {
  Object.assign(lookupSetForm, { id: "", code: "", name: "", description: "" });
}

async function saveLookupValue() {
  if (!selectedLookupSetId.value) {
    return;
  }
  await api.request(`/api/core/lookup-sets/${selectedLookupSetId.value}/values`, {
    method: "POST",
    body: { ...lookupValueForm },
  });
  resetLookupValueForm();
  lookupValues.value = await api.request<LookupValue[]>(
    `/api/core/lookup-sets/${selectedLookupSetId.value}/values`,
  );
  await showSuccess("Lookup value saved", "Lookup values are updated.");
}

async function progressWorkflowTask() {
  const task = selectedWorkflowTask.value;
  if (!task) return;
  if (task.status === "OPEN") {
    await api.request(`/api/core/workflows/tasks/${task.id}/claim`, {
      method: "POST",
      body: { expectedVersion: task.version },
    });
    await showSuccess(
      "Workflow task claimed",
      `${task.taskReference} is now reserved for your decision.`,
    );
  } else if (task.status === "CLAIMED") {
    await api.request(`/api/core/workflows/tasks/${task.id}/decision`, {
      method: "POST",
      body: {
        expectedVersion: task.version,
        decisionCode: workflowDecisionForm.decisionCode,
        comment: workflowDecisionForm.comment.trim(),
      },
    });
    await showSuccess(
      "Workflow decision recorded",
      `${task.taskReference} has immutable decision evidence.`,
    );
  }
  workflowTasks.value = await api.request<WorkflowTask[]>("/api/core/workflows/tasks");
}

function editLookupValue(row: Record<string, unknown>) {
  Object.assign(lookupValueForm, row);
  openDrawer("lookup-value");
}

async function deleteLookupValue(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete lookup value?",
      text: String(row.name),
      confirmButtonText: "Delete value",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(`/api/core/lookup-values/${row.id}`, { method: "DELETE" });
  lookupValues.value = await api.request<LookupValue[]>(
    `/api/core/lookup-sets/${selectedLookupSetId.value}/values`,
  );
}

function resetLookupValueForm() {
  Object.assign(lookupValueForm, {
    id: "",
    code: "",
    name: "",
    sortOrder: 0,
    active: true,
  });
}

async function saveQualificationSubject() {
  const subjectId = qualificationSubjectForm.id;
  const subjectLevel = qualificationSubjectForm.level as "O_LEVEL" | "A_LEVEL";
  await api.request(
    subjectId
      ? `/api/admissions/qualification-reference-data/subjects/${subjectId}`
      : "/api/admissions/qualification-reference-data/subjects",
    {
      method: subjectId ? "PUT" : "POST",
      body: {
        level: qualificationSubjectForm.level,
        code: qualificationSubjectForm.code.trim(),
        name: qualificationSubjectForm.name.trim(),
        subjectGroupCode: qualificationSubjectForm.subjectGroupCode,
        scienceSubject: qualificationSubjectForm.scienceSubject,
        mathematicsSubject: qualificationSubjectForm.mathematicsSubject,
        englishSubject: qualificationSubjectForm.englishSubject,
        active: qualificationSubjectForm.active,
        expectedVersion: qualificationSubjectForm.version,
      },
    },
  );
  resetQualificationSubjectForm(subjectLevel);
  await showSuccess(
    subjectId ? "Subject updated" : "Subject created",
    "Qualification subject reference data is updated.",
  );
  await loadCoreData();
  activeReferenceDataset.value =
    subjectLevel === "O_LEVEL" ? "o-level-subjects" : "a-level-subjects";
}

function editQualificationSubject(row: Record<string, unknown>, level: "O_LEVEL" | "A_LEVEL") {
  Object.assign(qualificationSubjectForm, {
    id: String(row.id),
    level,
    code: String(row.code),
    name: String(row.name),
    subjectGroupCode: String(row.subjectGroupCode),
    scienceSubject: Boolean(row.scienceSubject),
    mathematicsSubject: Boolean(row.mathematicsSubject),
    englishSubject: Boolean(row.englishSubject),
    active: Boolean(row.active),
    version: Number(row.version),
  });
  openDrawer("qualification-subject");
}

async function deleteQualificationSubject(row: Record<string, unknown>) {
  if (
    !(await confirmAction({
      title: "Delete qualification subject?",
      text: `${String(row.code)} · ${String(row.name)}`,
      confirmButtonText: "Delete subject",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(
    `/api/admissions/qualification-reference-data/subjects/${row.id}?expectedVersion=${row.version}`,
    { method: "DELETE" },
  );
  await showSuccess(
    "Subject deleted",
    "The subject is no longer available for new qualification capture. Historical results are preserved.",
  );
  await loadCoreData();
}

function resetQualificationSubjectForm(level: "O_LEVEL" | "A_LEVEL" = "O_LEVEL") {
  Object.assign(qualificationSubjectForm, {
    id: "",
    level,
    code: "",
    name: "",
    subjectGroupCode: "HUMANITIES",
    scienceSubject: false,
    mathematicsSubject: false,
    englishSubject: false,
    active: true,
    version: 0,
  });
}

async function saveQualificationGrade() {
  const gradeId = qualificationGradeForm.id;
  const gradeLevel = qualificationGradeForm.level as "O_LEVEL" | "A_LEVEL";
  await api.request(
    gradeId
      ? `/api/admissions/qualification-reference-data/grades/${gradeId}`
      : "/api/admissions/qualification-reference-data/grades",
    {
      method: gradeId ? "PUT" : "POST",
      body: {
        level: qualificationGradeForm.level,
        grade: qualificationGradeForm.grade.trim(),
        points: qualificationGradeForm.points ?? null,
        pass: qualificationGradeForm.pass,
        sortOrder: qualificationGradeForm.sortOrder,
        expectedVersion: qualificationGradeForm.version,
      },
    },
  );
  resetQualificationGradeForm(gradeLevel);
  await showSuccess(
    gradeId ? "Grade updated" : "Grade created",
    "Qualification grade reference data is updated.",
  );
  await loadCoreData();
  activeReferenceDataset.value = gradeLevel === "O_LEVEL" ? "o-level-grades" : "a-level-grades";
}

function editQualificationGrade(row: Record<string, unknown>, level: "O_LEVEL" | "A_LEVEL") {
  Object.assign(qualificationGradeForm, {
    id: String(row.id),
    level,
    grade: String(row.grade),
    points: row.points === null || row.points === undefined ? undefined : Number(row.points),
    pass: Boolean(row.pass),
    sortOrder: Number(row.sortOrder),
    version: Number(row.version),
  });
  openDrawer("qualification-grade");
}

async function deleteQualificationGrade(
  row: Record<string, unknown>,
  level: "O_LEVEL" | "A_LEVEL",
) {
  if (
    !(await confirmAction({
      title: "Delete qualification grade?",
      text: `${level === "O_LEVEL" ? "O Level" : "A Level"} grade ${String(row.grade)}`,
      confirmButtonText: "Delete grade",
      destructive: true,
    }))
  ) {
    return;
  }
  await api.request(
    `/api/admissions/qualification-reference-data/grades/${row.id}?expectedVersion=${row.version}`,
    { method: "DELETE" },
  );
  await showSuccess(
    "Grade deleted",
    "The grade is no longer available for new qualification capture. Historical results are preserved.",
  );
  await loadCoreData();
  activeReferenceDataset.value = level === "O_LEVEL" ? "o-level-grades" : "a-level-grades";
}

function resetQualificationGradeForm(level: "O_LEVEL" | "A_LEVEL" = "O_LEVEL") {
  Object.assign(qualificationGradeForm, {
    id: "",
    level,
    grade: "",
    points: undefined,
    pass: false,
    sortOrder: 0,
    version: 0,
  });
}

function revokeGrantRow(row: Record<string, unknown>) {
  return revokePermission(row as unknown as RolePermissionGrant);
}

function expireAssignmentRow(row: Record<string, unknown>) {
  return expireAssignment(row as unknown as UserRoleAssignment);
}

function rowAction(
  payload: { action: { id: string }; row: Record<string, unknown> },
  handlers: Record<string, (row: Record<string, unknown>) => void | Promise<void>>,
) {
  return handlers[payload.action.id]?.(payload.row);
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Core Identity">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            label="Refresh"
            color="primary"
            :loading="loading"
            @click="loadCoreData"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <div class="flex flex-wrap gap-2">
            <UButton
              v-for="tab in tabs"
              :key="tab.id"
              :icon="tab.icon"
              :label="tab.label"
              :color="activeTab === tab.id ? 'primary' : 'neutral'"
              :variant="activeTab === tab.id ? 'solid' : 'ghost'"
              @click="activeTab = tab.id"
            />
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-4 p-4">
        <UAlert
          v-if="error"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Core error"
          :description="error"
        />

        <section v-if="activeTab === 'profile'" class="space-y-4">
          <EmhareRegisterPanel
            title="Institution profile"
            description="Single-institution identity, branding, contact and official document defaults."
          >
            <template #actions>
              <UButton
                icon="i-lucide-panel-right-open"
                label="Edit profile"
                color="primary"
                @click="openDrawer('profile')"
              />
            </template>
            <div class="grid gap-4 md:grid-cols-[8rem_minmax(0,1fr)] md:items-start">
              <div data-emhare-institution-logo aria-label="Institution logo" class="min-w-0">
                <div
                  class="flex aspect-square items-center justify-center overflow-hidden rounded-xl border border-muted bg-default p-3 shadow-sm"
                >
                  <USkeleton v-if="profileLogoLoading" class="size-full rounded-lg" />
                  <img
                    v-else-if="storedProfileLogoUrl"
                    :src="storedProfileLogoUrl"
                    :alt="`${institutionProfile.name} logo`"
                    class="size-full object-contain"
                  />
                  <div
                    v-else
                    data-emhare-logo-fallback
                    class="flex size-full flex-col items-center justify-center gap-2 rounded-lg bg-primary/10 text-primary"
                  >
                    <UIcon name="i-lucide-landmark" class="size-8" />
                    <span class="text-sm font-bold tracking-wide">
                      {{ institutionProfile.code || "Institution" }}
                    </span>
                  </div>
                </div>
                <p class="mt-2 text-center text-xs font-medium text-muted">Institution logo</p>
              </div>
              <EmhareDescriptionList :items="profileDetails" />
            </div>
          </EmhareRegisterPanel>

          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <EmhareKpiCard
              data-testid="core-stat-users"
              label="Users"
              :value="coreStatistics.userCount"
              icon="i-lucide-users"
            />
            <EmhareKpiCard
              data-testid="core-stat-roles"
              label="Roles"
              :value="coreStatistics.roleCount"
              icon="i-lucide-shield-check"
            />
            <EmhareKpiCard
              data-testid="core-stat-permissions"
              label="Permissions"
              :value="coreStatistics.permissionCount"
              icon="i-lucide-list-checks"
            />
            <EmhareKpiCard
              data-testid="core-stat-lookup-sets"
              label="Lookup sets"
              :value="coreStatistics.lookupSetCount"
              icon="i-lucide-bookmark"
            />
          </div>
        </section>

        <section v-if="activeTab === 'users'">
          <EmhareRegisterPanel
            title="Users"
            description="Keycloak identities governed by local role assignments and operating scope."
            :record-count="users.length"
          >
            <template #actions>
              <UButton
                v-if="auth.hasPermission('CORE_ROLE_ASSIGN')"
                icon="i-lucide-plus"
                label="Create user"
                color="primary"
                @click="createUser"
              />
            </template>
            <EmhareDataTable
              :columns="userColumns"
              :rows="tableRows(users as unknown as Record<string, unknown>[], userTableState)"
              :total="tableTotal(users as unknown as Record<string, unknown>[], userTableState)"
              :state="userTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'disable',
                  label: 'Disable',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="userTableState = $event"
              @row-action="rowAction($event, { edit: editUser, disable: disableUser })"
            >
              <template #status-cell="{ value }">
                <EmhareStatusPill
                  :label="String(value)"
                  :tone="
                    value === 'ACTIVE'
                      ? 'success'
                      : value === 'LOCKED'
                        ? 'warning'
                        : value === 'DISABLED'
                          ? 'error'
                          : 'neutral'
                  "
                />
              </template>
              <template #lastLoginAt-cell="{ value }">
                <span>{{ value ? new Date(String(value)).toLocaleString() : "Never" }}</span>
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <section v-if="activeTab === 'rbac'" class="space-y-4">
          <UTabs
            v-model="activeRbacDataset"
            :items="rbacDatasets"
            :content="false"
            color="primary"
            variant="pill"
          />

          <EmhareRegisterPanel
            v-if="activeRbacDataset === 'roles'"
            title="Roles"
            description="Responsibility bundles used to grant operational access."
            :record-count="roles.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create role"
                color="primary"
                @click="createRole"
              />
            </template>
            <EmhareDataTable
              :columns="roleColumns"
              :rows="tableRows(roles as unknown as Record<string, unknown>[], roleTableState)"
              :total="tableTotal(roles as unknown as Record<string, unknown>[], roleTableState)"
              :state="roleTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="roleTableState = $event"
              @row-action="rowAction($event, { edit: editRole, delete: deleteRole })"
            />
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeRbacDataset === 'permissions'"
            title="Permissions"
            description="Atomic business capabilities assigned through roles."
            :record-count="permissions.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create permission"
                color="primary"
                @click="createPermission"
              />
            </template>
            <EmhareDataTable
              :columns="permissionColumns"
              :rows="
                tableRows(permissions as unknown as Record<string, unknown>[], permissionTableState)
              "
              :total="
                tableTotal(
                  permissions as unknown as Record<string, unknown>[],
                  permissionTableState,
                )
              "
              :state="permissionTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="permissionTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: editPermission,
                  delete: deletePermission,
                })
              "
            />
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeRbacDataset === 'grants'"
            :title="selectedRole ? `Role grants · ${selectedRole.name}` : 'Role grants'"
            description="Permissions currently attached to the selected role."
            :record-count="rolePermissions.length"
          >
            <template #actions>
              <EmhareGuidedActionButton
                icon="i-lucide-plus"
                label="Grant permission"
                color="primary"
                guidance-title="Select a role first"
                :guidance-instructions="
                  selectedRoleId ? [] : ['Select the role that should receive the permission.']
                "
                @click="openDrawer('grant')"
              />
            </template>
            <div class="mb-3 max-w-xl">
              <EmhareFormField
                v-model="selectedRoleId"
                type="searchable-select"
                name="selectedRole"
                label="Role"
                :items="roleOptions"
              />
            </div>
            <EmhareDataTable
              :columns="grantColumns"
              :rows="
                tableRows(rolePermissions as unknown as Record<string, unknown>[], grantTableState)
              "
              :total="
                tableTotal(rolePermissions as unknown as Record<string, unknown>[], grantTableState)
              "
              :state="grantTableState"
              :loading="loading"
              :row-actions="[
                {
                  id: 'revoke',
                  label: 'Revoke',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="grantTableState = $event"
              @row-action="rowAction($event, { revoke: revokeGrantRow })"
            />
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeRbacDataset === 'assignments'"
            :title="
              selectedUser ? `User assignments · ${selectedUser.displayName}` : 'User assignments'
            "
            description="Active and historical roles for the selected user."
            :record-count="assignments.length"
          >
            <template #actions>
              <EmhareGuidedActionButton
                icon="i-lucide-plus"
                label="Assign role"
                color="primary"
                guidance-title="Select a user first"
                :guidance-instructions="
                  selectedUserId ? [] : ['Select the user who should receive the role assignment.']
                "
                @click="openDrawer('assignment')"
              />
            </template>
            <div class="mb-3 max-w-xl">
              <EmhareFormField
                v-model="selectedUserId"
                type="searchable-select"
                name="selectedUser"
                label="User"
                :items="userOptions"
              />
            </div>
            <EmhareDataTable
              :columns="assignmentColumns"
              :rows="
                tableRows(
                  assignmentRows as unknown as Record<string, unknown>[],
                  assignmentTableState,
                )
              "
              :total="
                tableTotal(
                  assignmentRows as unknown as Record<string, unknown>[],
                  assignmentTableState,
                )
              "
              :state="assignmentTableState"
              :loading="loading"
              :row-actions="[
                {
                  id: 'expire',
                  label: 'Expire assignment',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="assignmentTableState = $event"
              @row-action="rowAction($event, { expire: expireAssignmentRow })"
            >
              <template #endsAt-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Expired' : 'Active'"
                  :tone="value ? 'neutral' : 'success'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <section v-if="activeTab === 'reference'" class="space-y-4">
          <UTabs
            v-model="activeReferenceDataset"
            :items="referenceDatasets"
            :content="false"
            color="primary"
            variant="pill"
          />

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'lookup-sets'"
            title="Lookup sets"
            description="Governed collections used by operational forms and validation rules."
            :record-count="lookupSets.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create lookup set"
                color="primary"
                @click="createLookupSet"
              />
            </template>
            <EmhareDataTable
              :columns="lookupSetColumns"
              :rows="
                tableRows(lookupSets as unknown as Record<string, unknown>[], lookupSetTableState)
              "
              :total="
                tableTotal(lookupSets as unknown as Record<string, unknown>[], lookupSetTableState)
              "
              :state="lookupSetTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="lookupSetTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: editLookupSet,
                  delete: deleteLookupSet,
                })
              "
            />
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'lookup-values'"
            :title="
              selectedLookupSet ? `Lookup values · ${selectedLookupSet.name}` : 'Lookup values'
            "
            :description="
              selectedLookupSet?.description || 'Ordered values in the selected reference-data set.'
            "
            :record-count="countryLookupSelected ? countries.length : lookupValues.length"
          >
            <template #actions>
              <EmhareGuidedActionButton
                icon="i-lucide-plus"
                :label="countryLookupSelected ? 'Create country' : 'Create lookup value'"
                color="primary"
                guidance-title="Select a lookup set first"
                :guidance-instructions="
                  selectedLookupSetId
                    ? []
                    : ['Select the lookup set that should contain the new value.']
                "
                @click="countryLookupSelected ? createCountry() : createLookupValue()"
              />
            </template>
            <div class="mb-3 max-w-xl">
              <EmhareFormField
                v-model="selectedLookupSetId"
                type="searchable-select"
                name="lookupSet"
                label="Lookup set"
                :items="lookupSetOptions"
              />
            </div>
            <EmhareDataTable
              v-if="countryLookupSelected"
              :columns="countryColumns"
              :rows="
                tableRows(countries as unknown as Record<string, unknown>[], countryTableState)
              "
              :total="
                tableTotal(countries as unknown as Record<string, unknown>[], countryTableState)
              "
              :state="countryTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="countryTableState = $event"
              @row-action="rowAction($event, { edit: editCountry, delete: deleteCountry })"
            />
            <EmhareDataTable
              v-else
              :columns="lookupValueColumns"
              :rows="
                tableRows(
                  lookupValues as unknown as Record<string, unknown>[],
                  lookupValueTableState,
                )
              "
              :total="
                tableTotal(
                  lookupValues as unknown as Record<string, unknown>[],
                  lookupValueTableState,
                )
              "
              :state="lookupValueTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-x-circle',
                  tone: 'error',
                },
              ]"
              @update:state="lookupValueTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: editLookupValue,
                  delete: deleteLookupValue,
                })
              "
            >
              <template #active-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Active' : 'Inactive'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'o-level-subjects'"
            title="O Level subjects"
            description="Current ZIMSEC subject catalogue used when applicants capture O Level results. Maintained by Admissions as the qualification source of truth."
            :record-count="oLevelSubjects.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create O Level subject"
                color="primary"
                @click="createQualificationSubject('O_LEVEL')"
              />
            </template>
            <EmhareDataTable
              :columns="qualificationSubjectColumns"
              :rows="
                tableRows(
                  oLevelSubjects as unknown as Record<string, unknown>[],
                  oLevelSubjectTableState,
                )
              "
              :total="
                tableTotal(
                  oLevelSubjects as unknown as Record<string, unknown>[],
                  oLevelSubjectTableState,
                )
              "
              :state="oLevelSubjectTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-trash-2',
                  tone: 'error',
                },
              ]"
              @update:state="oLevelSubjectTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: (row) => editQualificationSubject(row, 'O_LEVEL'),
                  delete: deleteQualificationSubject,
                })
              "
            >
              <template #scienceSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
              <template #mathematicsSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'primary' : 'neutral'"
                />
              </template>
              <template #englishSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'warning' : 'neutral'"
                />
              </template>
              <template #active-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Active' : 'Inactive'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'a-level-subjects'"
            title="A Level subjects"
            description="Current ZIMSEC subject catalogue used when applicants capture A Level results. Maintained by Admissions as the qualification source of truth."
            :record-count="aLevelSubjects.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create A Level subject"
                color="primary"
                @click="createQualificationSubject('A_LEVEL')"
              />
            </template>
            <EmhareDataTable
              :columns="qualificationSubjectColumns"
              :rows="
                tableRows(
                  aLevelSubjects as unknown as Record<string, unknown>[],
                  aLevelSubjectTableState,
                )
              "
              :total="
                tableTotal(
                  aLevelSubjects as unknown as Record<string, unknown>[],
                  aLevelSubjectTableState,
                )
              "
              :state="aLevelSubjectTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-trash-2',
                  tone: 'error',
                },
              ]"
              @update:state="aLevelSubjectTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: (row) => editQualificationSubject(row, 'A_LEVEL'),
                  delete: deleteQualificationSubject,
                })
              "
            >
              <template #scienceSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
              <template #mathematicsSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'primary' : 'neutral'"
                />
              </template>
              <template #englishSubject-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Yes' : 'No'"
                  :tone="value ? 'warning' : 'neutral'"
                />
              </template>
              <template #active-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Active' : 'Inactive'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'o-level-grades'"
            title="O Level grades"
            description="ZIMSEC O Level pass and non-pass outcomes used by qualification capture and eligibility checks."
            :record-count="oLevelGrades.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create O Level grade"
                color="primary"
                @click="createQualificationGrade('O_LEVEL')"
              />
            </template>
            <EmhareDataTable
              :columns="qualificationGradeColumns"
              :rows="
                tableRows(
                  oLevelGrades as unknown as Record<string, unknown>[],
                  oLevelGradeTableState,
                )
              "
              :total="
                tableTotal(
                  oLevelGrades as unknown as Record<string, unknown>[],
                  oLevelGradeTableState,
                )
              "
              :state="oLevelGradeTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-trash-2',
                  tone: 'error',
                },
              ]"
              @update:state="oLevelGradeTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: (row) => editQualificationGrade(row, 'O_LEVEL'),
                  delete: (row) => deleteQualificationGrade(row, 'O_LEVEL'),
                })
              "
            >
              <template #points-cell="{ value }">
                {{ value ?? "—" }}
              </template>
              <template #pass-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Pass' : 'Not a pass'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>

          <EmhareRegisterPanel
            v-if="activeReferenceDataset === 'a-level-grades'"
            title="A Level grades"
            description="ZIMSEC A Level grades, principal pass points, subsidiary result, and fail outcome used by qualification capture and points calculation."
            :record-count="aLevelGrades.length"
          >
            <template #actions>
              <UButton
                icon="i-lucide-plus"
                label="Create A Level grade"
                color="primary"
                @click="createQualificationGrade('A_LEVEL')"
              />
            </template>
            <EmhareDataTable
              :columns="qualificationGradeColumns"
              :rows="
                tableRows(
                  aLevelGrades as unknown as Record<string, unknown>[],
                  aLevelGradeTableState,
                )
              "
              :total="
                tableTotal(
                  aLevelGrades as unknown as Record<string, unknown>[],
                  aLevelGradeTableState,
                )
              "
              :state="aLevelGradeTableState"
              :loading="loading"
              :row-actions="[
                { id: 'edit', label: 'Edit', icon: 'i-lucide-pencil' },
                {
                  id: 'delete',
                  label: 'Delete',
                  icon: 'i-lucide-trash-2',
                  tone: 'error',
                },
              ]"
              @update:state="aLevelGradeTableState = $event"
              @row-action="
                rowAction($event, {
                  edit: (row) => editQualificationGrade(row, 'A_LEVEL'),
                  delete: (row) => deleteQualificationGrade(row, 'A_LEVEL'),
                })
              "
            >
              <template #points-cell="{ value }">
                {{ value ?? "—" }}
              </template>
              <template #pass-cell="{ value }">
                <EmhareStatusPill
                  :label="value ? 'Principal pass' : 'Not a principal pass'"
                  :tone="value ? 'success' : 'neutral'"
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <section v-if="activeTab === 'workflow'" class="space-y-4">
          <UAlert
            color="primary"
            variant="soft"
            icon="i-lucide-shield-check"
            title="Governed operational queue"
            description="Tasks are assigned by user or role, constrained to institution or academic-unit scope, claimed by one operator, and completed with immutable decision evidence."
          />
          <div class="grid gap-3 sm:grid-cols-3">
            <EmhareKpiCard
              label="Open"
              :value="workflowTasks.filter((task) => task.status === 'OPEN').length"
              icon="i-lucide-inbox"
            />
            <EmhareKpiCard
              label="Claimed"
              :value="workflowTasks.filter((task) => task.status === 'CLAIMED').length"
              icon="i-lucide-user-check"
              tone="warning"
            />
            <EmhareKpiCard
              label="Completed"
              :value="workflowTasks.filter((task) => task.status === 'COMPLETED').length"
              icon="i-lucide-circle-check"
              tone="success"
            />
          </div>
          <EmhareRegisterPanel
            title="Workflow tasks"
            description="One register for pending, claimed, and completed cross-service work. Open a row to claim it or record the decision."
            :record-count="workflowTasks.length"
          >
            <EmhareDataTable
              :columns="workflowColumns"
              :rows="
                tableRows(workflowRows as unknown as Record<string, unknown>[], workflowTableState)
              "
              :total="
                tableTotal(workflowRows as unknown as Record<string, unknown>[], workflowTableState)
              "
              :state="workflowTableState"
              :loading="loading"
              :row-actions="[
                {
                  id: 'open',
                  label: 'Open task',
                  icon: 'i-lucide-panel-right-open',
                },
              ]"
              @update:state="workflowTableState = $event"
              @row-action="rowAction($event, { open: openWorkflowTask })"
            >
              <template #taskReference-cell="{ value, row }">
                <div>
                  <p class="font-mono text-xs font-medium">{{ value }}</p>
                  <p class="text-xs text-muted">{{ row.workflowCode }}</p>
                </div>
              </template>
              <template #dueAt-cell="{ value }">
                <span
                  :class="
                    value && new Date(String(value)).getTime() < Date.now()
                      ? 'font-medium text-error'
                      : ''
                  "
                >
                  {{ value ? new Date(String(value)).toLocaleString() : "No fixed due date" }}
                </span>
              </template>
              <template #status-cell="{ value }">
                <EmhareStatusPill
                  :label="String(value)"
                  :tone="
                    value === 'COMPLETED'
                      ? 'success'
                      : value === 'CLAIMED'
                        ? 'warning'
                        : value === 'CANCELLED'
                          ? 'error'
                          : 'primary'
                  "
                />
              </template>
            </EmhareDataTable>
          </EmhareRegisterPanel>
        </section>

        <section v-if="activeTab === 'audit'">
          <div class="space-y-5">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <EmhareKpiCard
                label="Users"
                :value="operationalReport.inventory.userCount"
                icon="i-lucide-users"
              />
              <EmhareKpiCard
                label="Roles"
                :value="operationalReport.inventory.roleCount"
                icon="i-lucide-shield-check"
              />
              <EmhareKpiCard
                label="Audit activity · 24h"
                :value="operationalReport.auditEventsLast24Hours"
                icon="i-lucide-clipboard-check"
                tone="success"
              />
              <EmhareKpiCard
                label="Login sessions · 24h"
                :value="operationalReport.loginSessionsLast24Hours"
                icon="i-lucide-log-in"
                tone="info"
              />
            </div>

            <EmhareRegisterPanel
              title="Core activity"
              description="Who changed what, when, with before-and-after evidence retained for review."
              :record-count="auditEvents.length"
            >
              <EmhareDataTable
                :columns="auditColumns"
                :rows="
                  tableRows(auditEvents as unknown as Record<string, unknown>[], auditTableState)
                "
                :total="
                  tableTotal(auditEvents as unknown as Record<string, unknown>[], auditTableState)
                "
                :state="auditTableState"
                :loading="loading"
                @update:state="auditTableState = $event"
              >
                <template #occurredAt-cell="{ value }">
                  <span>{{ new Date(String(value)).toLocaleString() }}</span>
                </template>
                <template #eventType-cell="{ value }">
                  <span class="font-mono text-xs">{{ String(value) }}</span>
                </template>
                <template #actorUserId-cell="{ value }">
                  <span class="font-mono text-xs">{{ value || "System" }}</span>
                </template>
              </EmhareDataTable>
            </EmhareRegisterPanel>

            <EmhareRegisterPanel
              title="Login sessions"
              description="One entry per authenticated identity session for security review and support."
              :record-count="loginEvents.length"
            >
              <EmhareDataTable
                :columns="loginColumns"
                :rows="
                  tableRows(loginEvents as unknown as Record<string, unknown>[], loginTableState)
                "
                :total="
                  tableTotal(loginEvents as unknown as Record<string, unknown>[], loginTableState)
                "
                :state="loginTableState"
                :loading="loading"
                @update:state="loginTableState = $event"
              >
                <template #occurredAt-cell="{ value }">
                  <span>{{ new Date(String(value)).toLocaleString() }}</span>
                </template>
                <template #outcome-cell="{ value }">
                  <EmhareStatusPill :label="String(value)" tone="success" />
                </template>
              </EmhareDataTable>
            </EmhareRegisterPanel>
          </div>
        </section>

        <EmhareRecordDrawer
          v-model:open="drawerOpen"
          :presentation="drawerKind === 'profile' || drawerKind === 'user' ? 'page' : 'sidepanel'"
          :title="drawerTitle"
          :description="drawerDescription"
          :submit-label="
            drawerKind === 'user' && !userForm.id
              ? userProvisioningSubmitLabel
              : drawerKind === 'grant'
                ? 'Grant permission'
                : drawerKind === 'assignment'
                  ? 'Assign role'
                  : drawerKind === 'workflow-task'
                    ? selectedWorkflowTask?.status === 'OPEN'
                      ? 'Claim task'
                      : 'Record decision'
                    : 'Save record'
          "
          :submit-icon="
            drawerKind === 'user' && !userForm.id
              ? userProvisioningStep === 'review'
                ? 'i-lucide-user-check'
                : 'i-lucide-arrow-right'
              : drawerKind === 'workflow-task'
                ? selectedWorkflowTask?.status === 'OPEN'
                  ? 'i-lucide-hand'
                  : 'i-lucide-gavel'
                : drawerKind === 'grant' || drawerKind === 'assignment'
                  ? 'i-lucide-plus'
                  : 'i-lucide-save'
          "
          :busy="drawerSaving"
          :submit-disabled="drawerSubmitDisabled"
          :show-back="drawerKind === 'user' && !userForm.id && userProvisioningStep !== 'identity'"
          :width="drawerKind === 'profile' || drawerKind === 'user' ? 'xl' : 'lg'"
          @submit="submitDrawer"
          @back="returnToPreviousUserProvisioningStep"
          @close="handleDrawerClosed"
        >
          <div v-if="drawerKind === 'profile'" class="space-y-8 pb-2">
            <EmhareFormSection
              title="Institution identity"
              description="Names and codes used across operations and official records."
              icon="i-lucide-landmark"
            >
              <EmhareFormField
                v-model="profileForm.code"
                name="code"
                label="Institution code"
                required
              />
              <EmhareFormField
                v-model="profileForm.legacyCode"
                name="legacyCode"
                label="Legacy code"
                description="Retained for migration and reconciliation."
              />
              <EmhareFormField
                v-model="profileForm.name"
                name="name"
                label="Operating name"
                class="md:col-span-2"
                required
              />
              <EmhareFormField
                v-model="profileForm.legalName"
                name="legalName"
                label="Legal name"
                class="md:col-span-2"
                required
              />
              <EmhareFormField
                v-model="profileForm.registrarName"
                name="registrarName"
                label="Registrar name"
                description="Printed above the Registrar title on official admission letters."
                class="md:col-span-2"
                required
              />
            </EmhareFormSection>

            <USeparator />

            <EmhareFormSection
              title="Location and finance"
              description="Regional defaults used by operational services."
              icon="i-lucide-map-pinned"
            >
              <EmhareFormField
                v-model="profileForm.countryCode"
                type="searchable-select"
                name="countryCode"
                label="Country"
                :items="countryCodeOptions"
                required
              />
              <EmhareFormField
                v-model="profileForm.timezone"
                type="searchable-select"
                name="timezone"
                label="Timezone"
                :items="timezoneOptions"
                required
              />
              <EmhareFormField
                v-model="profileForm.defaultCurrencyCode"
                type="select"
                name="defaultCurrencyCode"
                label="Transaction base currency"
                :items="baseCurrencyOptions"
                class="md:col-span-2"
                required
                disabled
                description="USD is the governed ledger base currency. ZWG transactions require an effective exchange rate."
              />
            </EmhareFormSection>

            <USeparator />

            <EmhareFormSection
              title="Bank details"
              description="Maintain every institution-owned payment account printed on official documents. At least one USD/Nostro and one ZWG account are required."
              icon="i-lucide-landmark"
            >
              <div class="space-y-3 md:col-span-2">
                <UAlert
                  color="primary"
                  variant="soft"
                  icon="i-lucide-badge-dollar-sign"
                  title="Currency-specific payment accounts"
                  description="USD accounts are labelled as Nostro accounts on offer letters. ZWG transactions still require the effective Finance exchange rate when applicable."
                />
                <div
                  v-for="(bankAccount, index) in profileBankAccounts"
                  :key="`${bankAccount.currencyCode}-${index}`"
                  class="rounded-lg border border-muted bg-elevated/40 p-4"
                >
                  <div class="mb-4 flex items-center justify-between gap-3">
                    <div class="flex items-center gap-2">
                      <UBadge color="primary" variant="soft">
                        {{ bankAccount.currencyCode }} account
                      </UBadge>
                      <span class="text-sm font-medium text-default">
                        {{ bankAccount.bankName || `Payment account ${index + 1}` }}
                      </span>
                    </div>
                    <UButton
                      label="Remove"
                      icon="i-lucide-trash-2"
                      color="error"
                      variant="ghost"
                      size="sm"
                      @click="removeProfileBankAccount(index)"
                    />
                  </div>
                  <div class="grid gap-4 md:grid-cols-2">
                    <EmhareFormField
                      v-model="bankAccount.currencyCode"
                      type="select"
                      :name="`bankAccounts.${index}.currencyCode`"
                      label="Account currency"
                      :items="paymentCurrencyOptions"
                      required
                    />
                    <EmhareFormField
                      v-model="bankAccount.bankName"
                      :name="`bankAccounts.${index}.bankName`"
                      label="Bank name"
                      placeholder="CBZ BANK"
                      required
                    />
                    <EmhareFormField
                      v-model="bankAccount.accountNumber"
                      :name="`bankAccounts.${index}.accountNumber`"
                      label="Account number"
                      required
                    />
                    <EmhareFormField
                      v-model="bankAccount.accountName"
                      :name="`bankAccounts.${index}.accountName`"
                      label="Account name"
                    />
                    <EmhareFormField
                      v-model="bankAccount.branchName"
                      :name="`bankAccounts.${index}.branchName`"
                      label="Branch"
                      placeholder="Kwame Nkrumah Avenue, Harare"
                    />
                    <EmhareFormField
                      v-model="bankAccount.branchSortCode"
                      :name="`bankAccounts.${index}.branchSortCode`"
                      label="Branch sort code"
                    />
                    <EmhareFormField
                      v-model="bankAccount.swiftCode"
                      :name="`bankAccounts.${index}.swiftCode`"
                      label="SWIFT code"
                    />
                    <EmhareFormField
                      v-model="bankAccount.paymentReferenceInstructions"
                      :name="`bankAccounts.${index}.paymentReferenceInstructions`"
                      label="Payment reference instructions"
                      description="Applicants receive a registration number when they accept the offer and must quote it when paying."
                    />
                  </div>
                </div>
                <UButton
                  label="Add bank account"
                  icon="i-lucide-plus"
                  color="primary"
                  variant="outline"
                  @click="addProfileBankAccount"
                />
              </div>
            </EmhareFormSection>

            <USeparator />

            <EmhareFormSection
              title="Public contact details"
              description="Contact information shown on institution-issued communication."
              icon="i-lucide-contact"
            >
              <EmhareFormField
                v-model="profileContactForm.email"
                type="email"
                name="contactEmail"
                label="Email address"
                placeholder="info@example.ac.zw"
              />
              <EmhareFormField
                v-model="profileContactForm.phone"
                type="phone"
                name="contactPhone"
                label="Phone number"
                placeholder="+263 ..."
              />
              <EmhareFormField
                v-model="profileContactForm.website"
                name="contactWebsite"
                label="Website"
                placeholder="https://www.example.ac.zw"
                class="md:col-span-2"
              />
            </EmhareFormSection>

            <USeparator />

            <EmhareFormSection
              title="Brand and official documents"
              description="Visual identity used by eMhare and generated records."
              icon="i-lucide-palette"
            >
              <div
                class="grid gap-5 rounded-xl border border-muted bg-elevated/40 p-4 md:col-span-2 sm:grid-cols-[8rem_minmax(0,1fr)]"
              >
                <div
                  class="flex aspect-square items-center justify-center overflow-hidden rounded-xl border border-muted bg-default p-3 shadow-sm"
                >
                  <img
                    v-if="activeProfileLogoUrl"
                    :src="activeProfileLogoUrl"
                    :alt="`${profileForm.name} logo preview`"
                    class="size-full object-contain"
                  />
                  <UIcon v-else name="i-lucide-image-plus" class="size-10 text-dimmed" />
                </div>
                <div class="min-w-0 space-y-3">
                  <div>
                    <p class="text-sm font-semibold text-highlighted">Institution logo</p>
                    <p class="mt-1 text-sm text-muted">
                      Use a transparent PNG or a JPEG under 2 MB. A square or horizontal mark with
                      clear padding works best.
                    </p>
                  </div>
                  <EmhareFormField
                    :model-value="profileLogoFile"
                    type="file"
                    name="institutionLogo"
                    label="Choose logo"
                    accept="image/png,image/jpeg"
                    @update:model-value="selectProfileLogo"
                  />
                  <div
                    v-if="storedProfileLogo || profileLogoFile"
                    class="flex flex-wrap items-center gap-2 text-xs text-muted"
                  >
                    <span class="truncate">
                      {{ profileLogoFile?.name || storedProfileLogo?.originalFileName }}
                    </span>
                    <span v-if="profileLogoLoading">Loading preview…</span>
                    <UButton
                      label="Remove"
                      icon="i-lucide-trash-2"
                      color="error"
                      variant="ghost"
                      size="xs"
                      @click="removeProfileLogo"
                    />
                  </div>
                </div>
              </div>
              <div
                class="grid gap-5 rounded-xl border border-muted bg-elevated/40 p-4 md:col-span-2 sm:grid-cols-[8rem_minmax(0,1fr)]"
              >
                <div
                  class="flex aspect-square items-center justify-center overflow-hidden rounded-xl border border-muted bg-default p-3 shadow-sm"
                >
                  <img
                    v-if="activeProfileRegistrarSignatureUrl"
                    :src="activeProfileRegistrarSignatureUrl"
                    :alt="`${profileForm.registrarName} signature preview`"
                    class="size-full object-contain"
                  />
                  <UIcon v-else name="i-lucide-signature" class="size-10 text-dimmed" />
                </div>
                <div class="min-w-0 space-y-3">
                  <div>
                    <p class="text-sm font-semibold text-highlighted">Registrar signature</p>
                    <p class="mt-1 text-sm text-muted">
                      Upload the transparent PNG or JPEG signature printed above the configured
                      Registrar name on new offer letters.
                    </p>
                  </div>
                  <EmhareFormField
                    :model-value="profileRegistrarSignatureFile"
                    type="file"
                    name="registrarSignature"
                    label="Choose registrar signature"
                    accept="image/png,image/jpeg"
                    @update:model-value="selectProfileRegistrarSignature"
                  />
                  <div
                    v-if="storedProfileRegistrarSignature || profileRegistrarSignatureFile"
                    class="flex flex-wrap items-center gap-2 text-xs text-muted"
                  >
                    <span class="truncate">
                      {{
                        profileRegistrarSignatureFile?.name ||
                        storedProfileRegistrarSignature?.originalFileName
                      }}
                    </span>
                    <span v-if="profileRegistrarSignatureLoading"> Loading preview… </span>
                    <UButton
                      label="Remove"
                      icon="i-lucide-trash-2"
                      color="error"
                      variant="ghost"
                      size="xs"
                      @click="removeProfileRegistrarSignature"
                    />
                  </div>
                </div>
              </div>
              <EmhareFormField
                v-model="profileBrandingForm.documentHeader"
                name="documentHeader"
                label="Official document header"
                class="md:col-span-2"
                placeholder="University of Zimbabwe"
              />
              <EmhareFormField
                v-model="profileBrandingForm.primaryColor"
                type="color"
                name="primaryColor"
                label="Primary colour"
              />
              <EmhareFormField
                v-model="profileBrandingForm.secondaryColor"
                type="color"
                name="secondaryColor"
                label="Accent colour"
              />
            </EmhareFormSection>
          </div>

          <div v-else-if="drawerKind === 'user'" class="space-y-6 pb-2">
            <template v-if="!userForm.id">
              <EmhareJourneyStepper
                :steps="userProvisioningSteps"
                :current-step="userProvisioningStep"
                label="User profile completion"
                @update:current-step="userProvisioningStep = $event"
              />

              <div v-if="userProvisioningStep === 'identity'" class="space-y-5">
                <UAlert
                  color="primary"
                  variant="soft"
                  icon="i-lucide-key-round"
                  title="Keycloak account included"
                  description="Completing this workflow creates or links the Keycloak identity and activates the local access profile together. New users receive a temporary password for first sign-in."
                />
                <EmhareFormSection
                  title="User identity"
                  description="Existing Keycloak users are linked by exact username and email. Otherwise, a new identity is created."
                  icon="i-lucide-user-round"
                >
                  <EmhareFormField
                    v-model="userForm.username"
                    name="username"
                    label="Username"
                    required
                  />
                  <EmhareFormField
                    v-model="userForm.email"
                    type="email"
                    name="email"
                    label="Email"
                    required
                  />
                  <EmhareFormField
                    v-model="userForm.displayName"
                    name="displayName"
                    label="Display name"
                    required
                  />
                  <EmhareFormField
                    v-model="userForm.phoneNumber"
                    type="phone"
                    name="phoneNumber"
                    label="Phone number"
                  />
                </EmhareFormSection>
              </div>

              <div v-else-if="userProvisioningStep === 'access'" class="space-y-4">
                <UAlert
                  color="primary"
                  variant="soft"
                  icon="i-lucide-shield-check"
                  title="Assign the user's working access"
                  description="At least one role with usable permissions is required. Academic-unit roles must also identify the unit they govern."
                />

                <div
                  v-for="(assignment, assignmentIndex) in userAccessDrafts"
                  :key="assignment.key"
                  class="rounded-xl border border-muted bg-elevated/40 p-4 sm:p-5"
                >
                  <div class="mb-4 flex items-start justify-between gap-3">
                    <div>
                      <p class="text-sm font-semibold text-highlighted">
                        Access assignment {{ assignmentIndex + 1 }}
                      </p>
                      <p class="mt-1 text-xs text-muted">
                        Choose the responsibility and its operating scope.
                      </p>
                    </div>
                    <UButton
                      icon="i-lucide-trash-2"
                      label="Remove"
                      color="neutral"
                      variant="ghost"
                      size="xs"
                      @click="removeUserAccessDraft(assignment.key)"
                    />
                  </div>

                  <div class="grid gap-4 sm:grid-cols-2">
                    <EmhareFormField
                      :model-value="assignment.roleId"
                      type="searchable-select"
                      :name="`role-${assignment.key}`"
                      label="Role"
                      placeholder="Select role"
                      :items="roleOptions"
                      required
                      @update:model-value="updateUserProvisioningRole(assignment, $event)"
                    />
                    <EmhareFormField
                      v-if="userProvisioningRoleNeedsScope(assignment)"
                      v-model="assignment.academicUnitId"
                      type="searchable-select"
                      :name="`academic-unit-${assignment.key}`"
                      label="Academic unit"
                      placeholder="Select academic unit"
                      :items="academicUnitOptions"
                      required
                    />
                    <div v-else class="rounded-lg border border-dashed border-muted px-4 py-3">
                      <p class="text-xs font-semibold uppercase tracking-wide text-muted">Scope</p>
                      <p class="mt-1 text-sm text-highlighted">Institution-wide</p>
                    </div>
                  </div>

                  <div v-if="assignment.roleId" class="mt-4 border-t border-muted pt-4">
                    <p class="text-xs font-semibold uppercase tracking-wide text-muted">
                      Access areas
                    </p>
                    <p
                      v-if="rolePermissionLoadingIds.has(assignment.roleId)"
                      class="mt-2 text-sm text-muted"
                    >
                      Loading role permissions…
                    </p>
                    <div
                      v-else-if="roleAccessAreas(assignment.roleId).length"
                      class="mt-2 flex flex-wrap gap-2"
                    >
                      <UBadge
                        v-for="area in roleAccessAreas(assignment.roleId)"
                        :key="area"
                        :label="area"
                        color="primary"
                        variant="soft"
                      />
                    </div>
                    <p v-else class="mt-2 text-sm text-warning">
                      This role has no usable access yet. Grant permissions to it before assignment.
                    </p>
                  </div>
                </div>

                <UButton
                  icon="i-lucide-plus"
                  label="Add another role"
                  color="primary"
                  variant="outline"
                  @click="addUserAccessDraft"
                />
              </div>

              <div v-else class="space-y-5">
                <UAlert
                  color="primary"
                  variant="soft"
                  icon="i-lucide-user-check"
                  title="Ready to activate"
                  description="The Keycloak identity and all local role assignments are completed together. A failure leaves neither a new Keycloak account nor a partial local profile."
                />

                <div class="rounded-xl border border-muted bg-elevated/40 p-5">
                  <p class="text-xs font-semibold uppercase tracking-wide text-muted">Identity</p>
                  <p class="mt-2 text-base font-semibold text-highlighted">
                    {{ userForm.displayName }}
                  </p>
                  <p class="mt-1 text-sm text-muted">
                    {{ userForm.username }} · {{ userForm.email }}
                  </p>
                  <p v-if="userForm.phoneNumber" class="mt-1 text-sm text-muted">
                    {{ userForm.phoneNumber }}
                  </p>
                </div>

                <div class="space-y-3">
                  <p class="text-xs font-semibold uppercase tracking-wide text-muted">
                    Authorised access
                  </p>
                  <div
                    v-for="assignment in userAccessDrafts"
                    :key="assignment.key"
                    class="rounded-xl border border-muted px-4 py-4"
                  >
                    <div class="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p class="text-sm font-semibold text-highlighted">
                          {{ userProvisioningRole(assignment)?.name }}
                        </p>
                        <p class="mt-1 text-xs text-muted">
                          {{ academicUnitLabel(assignment.academicUnitId) }}
                        </p>
                      </div>
                      <UBadge label="Active on completion" color="primary" variant="soft" />
                    </div>
                    <div class="mt-3 flex flex-wrap gap-2">
                      <UBadge
                        v-for="area in roleAccessAreas(assignment.roleId)"
                        :key="area"
                        :label="area"
                        color="neutral"
                        variant="soft"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </template>

            <div v-else class="space-y-8">
              <EmhareFormSection
                title="User identity"
                description="Update the local profile while Keycloak continues to govern sign-in credentials."
                icon="i-lucide-user-round"
              >
                <EmhareFormField
                  v-model="userForm.username"
                  name="username"
                  label="Username"
                  required
                  readonly
                />
                <EmhareFormField
                  v-model="userForm.email"
                  type="email"
                  name="email"
                  label="Email"
                  required
                  readonly
                />
                <EmhareFormField
                  v-model="userForm.displayName"
                  name="displayName"
                  label="Display name"
                  required
                />
                <EmhareFormField
                  v-model="userForm.phoneNumber"
                  type="phone"
                  name="phoneNumber"
                  label="Phone number"
                />
                <EmhareFormField
                  v-model="userForm.status"
                  type="select"
                  name="status"
                  label="Status"
                  :items="userStatuses"
                  required
                />
              </EmhareFormSection>

              <EmhareFormSection
                title="Role and academic-unit assignments"
                description="Academic-unit roles operate only within the selected unit. System roles remain institution-wide."
                icon="i-lucide-building-2"
                class="grid-cols-1"
              >
                <UAlert
                  color="primary"
                  variant="soft"
                  icon="i-lucide-shield-check"
                  title="Keep access scope explicit"
                  description="Choose an academic unit for every academic-unit role. Existing role identities are locked; remove an assignment and add another when the responsibility itself changes."
                />

                <div
                  v-for="(assignment, assignmentIndex) in userAccessDrafts"
                  :key="assignment.key"
                  class="rounded-xl border border-muted bg-elevated/40 p-4 sm:p-5"
                >
                  <div class="mb-4 flex items-start justify-between gap-3">
                    <div>
                      <p class="text-sm font-semibold text-highlighted">
                        Role assignment {{ assignmentIndex + 1 }}
                      </p>
                      <p class="mt-1 text-xs text-muted">
                        {{
                          assignment.assignmentId ? "Existing active assignment" : "New assignment"
                        }}
                      </p>
                    </div>
                    <UButton
                      icon="i-lucide-trash-2"
                      label="Remove"
                      color="neutral"
                      variant="ghost"
                      size="xs"
                      @click="removeUserAccessDraft(assignment.key)"
                    />
                  </div>

                  <div class="grid gap-4 sm:grid-cols-2">
                    <EmhareFormField
                      :model-value="assignment.roleId"
                      type="searchable-select"
                      :name="`edit-role-${assignment.key}`"
                      label="Role"
                      placeholder="Select role"
                      :items="roleOptions"
                      :disabled="Boolean(assignment.assignmentId)"
                      required
                      @update:model-value="updateUserProvisioningRole(assignment, $event)"
                    />
                    <EmhareFormField
                      v-if="userProvisioningRoleNeedsScope(assignment)"
                      v-model="assignment.academicUnitId"
                      type="searchable-select"
                      :name="`edit-academic-unit-${assignment.key}`"
                      label="Academic unit"
                      placeholder="Search by unit code or name"
                      :items="academicUnitOptions"
                      required
                    />
                    <div v-else class="rounded-lg border border-dashed border-muted px-4 py-3">
                      <p class="text-xs font-semibold uppercase tracking-wide text-muted">Scope</p>
                      <p class="mt-1 text-sm text-highlighted">Institution-wide</p>
                    </div>
                  </div>
                </div>

                <UButton
                  icon="i-lucide-plus"
                  label="Add another role"
                  color="primary"
                  variant="outline"
                  class="w-fit"
                  @click="addUserAccessDraft"
                />
              </EmhareFormSection>
            </div>
          </div>

          <div v-else-if="drawerKind === 'role'" class="space-y-4">
            <EmhareFormField
              v-model="roleForm.code"
              name="roleCode"
              label="Code"
              required
              :readonly="Boolean(roleForm.id)"
            />
            <EmhareFormField v-model="roleForm.name" name="roleName" label="Name" required />
            <EmhareFormField
              v-model="roleForm.scope"
              type="select"
              name="scope"
              label="Scope"
              :items="roleScopes"
            />
            <EmhareFormField
              v-model="roleForm.systemManaged"
              type="toggle"
              name="systemManaged"
              label="System managed"
            />
          </div>

          <div v-else-if="drawerKind === 'permission'" class="space-y-4">
            <EmhareFormField
              v-model="permissionForm.code"
              name="permissionCode"
              label="Code"
              required
              :readonly="Boolean(permissionForm.id)"
            />
            <EmhareFormField
              v-model="permissionForm.name"
              name="permissionName"
              label="Name"
              required
            />
            <EmhareFormField
              v-model="permissionForm.category"
              type="select"
              name="category"
              label="Category"
              :items="permissionCategories"
            />
            <EmhareFormField
              v-model="permissionForm.description"
              type="textarea"
              name="description"
              label="Description"
            />
          </div>

          <div v-else-if="drawerKind === 'grant'" class="space-y-4">
            <EmhareFormField
              v-model="selectedRoleId"
              type="searchable-select"
              name="drawerRole"
              label="Role"
              :items="roleOptions"
              required
            />
            <EmhareFormField
              v-model="grantForm.permissionId"
              type="searchable-select"
              name="permissionId"
              label="Permission"
              :items="permissionOptions"
              required
            />
          </div>

          <div v-else-if="drawerKind === 'assignment'" class="space-y-4">
            <EmhareFormField
              v-model="selectedUserId"
              type="searchable-select"
              name="drawerUser"
              label="User"
              :items="userOptions"
              required
            />
            <EmhareFormField
              v-model="assignmentForm.roleId"
              type="searchable-select"
              name="assignmentRole"
              label="Role"
              :items="roleOptions"
              required
            />
            <EmhareFormField
              v-model="assignmentForm.academicUnitId"
              type="searchable-select"
              name="academicUnitId"
              label="Academic unit"
              :items="academicUnitOptions"
              :required="assignmentRequiresAcademicUnit"
              :disabled="selectedAssignmentRole?.scope === 'SYSTEM'"
              :description="
                selectedAssignmentRole?.scope === 'SYSTEM'
                  ? 'This system role applies institution-wide.'
                  : 'Select the governed academic unit this role may operate within.'
              "
              placeholder="Search by unit code or name"
            />
            <EmhareFormField
              v-model="assignmentForm.startsAt"
              type="date"
              name="startsAt"
              label="Starts at"
            />
          </div>

          <div v-else-if="drawerKind === 'country'" class="grid gap-4 sm:grid-cols-2">
            <EmhareFormField v-model="countryForm.iso2Code" name="iso2Code" label="ISO2" required />
            <EmhareFormField v-model="countryForm.iso3Code" name="iso3Code" label="ISO3" required />
            <EmhareFormField v-model="countryForm.name" name="countryName" label="Name" required />
            <EmhareFormField
              v-model="countryForm.nationalityName"
              name="nationalityName"
              label="Nationality"
              required
            />
          </div>

          <div v-else-if="drawerKind === 'lookup-set'" class="space-y-4">
            <EmhareFormField
              v-model="lookupSetForm.code"
              name="lookupSetCode"
              label="Code"
              required
            />
            <EmhareFormField
              v-model="lookupSetForm.name"
              name="lookupSetName"
              label="Name"
              required
            />
            <EmhareFormField
              v-model="lookupSetForm.description"
              type="textarea"
              name="lookupSetDescription"
              label="Description"
            />
          </div>

          <div v-else-if="drawerKind === 'lookup-value'" class="space-y-4">
            <EmhareFormField
              v-model="selectedLookupSetId"
              type="searchable-select"
              name="drawerLookupSet"
              label="Lookup set"
              :items="managedLookupSetOptions"
              required
            />
            <EmhareFormField
              v-model="lookupValueForm.code"
              name="lookupValueCode"
              label="Code"
              required
            />
            <EmhareFormField
              v-model="lookupValueForm.name"
              name="lookupValueName"
              label="Name"
              required
            />
            <EmhareFormField
              v-model="lookupValueForm.sortOrder"
              type="number"
              name="sortOrder"
              label="Sort order"
            />
            <EmhareFormField
              v-model="lookupValueForm.active"
              type="toggle"
              name="active"
              label="Active"
            />
          </div>

          <div v-else-if="drawerKind === 'qualification-subject'" class="space-y-4">
            <UAlert
              color="primary"
              variant="soft"
              icon="i-lucide-book-open-check"
              :title="
                qualificationSubjectForm.level === 'A_LEVEL' ? 'A Level subject' : 'O Level subject'
              "
              description="Changes apply to new applicant qualification capture. Existing result snapshots remain unchanged."
            />
            <div class="grid gap-4 sm:grid-cols-2">
              <EmhareFormField
                v-model="qualificationSubjectForm.code"
                name="qualificationSubjectCode"
                label="Subject code"
                required
              />
              <EmhareFormField
                v-model="qualificationSubjectForm.subjectGroupCode"
                type="select"
                name="qualificationSubjectGroup"
                label="Subject group"
                :items="qualificationSubjectGroups"
                required
              />
            </div>
            <EmhareFormField
              v-model="qualificationSubjectForm.name"
              name="qualificationSubjectName"
              label="Subject name"
              required
            />
            <div class="grid gap-3 sm:grid-cols-3">
              <EmhareFormField
                v-model="qualificationSubjectForm.scienceSubject"
                type="toggle"
                name="qualificationScienceSubject"
                label="Science"
                description="Can independently satisfy a Science pass requirement."
              />
              <EmhareFormField
                v-model="qualificationSubjectForm.mathematicsSubject"
                type="toggle"
                name="qualificationMathematicsSubject"
                label="Mathematics"
                description="Can independently satisfy a Mathematics pass requirement."
              />
              <EmhareFormField
                v-model="qualificationSubjectForm.englishSubject"
                type="toggle"
                name="qualificationEnglishSubject"
                label="English"
                description="Can independently satisfy an English pass requirement."
              />
            </div>
            <EmhareFormField
              v-model="qualificationSubjectForm.active"
              type="toggle"
              name="qualificationSubjectActive"
              label="Available for capture"
              description="Inactive subjects remain in the register but cannot be selected for new applicant results."
            />
          </div>

          <div v-else-if="drawerKind === 'qualification-grade'" class="space-y-4">
            <UAlert
              color="primary"
              variant="soft"
              icon="i-lucide-graduation-cap"
              :title="
                qualificationGradeForm.level === 'A_LEVEL' ? 'A Level grade' : 'O Level grade'
              "
              description="Grade outcomes feed qualification validation. A Level points also feed applicant points calculation."
            />
            <div class="grid gap-4 sm:grid-cols-2">
              <EmhareFormField
                v-model="qualificationGradeForm.grade"
                name="qualificationGrade"
                label="Grade"
                required
              />
              <EmhareFormField
                v-model="qualificationGradeForm.sortOrder"
                type="number"
                name="qualificationGradeSortOrder"
                label="Display order"
                required
              />
            </div>
            <EmhareFormField
              v-model="qualificationGradeForm.points"
              type="number"
              name="qualificationGradePoints"
              label="Points"
              description="Leave blank when the grade carries no points."
            />
            <EmhareFormField
              v-model="qualificationGradeForm.pass"
              type="toggle"
              name="qualificationGradePass"
              :label="qualificationGradeForm.level === 'A_LEVEL' ? 'Principal pass' : 'Pass'"
            />
          </div>

          <div v-else-if="drawerKind === 'workflow-task' && selectedWorkflowTask" class="space-y-4">
            <UAlert
              :color="
                selectedWorkflowTask.status === 'COMPLETED'
                  ? 'success'
                  : selectedWorkflowTask.status === 'CLAIMED'
                    ? 'warning'
                    : 'primary'
              "
              variant="soft"
              :icon="
                selectedWorkflowTask.status === 'COMPLETED'
                  ? 'i-lucide-circle-check'
                  : 'i-lucide-list-todo'
              "
              :title="selectedWorkflowTask.title"
              :description="selectedWorkflowTask.description"
            />
            <EmhareDescriptionList
              :items="[
                {
                  label: 'Subject',
                  value: `${selectedWorkflowTask.subjectReference} · ${selectedWorkflowTask.subjectType}`,
                },
                {
                  label: 'Assigned to',
                  value:
                    selectedWorkflowTask.assigneeType === 'USER'
                      ? selectedWorkflowTask.assignedUserName
                      : selectedWorkflowTask.assignedRoleName,
                },
                {
                  label: 'Scope',
                  value:
                    selectedWorkflowTask.scopeType === 'INSTITUTION'
                      ? 'Institution-wide'
                      : (academicSetup.overview.value?.academicUnits.find(
                          (unit) => unit.id === selectedWorkflowTask?.academicUnitId,
                        )?.name ?? 'Academic unit'),
                },
                {
                  label: 'Due',
                  value: selectedWorkflowTask.dueAt
                    ? new Date(selectedWorkflowTask.dueAt).toLocaleString()
                    : 'No fixed due date',
                },
                { label: 'Status', value: selectedWorkflowTask.status },
                {
                  label: 'Claimed by',
                  value: selectedWorkflowTask.claimedByUserName ?? 'Not claimed',
                },
              ]"
            />
            <template v-if="selectedWorkflowTask.status === 'CLAIMED'">
              <EmhareFormField
                v-model="workflowDecisionForm.decisionCode"
                type="select"
                name="workflowDecision"
                label="Decision"
                :items="[
                  { label: 'Approved', value: 'APPROVED' },
                  { label: 'Rejected', value: 'REJECTED' },
                  { label: 'Returned for correction', value: 'RETURNED' },
                  { label: 'Completed', value: 'COMPLETED' },
                ]"
                required
              />
              <EmhareFormField
                v-model="workflowDecisionForm.comment"
                type="textarea"
                name="workflowComment"
                label="Decision evidence"
                description="Record the factual basis, checks performed, and any follow-up required. This becomes immutable workflow evidence."
                required
              />
            </template>
            <EmharePaginatedCollection
              v-if="selectedWorkflowTask.decisions.length"
              :items="selectedWorkflowTask.decisions"
              :initial-page-size="5"
              v-slot="{ items: paginatedWorkflowDecisions }"
            >
              <div class="space-y-2">
                <h3 class="text-sm font-semibold">Decision history</h3>
                <div
                  v-for="decision in paginatedWorkflowDecisions"
                  :key="decision.id"
                  class="rounded-md border border-muted p-3"
                >
                  <div class="flex items-center justify-between gap-2">
                    <EmhareStatusPill :label="decision.decisionCode" tone="success" />
                    <span class="text-xs text-muted">{{
                      new Date(decision.decidedAt).toLocaleString()
                    }}</span>
                  </div>
                  <p class="mt-2 text-sm">{{ decision.comment }}</p>
                  <p class="mt-1 text-xs text-muted">{{ decision.actorName }}</p>
                </div>
              </div>
            </EmharePaginatedCollection>
          </div>
        </EmhareRecordDrawer>
      </div>
    </template>
  </UDashboardPanel>
</template>
