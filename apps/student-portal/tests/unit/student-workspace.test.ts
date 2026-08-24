// Author: Tinashe K

import { flushPromises, shallowMount } from "@vue/test-utils";
import { computed, onMounted, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const request = vi.fn();
const errorMessage = vi.fn((_error: unknown, fallback = "Request failed") => fallback);
const syncCoreUser = vi.fn();
const logout = vi.fn();
const toastAdd = vi.fn();
const confirmAction = vi.fn();
const showError = vi.fn();

vi.stubGlobal("computed", computed);
vi.stubGlobal("reactive", reactive);
vi.stubGlobal("ref", ref);
vi.stubGlobal("onMounted", onMounted);
vi.stubGlobal("watch", vi.fn());
vi.stubGlobal("onWatcherCleanup", vi.fn());
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("useEmhareAuth", () => ({
  syncCoreUser,
  logout,
  displayName: ref("Student One"),
}));
vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage }));
vi.stubGlobal("useToast", () => ({ add: toastAdd }));
vi.stubGlobal("useEmhareConfirm", () => ({ confirmAction, showError }));
vi.stubGlobal("useProgrammeStudyPeriod", () => ({
  semesterItems: [
    { label: "Semester 1", value: 1 },
    { label: "Semester 2", value: 2 },
  ],
  yearOfStudyItems: (maximum: number) =>
    Array.from({ length: maximum }, (_, index) => ({
      label: `Year ${index + 1}`,
      value: index + 1,
    })),
  studyPeriodLabel: (period: number) => `Stage ${period}`,
  toProgrammePeriodNumber: (year: number, semester: number) => (year - 1) * 2 + semester,
}));

const enrolment = {
  id: "enrolment-1",
  studentId: "student-1",
  programmeId: "programme-1",
  programmeVersionId: "programme-version-1",
  programmeCode: "HCS",
  programmeName: "Computer Science",
  commencementDate: "2025-08-01",
  status: "ACTIVE",
};

const workspace = {
  studentId: "student-1",
  studentNumber: "R123456A",
  status: "ACTIVE",
  programmeEnrolments: [enrolment],
};

const draftRegistration = {
  id: "registration-1",
  programmeEnrolmentId: "enrolment-1",
  academicPeriodId: "period-1",
  academicPeriodCode: "AUG-2026",
  academicPeriodName: "August 2026",
  programmeCode: "HCS",
  programmeName: "Computer Science",
  programmePeriodNumber: 1,
  totalCredits: 30,
  status: "DRAFT",
  version: 0,
  modules: [],
};

const overview = {
  programmes: [
    {
      id: "programme-1",
      code: "HCS",
      name: "Computer Science",
      maximumDurationPeriods: 8,
    },
  ],
  academicPeriods: [
    {
      id: "period-closed",
      code: "JAN-2026",
      name: "January 2026",
      startDate: "2026-01-01",
      endDate: "2026-06-30",
      status: "CLOSED",
    },
    {
      id: "period-1",
      code: "AUG-2026",
      name: "August 2026",
      startDate: "2026-08-01",
      endDate: "2026-12-20",
      status: "OPEN",
    },
  ],
};

const catalogue = {
  programmeVersionId: "programme-version-1",
  academicPeriodId: "period-1",
  periodNumber: 1,
  modules: [
    {
      curriculumModuleId: "curriculum-module-1",
      moduleId: "module-1",
      moduleCode: "CT101",
      moduleName: "Programming",
      moduleType: "COMPULSORY",
      creditValue: 15,
    },
    {
      curriculumModuleId: "curriculum-module-2",
      moduleId: "module-2",
      moduleCode: "CT102",
      moduleName: "Web Systems",
      moduleType: "ELECTIVE",
      creditValue: 15,
    },
  ],
};

