// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, defineComponent, onMounted, reactive, ref, watch } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import StudentRegistrations from "../../pages/operations/student-registrations.vue";
import { useProgrammeStudyPeriod } from "../../../../packages/portal-shell/composables/useProgrammeStudyPeriod";
import type {
  RegistrationStatus,
  RegistrationSummary,
  StudentConversionSummary,
} from "../../../../packages/portal-shell/types/student-records";

const { fire } = vi.hoisted(() => ({ fire: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire } }));
const request = vi.fn();
const showError = vi.fn();
const notify = vi.fn();
const selectedAcademicPeriodId = ref<string | null>("current-period");
let wrapper: VueWrapper;
let registrationRecords: RegistrationSummary[];

function registration(
  id: string,
  status: RegistrationStatus = "DRAFT",
  academicPeriodId = "current-period",
): RegistrationSummary {
  return {
    id,
    registrationNumber: `REG-${id}`,
    studentId: `student-${id}`,
    studentNumber: `R-${id}`,
    studentName: `Student ${id}`,
    programmeEnrolmentId: "enrolment",
    programmeCode: "BSC",
    programmeName: "Science",
    academicPeriodId,
    academicPeriodCode: "2026-S1",
    academicPeriodName: "Semester 1",
    academicPeriodStartsOn: "2026-08-01",
    academicPeriodEndsOn: "2026-12-31",
    programmePeriodNumber: 1,
    registrationType: "NORMAL",
    status,
    statusReason: "Evidence reviewed",
    initiatedAt: "2026-08-30T08:00:00Z",
    submittedAt: null,
    academicApprovedAt: null,
    confirmedAt: null,
    version: 4,
    totalCredits: 12,
    modules: [
      {
        id: "module-registration",
        curriculumModuleId: "required",
        moduleId: "module",
        moduleCode: "CSC101",
        moduleName: "Computing",
        curriculumModuleType: "COMPULSORY",
        creditValue: 12,
        minimumMarkRequired: 50,
        selectionSource: "AUTO_COMPULSORY",
      },
    ],
  };
}

function conversion(
  id: string,
  overrides: Partial<StudentConversionSummary> = {},
): StudentConversionSummary {
  return {
    id,
    status: "COMPLETED",
    financeProvisioningStatus: "COMPLETED",
    portalProvisioningStatus: "COMPLETED",
    sourceApplicationId: "application",
    sourceOfferId: "offer",
    studentId: "student",
    studentNumber: `R-${id}`,
    studentStatus: "ACTIVE",
    programmeEnrolmentId: "enrolment",
    programmeId: "programme",
    programmeVersionId: "approved-version",
    programmeCode: "BSC",
    programmeName: "Science",
    programmeEnrolmentStatus: "ACTIVE",
    requestedAt: "2026-08-01",
    completedAt: "2026-08-01",
    failureReason: null,
    retryCount: 0,
    lastRetryAt: null,
    lastRetryByUserId: null,
    lastRetryReason: null,
    ...overrides,
  };
}

const catalogue = {
  programmeCode: "BSC",
  academicPeriodCode: "2026-S1",
  programmeVersionCode: "V1",
  periodNumber: 1,
  modules: [
    {
      curriculumModuleId: "required",
      moduleCode: "CSC101",
      moduleName: "Computing",
      moduleType: "COMPULSORY",
      creditValue: 12,
    },
    {
      curriculumModuleId: "elective",
      moduleCode: "STA101",
      moduleName: "Statistics",
      moduleType: "ELECTIVE",
      creditValue: 12,
    },
  ],
};
const SlotContainer = defineComponent({
  template:
    '<section><slot name="header" /><slot name="body" /><slot name="right" /><slot /><slot name="footer" /></section>',
});
const Button = defineComponent({
  props: ["label", "loading"],
  template: '<button type="button" :aria-busy="loading">{{ label }}</button>',
});
const Select = defineComponent({
  props: ["modelValue", "items"],
  emits: ["update:modelValue"],
  template:
    '<select :value="modelValue" @change="$emit(\'update:modelValue\', items.find(item => String(item.value) === $event.target.value)?.value)"><option value="">Choose</option><option v-for="item in items" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
});
const Alert = defineComponent({
  props: ["title", "description"],
  template: "<aside>{{ title }} {{ description }}</aside>",
});