async function mountWorkspace() {
  const StudentWorkspace = (await import("../../pages/student/index.vue")).default;
  const wrapper = shallowMount(StudentWorkspace, {
    global: {
      stubs: {
        UContainer: { template: "<div><slot /></div>" },
        UButton: { props: ["label"], template: "<button>{{ label }}<slot /></button>" },
        UAlert: true,
        UBadge: true,
        UCard: { template: "<div><slot name='header'/><slot /></div>" },
        UTable: true,
        USelect: true,
        USelectMenu: true,
        UFormField: { template: "<div><slot /></div>" },
        USkeleton: true,
        UIcon: true,
        EmharePageHeader: { template: "<div><slot name='actions'/><slot /></div>" },
        EmhareGuidedActionButton: true,
        EmhareStatusPill: true,
        EmhareSectionNav: true,
        EmhareRecordDrawer: { template: "<div><slot name='body'/><slot name='footer'/></div>" },
        EmharePaginatedTable: {
          props: ["data"],
          template: `<div>
            <slot name="programmeCode-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="commencementDate-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="programmePeriodNumber-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="totalCredits-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="status-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="actions-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="moduleCode-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="curriculumModuleType-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="creditValue-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="minimumMarkRequired-cell" :row="{ original: data?.[0] || {} }" />
            <slot name="empty" />
          </div>`,
        },
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("student workspace at /student", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    syncCoreUser.mockResolvedValue({});
    confirmAction.mockResolvedValue(true);
    showError.mockResolvedValue(undefined);
    request.mockImplementation(async (url: string, options?: { method?: string }) => {
      if (url === "/api/student-records/me") return workspace;
      if (url === "/api/student-records/registrations/mine" && options?.method === "POST") {
        return draftRegistration;
      }
      if (url === "/api/student-records/registrations/mine") return [draftRegistration];
      if (url === "/api/academic/overview") return overview;
      if (url.startsWith("/api/academic/registration-catalogue")) return catalogue;
      if (url.endsWith("/submit")) return { ...draftRegistration, status: "SUBMITTED", version: 1 };
      throw new Error(`Unexpected URL ${url}`);
    });
  });

  it("loads the owned record and completes registration preparation and submission", async () => {
    const wrapper = await mountWorkspace();
    const page = wrapper.vm as unknown as Record<string, any>;
    expect(syncCoreUser).toHaveBeenCalled();
    expect(page.workspace.studentNumber).toBe("R123456A");
    expect(page.activeEnrolments).toHaveLength(1);
    expect(page.openAcademicPeriods).toHaveLength(1);
    expect(page.programmeEnrolmentItems[0].label).toContain("HCS");
    expect(page.academicPeriodItems[0].label).toContain("AUG-2026");

    page.openRegistrationDrawer();
    expect(page.registrationDrawerOpen).toBe(true);
    expect(page.registrationForm.programmeEnrolmentId).toBe("enrolment-1");
    await page.loadRegistrationCatalogue();
    expect(page.registrationCatalogue.modules).toHaveLength(2);
    expect(page.compulsoryModules[0].moduleCode).toBe("CT101");
    expect(page.electiveItems[0].label).toContain("CT102");
    expect(page.registrationFormReady).toBe(true);

    page.registrationForm.selectedElectiveCurriculumModuleIds = ["curriculum-module-2"];
    await page.createRegistration();
    expect(page.activeSection).toBe("REGISTRATIONS");
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Draft registration created" }),
    );

    await page.submitRegistration(draftRegistration);
    expect(request).toHaveBeenCalledWith(
      "/api/student-records/registrations/mine/registration-1/submit",
      expect.objectContaining({ method: "POST" }),
    );
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Registration submitted" }),
    );
    page.openRegistrationDetails(draftRegistration);
    expect(page.registrationDetailsOpen).toBe(true);
  });

  it("derives stages, guidance, credits, date labels, and all status tones", async () => {
    const wrapper = await mountWorkspace();
    const page = wrapper.vm as unknown as Record<string, any>;
    expect(page.nextProgrammePeriodNumber("missing")).toBe(1);
    page.registrations = [
      { ...draftRegistration, status: "CONFIRMED", programmePeriodNumber: 2, totalCredits: 30 },
      { ...draftRegistration, id: "registration-2", status: "CONFIRMED", totalCredits: 15 },
    ];
    expect(page.nextProgrammePeriodNumber("enrolment-1")).toBe(3);
    expect(page.fromInternalPeriod(3)).toEqual({ yearOfStudy: 2, semesterNumber: 1 });
    expect(page.totalConfirmedCredits).toBe(45);
    expect(page.latestConfirmedRegistration.status).toBe("CONFIRMED");
    expect(
      page.currentOrNextOpenPeriod(overview.academicPeriods.filter((p) => p.status === "OPEN"))?.id,
    ).toBe("period-1");
    expect(page.currentOrNextOpenPeriod([])).toBeUndefined();
    expect(page.statusTone("ACTIVE")).toBe("success");
    expect(page.statusTone("DRAFT")).toBe("info");
    expect(page.statusTone("REJECTED")).toBe("error");
    expect(page.statusTone("UNKNOWN")).toBe("neutral");
    expect(page.formatDate(null)).toBe("Not captured");
    expect(page.formatDate("2026-08-01")).toContain("2026");

    page.workspace = { ...workspace, programmeEnrolments: [] };
    page.academicOverview = { ...overview, academicPeriods: [] };
    expect(page.registrationStartGuidance).toHaveLength(2);
    expect(page.registrationFormGuidance.length).toBeGreaterThan(0);
  });

  it("surfaces loading, catalogue, creation, and submission failures safely", async () => {
    const wrapper = await mountWorkspace();
    const page = wrapper.vm as unknown as Record<string, any>;

    request.mockRejectedValueOnce(new Error("offline"));
    await page.loadWorkspace();
    expect(page.loadError).toBe("The student workspace could not be loaded.");

    page.openRegistrationDrawer();
    request.mockRejectedValueOnce(new Error("catalogue missing"));
    await page.loadRegistrationCatalogue();
    expect(page.registrationFormError).toContain("No approved curriculum");

    page.registrationCatalogue = catalogue;
    request.mockRejectedValueOnce(new Error("create failed"));
    await page.createRegistration();
    expect(page.registrationFormError).toBe("Registration could not be created.");

    confirmAction.mockResolvedValueOnce(false);
    await page.submitRegistration(draftRegistration);
    request.mockRejectedValueOnce(new Error("submit failed"));
    await page.submitRegistration(draftRegistration);
    expect(showError).toHaveBeenCalledWith("Registration could not be submitted", "Request failed");

    page.registrationDrawerOpen = false;
    await page.loadRegistrationCatalogue();
    page.registrationCatalogue = null;
    await page.createRegistration();
  });

  it("covers empty ownership, pending stages, replacement, and stale catalogue responses", async () => {
    const wrapper = await mountWorkspace();
    const page = wrapper.vm as unknown as Record<string, any>;

    page.workspace = null;
    page.academicOverview = null;
    page.registrationCatalogue = null;
    expect(page.activeEnrolments).toEqual([]);
    expect(page.selectedEnrolment).toBeNull();
    expect(page.selectedProgramme).toBeNull();
    expect(page.yearOfStudyOptions).toHaveLength(2);
    expect(page.compulsoryModules).toEqual([]);
    expect(page.electiveModules).toEqual([]);
    expect(page.currentModules).toEqual([]);
    page.openRegistrationDrawer();
    expect(page.registrationForm.programmeEnrolmentId).toBe("");
    expect(page.registrationForm.academicPeriodId).toBe("");

    page.workspace = workspace;
    page.academicOverview = overview;
    page.registrations = [{ ...draftRegistration, status: "SUBMITTED", programmePeriodNumber: 2 }];
    expect(page.nextProgrammePeriodNumber("enrolment-1")).toBe(2);
    page.registrations = [{ ...draftRegistration, status: "CANCELLED" }];
    expect(page.nextProgrammePeriodNumber("enrolment-1")).toBe(1);

    page.registrations = [draftRegistration];
    page.selectedRegistration = draftRegistration;
    const updated = { ...draftRegistration, status: "SUBMITTED", version: 1 };
    page.replaceRegistration(updated);
    expect(page.selectedRegistration.status).toBe("SUBMITTED");
    page.replaceRegistration({ ...updated, id: "missing" });

    expect(
      page.currentOrNextOpenPeriod([
        { ...overview.academicPeriods[1], id: "future", startDate: "2027-01-01" },
      ]).id,
    ).toBe("future");
    expect(
      page.currentOrNextOpenPeriod([
        {
          ...overview.academicPeriods[1],
          id: "past",
          startDate: "2025-01-01",
          endDate: "2025-02-01",
        },
      ]).id,
    ).toBe("past");

    page.openRegistrationDrawer();
    let resolveFirst!: (value: typeof catalogue) => void;
    let resolveSecond!: (value: typeof catalogue) => void;
    request.mockImplementationOnce(
      () => new Promise((resolve) => (resolveFirst = resolve as (value: typeof catalogue) => void)),
    );
    request.mockImplementationOnce(
      () =>
        new Promise((resolve) => (resolveSecond = resolve as (value: typeof catalogue) => void)),
    );
    const firstRequest = page.loadRegistrationCatalogue();
    const secondRequest = page.loadRegistrationCatalogue();
    resolveSecond({ ...catalogue, periodNumber: 2 });
    await secondRequest;
    resolveFirst(catalogue);
    await firstRequest;
    expect(page.registrationCatalogue.periodNumber).toBe(2);
  });

  it("renders all workspace sections and registration drawer states", async () => {
    const wrapper = await mountWorkspace();
    const page = wrapper.vm as unknown as Record<string, any>;
    page.registrations = [
      {
        ...draftRegistration,
        status: "CONFIRMED",
        registrationType: "STANDARD",
        statusReason: "Approved",
        modules: [
          {
            moduleCode: "CT101",
            moduleName: "Programming",
            curriculumModuleType: "COMPULSORY",
            creditValue: 15,
            minimumMarkRequired: 50,
          },
        ],
      },
    ];
    page.openRegistrationDetails(page.registrations[0]);
    page.registrationCatalogue = catalogue;
    page.registrationDrawerOpen = true;
    page.activeSection = "REGISTRATIONS";
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("Registration history");
    page.activeSection = "MODULES";
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("My confirmed Modules");
    page.loadingCatalogue = true;
    await wrapper.vm.$nextTick();
    page.loadingCatalogue = false;
    page.selectedRegistration.modules[0].minimumMarkRequired = null;
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("Institution rule");
  });
});