async function render() {
  wrapper = mount(StudentRegistrations, {
    global: {
      stubs: {
        UDashboardPanel: SlotContainer,
        UDashboardNavbar: SlotContainer,
        UDashboardSidebarCollapse: true,
        UCard: defineComponent({ template: "<article><slot /></article>" }),
        UButton: Button,
        EmhareGuidedActionButton: Button,
        USelect: Select,
        UAlert: Alert,
        UEmpty: Alert,
        UBadge: SlotContainer,
        USkeleton: true,
        EmhareStatusPill: defineComponent({
          props: ["label", "tone"],
          template: '<span class="status" :data-tone="tone">{{ label }}</span>',
        }),
        UFormField: defineComponent({
          props: ["label"],
          template: '<label class="field">{{ label }}<slot /></label>',
        }),
        EmhareRecordDrawer: defineComponent({
          props: ["open"],
          template:
            '<section v-if="open" role="dialog"><slot name="body" /><slot name="footer" /></section>',
        }),
        EmharePaginatedCollection: defineComponent({
          props: ["items"],
          template: '<section class="queue"><slot :items="items" /></section>',
        }),
        UCheckbox: defineComponent({
          props: ["modelValue"],
          emits: ["update:modelValue"],
          template:
            '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
        }),
      },
    },
  });
  await flushPromises();
}

async function click(label: string) {
  await wrapper
    .findAll("button")
    .find((button) => button.text() === label)!
    .trigger("click");
  await flushPromises();
}

async function select(label: string, value: string) {
  await wrapper
    .findAll(".field")
    .find((field) => field.text().startsWith(label))!
    .get("select")
    .setValue(value);
  await flushPromises();
}

async function prepareDraft() {
  await click("Start registration");
  await select("Active student enrolment", "eligible");
  await click("Load approved curriculum");
}

beforeEach(() => {
  vi.resetAllMocks();
  selectedAcademicPeriodId.value = "current-period";
  registrationRecords = [
    registration("draft"),
    registration("academic", "SUBMITTED"),
    registration("registry", "ACADEMIC_APPROVED"),
    registration("confirmed", "CONFIRMED"),
    registration("other", "CONFIRMED", "other-period"),
  ];
  for (const [name, value] of Object.entries({
    computed,
    onMounted,
    reactive,
    ref,
    watch,
    useProgrammeStudyPeriod,
  }))
    vi.stubGlobal(name, value);
  vi.stubGlobal("definePageMeta", vi.fn());
  vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage: (error: Error) => error.message }));
  vi.stubGlobal("useToast", () => ({ add: notify }));
  vi.stubGlobal("useEmhareConfirm", () => ({ showError }));
  vi.stubGlobal("useAcademicPeriodContext", () => ({
    selectedAcademicPeriodId,
    matchesAcademicPeriod: (item: { id?: string; academicPeriodId?: string }) =>
      !selectedAcademicPeriodId.value ||
      (item.academicPeriodId ?? item.id) === selectedAcademicPeriodId.value,
  }));
  request.mockImplementation(async (url: string, options?: { method?: string }) => {
    if (options?.method === "POST") return registration("created");
    if (url === "/api/student-records/registrations") return registrationRecords;
    if (url === "/api/student-records/conversions")
      return [
        conversion("eligible"),
        conversion("pending", { status: "PROVISIONING" }),
        conversion("suspended", { studentStatus: "SUSPENDED" }),
        conversion("withdrawn", { programmeEnrolmentStatus: "WITHDRAWN" }),
      ];
    if (url === "/api/academic/overview")
      return {
        programmes: [{ id: "programme", maximumDurationPeriods: 8 }],
        academicPeriods: [
          { id: "current-period", code: "2026-S1", name: "Semester 1", status: "OPEN" },
          { id: "other-period", code: "2027-S1", name: "Other semester", status: "OPEN" },
          { id: "closed", code: "2025-S1", name: "Closed semester", status: "CLOSED" },
        ],
      };
    if (url.startsWith("/api/academic/registration-catalogue?")) return catalogue;
    throw new Error(`Unexpected request: ${url}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});

describe("student registration workspace controls", () => {
  it("keeps summary and queue scoped to the selected academic period and reloads when it changes", async () => {
    await render();
    expect(wrapper.get('[aria-label="Registration queue summary"]').text()).toContain("Total4");
    expect(wrapper.get(".queue").text()).not.toContain("R-other");
    expect(wrapper.findAll(".status").map((item) => item.attributes("data-tone"))).toEqual([
      "neutral",
      "info",
      "warning",
      "success",
    ]);
    selectedAcademicPeriodId.value = "other-period";
    await flushPromises();
    expect(wrapper.get('[aria-label="Registration queue summary"]').text()).toContain("Total1");
    expect(wrapper.get(".queue").text()).toContain("R-other");
    expect(wrapper.get(".queue").text()).not.toContain("R-draft");
  });

  it("filters status without changing overall counts and exposes rejected/cancelled states", async () => {
    registrationRecords.push(
      registration("rejected", "REJECTED"),
      registration("cancelled", "CANCELLED"),
    );
    await render();
    expect(wrapper.findAll('.status[data-tone="error"]')).toHaveLength(2);
    await wrapper.get('select[aria-label="Filter registrations"]').setValue("CONFIRMED");
    expect(wrapper.findAll(".queue article")).toHaveLength(1);
    expect(wrapper.get(".queue").text()).toContain("R-confirmed");
    expect(wrapper.get('[aria-label="Registration queue summary"]').text()).toContain("Total6");
    await wrapper.get('select[aria-label="Filter registrations"]').setValue("REJECTED");
    expect(wrapper.get(".queue").text()).toContain("R-rejected");
  });

  it("shows load failures and recovers through Refresh", async () => {
    request.mockRejectedValueOnce(new Error("Registration service offline"));
    await render();
    expect(wrapper.text()).toContain(
      "Registration workspace unavailable Registration service offline",
    );
    expect(wrapper.text()).toContain("No registrations in this queue");
    await click("Refresh");
    expect(wrapper.text()).not.toContain("Registration service offline");
    expect(wrapper.findAll(".queue article")).toHaveLength(4);
  });

  it("only offers active enrolments and open periods, and refuses incomplete curriculum requests", async () => {
    await render();
    await click("Start registration");
    const drawer = wrapper.get('[role="dialog"]');
    expect(drawer.text()).toContain("R-eligible");
    for (const value of [
      "R-pending",
      "R-suspended",
      "R-withdrawn",
      "Other semester",
      "Closed semester",
    ])
      expect(drawer.text()).not.toContain(value);
    await click("Load approved curriculum");
    expect(showError).toHaveBeenCalledWith(
      "Registration details are incomplete",
      expect.any(String),
    );
    expect(
      request.mock.calls.filter(([url]) => String(url).includes("registration-catalogue")),
    ).toHaveLength(0);
    await wrapper.get("form").trigger("submit");
    expect(request.mock.calls.filter(([, options]) => options?.method === "POST")).toHaveLength(0);
    await select("Active student enrolment", "eligible");
    await select("Open academic period", "");
    await click("Load approved curriculum");
    expect(showError).toHaveBeenCalledTimes(2);
  });

  it("maps year and semester to the approved curriculum request and clears stale selections", async () => {
    await render();
    await prepareDraft();
    expect(request).toHaveBeenCalledWith(
      "/api/academic/registration-catalogue?academicPeriodId=current-period&programmeVersionId=approved-version&periodNumber=1",
    );
    expect(wrapper.get('[role="dialog"]').text()).toContain("Automatically included");
    await wrapper.get('input[type="checkbox"]').setValue(true);
    await select("Year of study", "2");
    await select("Semester", "2");
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
    await click("Load approved curriculum");
    expect(request).toHaveBeenCalledWith(
      "/api/academic/registration-catalogue?academicPeriodId=current-period&programmeVersionId=approved-version&periodNumber=4",
    );
    expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false);
  });

  it.each([1, 2])(
    "creates a draft with explicit electives and reports %i Modules correctly",
    async (moduleCount) => {
      await render();
      await prepareDraft();
      const checkbox = wrapper.get('input[type="checkbox"]');
      await checkbox.setValue(true);
      await checkbox.setValue(false);
      await checkbox.setValue(true);
      const created = registration("created");
      if (moduleCount === 2) created.modules.push({ ...created.modules[0]!, id: "second-module" });
      request.mockResolvedValueOnce(created);
      await wrapper.get("form").trigger("submit");
      await flushPromises();
      expect(request).toHaveBeenLastCalledWith("/api/student-records/registrations", {
        method: "POST",
        body: {
          studentId: "student",
          programmeEnrolmentId: "enrolment",
          academicPeriodId: "current-period",
          programmePeriodNumber: 1,
          registrationType: "NORMAL",
          selectedElectiveCurriculumModuleIds: ["elective"],
        },
      });
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(wrapper.get(".queue").text()).toContain("R-created");
      expect(notify).toHaveBeenCalledWith(
        expect.objectContaining({
          description: `R-created has ${moduleCount} approved curriculum Module${moduleCount === 1 ? "" : "s"}.`,
        }),
      );
      await click("Start registration");
      expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
      await click("Cancel");
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );

  it("keeps the draft editable after curriculum or save errors", async () => {
    await render();
    await click("Start registration");
    await select("Active student enrolment", "eligible");
    request.mockRejectedValueOnce(new Error("No approved curriculum"));
    await click("Load approved curriculum");
    expect(showError).toHaveBeenCalledWith(
      "Approved curriculum could not be loaded",
      "No approved curriculum",
    );
    await click("Load approved curriculum");
    request.mockRejectedValueOnce(new Error("Duplicate registration"));
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(showError).toHaveBeenCalledWith(
      "Registration could not be started",
      "Duplicate registration",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    expect(notify).not.toHaveBeenCalled();
  });

  it.each([
    ["Submit", "draft", "submit", "SUBMITTED"],
    ["Academic approve", "academic", "academic-approve", "ACADEMIC_APPROVED"],
    ["Registry confirm", "registry", "confirm", "CONFIRMED"],
    ["Reject", "academic", "reject", "REJECTED"],
  ] as const)(
    "records %s with version and reason, updates only that row",
    async (label, id, action, status) => {
      await render();
      fire.mockResolvedValue({ isConfirmed: true, value: "  Evidence verified  " });
      request.mockResolvedValueOnce(registration(id, status));
      await click(label);
      expect(request).toHaveBeenLastCalledWith(
        `/api/student-records/registrations/${id}/${action}`,
        { method: "POST", body: { expectedVersion: 4, reason: "Evidence verified" } },
      );
      expect(wrapper.findAll(".queue article")).toHaveLength(4);
      const options = fire.mock.calls[0]![0];
      expect(options.inputValidator("   ")).toBe("A decision reason is required.");
      expect(options.inputValidator("Evidence verified")).toBeUndefined();
      expect(options.icon).toBe(action === "reject" ? "warning" : "question");
      if (action === "confirm") expect(options.text).toContain("Assessment/Results and Exams");
      expect(notify).toHaveBeenCalledWith(
        expect.objectContaining({ color: action === "reject" ? "warning" : "success" }),
      );
    },
  );

  it.each([{ isConfirmed: false }, { isConfirmed: true, value: " " }, { isConfirmed: true }])(
    "does not mutate records when confirmation has no decision: %j",
    async (result) => {
      await render();
      fire.mockResolvedValue(result);
      request.mockClear();
      await click("Submit");
      expect(request).not.toHaveBeenCalled();
      expect(notify).not.toHaveBeenCalled();
    },
  );

  it("preserves the queue after a stale decision is rejected and releases the busy state", async () => {
    await render();
    fire.mockResolvedValue({ isConfirmed: true, value: "Evidence checked" });
    request.mockRejectedValueOnce(new Error("Registration version has changed"));
    await click("Submit");
    expect(showError).toHaveBeenCalledWith(
      "Registration decision could not be recorded",
      "Registration version has changed",
    );
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Submit")!
        .attributes("aria-busy"),
    ).toBe("false");
    expect(notify).not.toHaveBeenCalled();
  });
});
