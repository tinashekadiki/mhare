// Author: Tinashe K
import { type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import AccommodationPage from "../../pages/operations/accommodation.vue";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { mountCampusPage } from "../../../../tests/unit/support/campus-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let setup: ReturnType<typeof accommodationFixture>;
const academicPeriod = {
  id: "period-current",
  code: "SEM1",
  name: "Semester One",
  startDate: "2026-08-01",
  endDate: "2026-12-31",
};
const period = {
  id: "period",
  academicPeriodId: "period-current",
  academicPeriodCode: "SEM1",
  code: "RES1",
  name: "Residence term",
  applicationsOpenAt: "2026-08-01T08:00:00Z",
  applicationsCloseAt: "2026-08-31T18:00:00Z",
  allocationCutoffAt: "2026-09-01T18:00:00Z",
  occupancyStartsOn: "2026-09-01",
  occupancyEndsOn: "2026-12-20",
  status: "DRAFT",
  version: 9,
};
function accommodationFixture() {
  return {
    premises: [
      {
        id: "premise",
        code: "MAIN",
        name: "Main campus",
        addressLine: "Campus road",
        suburb: "Mount Pleasant",
        landlordName: "UZ",
        contactDetails: "Registry",
        active: true,
        version: 5,
      },
      { id: "inactive-premise", code: "OLD", name: "Old premise", active: false, version: 1 },
    ],
    roomTypes: [
      {
        id: "type",
        code: "SINGLE",
        name: "Single room",
        description: "Single occupancy",
        defaultCapacity: 1,
        active: true,
        version: 6,
      },
      { id: "inactive-type", code: "OLD", name: "Old type", active: false, version: 1 },
    ],
    residenceHalls: [
      {
        id: "hall",
        premiseId: "premise",
        premiseCode: "MAIN",
        code: "HALL",
        name: "Main hall",
        residentGenderPolicy: "ANY",
        wardenName: "Warden",
        wardenContact: "Telephone",
        active: true,
        version: 7,
      },
      {
        id: "inactive-hall",
        code: "OLD",
        name: "Old hall",
        residentGenderPolicy: "MALE",
        active: false,
        version: 1,
      },
    ],
    rooms: [
      {
        id: "room",
        residenceHallId: "hall",
        residenceHallCode: "HALL",
        roomTypeId: "type",
        roomTypeCode: "SINGLE",
        code: "101",
        floorLabel: "1",
        capacity: 2,
        accessibilityReady: true,
        conditionStatus: "AVAILABLE",
        conditionNotes: null,
        reservedForGroupId: null,
        active: true,
        version: 8,
      },
      {
        id: "maintenance",
        code: "102",
        capacity: 3,
        conditionStatus: "MAINTENANCE",
        accessibilityReady: false,
        active: true,
        version: 1,
      },
      {
        id: "out",
        code: "103",
        capacity: 4,
        conditionStatus: "OUT_OF_SERVICE",
        active: false,
        version: 1,
      },
    ],
    applicationPeriods: [
      { ...period },
      ...["APPLICATION_OPEN", "APPLICATION_CLOSED", "ALLOCATION_ACTIVE", "CLOSED"].map(
        (status) => ({ ...period, id: status, code: status, status }),
      ),
    ],
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  setup = accommodationFixture();
  vi.stubGlobal("useAcademicSetup", () => ({
    overview: ref({
      academicPeriods: [
        academicPeriod,
        { ...academicPeriod, id: "second", code: "SEM2", name: "Semester Two" },
      ],
    }),
    ensureOverview: vi.fn().mockResolvedValue(undefined),
  }));
  vi.mocked(Swal.fire).mockResolvedValue({
    isConfirmed: true,
    value: "  Approved capacity and dates  ",
  } as any);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    if (path === "/api/accommodation/setup") return structuredClone(setup);
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
describe("Accommodation setup workspace", () => {
  it.each([
    ["Premises", "premise", "premises", 5],
    ["Room types", "type", "room-types", 6],
    ["Residence halls", "hall", "residence-halls", 7],
    ["Rooms", "room", "rooms", 8],
  ] as const)("carries the persisted version when editing %s", async (tab, id, route, version) => {
    wrapper = await mountCampusPage(AccommodationPage);
    await clickButton(wrapper, tab);
    await wrapper.get(`[data-record="${id}"] button`).trigger("click");
    await wrapper.get('[role="dialog"]').findAll('input[type="checkbox"]').at(-1)!.setValue(false);
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      `/api/accommodation/setup/${route}/${id}`,
      expect.objectContaining({
        method: "PUT",
        body: expect.objectContaining({ expectedVersion: version, active: false }),
      }),
    );
  });
  it("separates available beds from configured active capacity", async () => {
    wrapper = await mountCampusPage(AccommodationPage);
    expect(wrapper.text()).toContain("Configured beds: 5");
    expect(wrapper.text()).toContain("Beds available for allocation: 2");
    expect(wrapper.text()).toContain("Premises: 1");
    expect(wrapper.text()).toContain("Residence halls: 1");
    await clickButton(wrapper, "Rooms");
    expect(wrapper.text()).toContain("Maintenance");
    expect(wrapper.text()).toContain("Out Of Service");
    expect(wrapper.text()).toContain("Ready");
    expect(wrapper.text()).toContain("Standard");
  });
  it.each([
    [
      "Premises",
      "Create premise",
      "premises",
      {
        Code: "NEW",
        Name: "New campus",
        Address: "Road",
        Suburb: "North",
        Landlord: "UZ",
        "Contact details": "Registry",
      },
      { code: "NEW", addressLine: "Road", suburb: "North", landlordName: "UZ" },
    ],
    [
      "Room types",
      "Create room type",
      "room-types",
      { Code: "TWIN", Name: "Twin", Description: "Two beds", "Default capacity": "2" },
      { code: "TWIN", defaultCapacity: 2, description: "Two beds" },
    ],
    [
      "Residence halls",
      "Create residence hall",
      "residence-halls",
      {
        Code: "EAST",
        Name: "East hall",
        "Resident policy": "FEMALE",
        Warden: "Manager",
        "Warden contact": "Phone",
      },
      { premiseId: "premise", residentGenderPolicy: "FEMALE", wardenName: "Manager" },
    ],
    [
      "Rooms",
      "Create room",
      "rooms",
      {
        "Room code": "201",
        Floor: "2",
        "Bed capacity": "3",
        Condition: "MAINTENANCE",
        "Condition notes": "Painting",
      },
      {
        residenceHallId: "hall",
        roomTypeId: "type",
        code: "201",
        capacity: 3,
        conditionStatus: "MAINTENANCE",
        conditionNotes: "Painting",
        reservedForGroupId: null,
      },
    ],
  ] as const)(
    "creates %s with controlled input payloads",
    async (tab, action, route, fields, body) => {
      wrapper = await mountCampusPage(AccommodationPage);
      await clickButton(wrapper, tab);
      await clickButton(wrapper, action);
      for (const [label, value] of Object.entries(fields)) await setField(wrapper, label, value);
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Old premise");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Old hall");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Old type");
      await clickButton(wrapper, "Save record");
      expect(context.request).toHaveBeenCalledWith(
        `/api/accommodation/setup/${route}`,
        expect.objectContaining({
          method: "POST",
          body: expect.objectContaining({ ...body, expectedVersion: 0 }),
        }),
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("prefills the calendar snapshot and serializes new period timestamps", async () => {
    wrapper = await mountCampusPage(AccommodationPage);
    await clickButton(wrapper, "Application periods");
    await clickButton(wrapper, "Create application period");
    await setField(wrapper, "Academic period", "period-current");
    expect(wrapper.get('[data-label="Code"] input').element).toHaveProperty("value", "RES-SEM1");
    expect(wrapper.get('[data-label="Name"] input').element).toHaveProperty(
      "value",
      "Semester One accommodation",
    );
    expect(wrapper.get('[data-label="Occupancy starts"] input').element).toHaveProperty(
      "value",
      "2026-08-01",
    );
    for (const label of ["Applications open", "Applications close", "Allocation cutoff"])
      await setField(wrapper, label, "2026-08-20T08:00");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      "/api/accommodation/setup/application-periods",
      expect.objectContaining({
        method: "POST",
        body: expect.objectContaining({
          academicPeriodCode: "SEM1",
          code: "RES-SEM1",
          applicationsOpenAt: new Date("2026-08-20T08:00").toISOString(),
        }),
      }),
    );
  });
  it("preserves a draft period's existing values and version on calendar changes", async () => {
    wrapper = await mountCampusPage(AccommodationPage);
    await clickButton(wrapper, "Application periods");
    await wrapper.get('[data-record="period"] button').trigger("click");
    await setField(wrapper, "Academic period", "second");
    expect(wrapper.get('[data-label="Code"] input').element).toHaveProperty("value", "RES1");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      "/api/accommodation/setup/application-periods/period",
      expect.objectContaining({
        method: "PUT",
        body: expect.objectContaining({
          expectedVersion: 9,
          academicPeriodCode: "SEM2",
          code: "RES1",
          occupancyStartsOn: "2026-09-01",
        }),
      }),
    );
  });
  it.each([
    ["period", "Application Open", "APPLICATION_OPEN"],
    ["APPLICATION_OPEN", "Application Closed", "APPLICATION_CLOSED"],
    ["APPLICATION_CLOSED", "Allocation Active", "ALLOCATION_ACTIVE"],
    ["ALLOCATION_ACTIVE", "Closed", "CLOSED"],
  ] as const)(
    "progresses %s to its next state with independent decision evidence",
    async (id, label, targetStatus) => {
      wrapper = await mountCampusPage(AccommodationPage);
      await clickButton(wrapper, "Application periods");
      const button = wrapper
        .get(`[data-record="${id}"]`)
        .findAll("button")
        .find((button) => button.text() === label)!;
      await button.trigger("click");
      await Promise.resolve();
      await Promise.resolve();
      expect(context.request).toHaveBeenCalledWith(
        `/api/accommodation/setup/application-periods/${id}/transition`,
        {
          method: "POST",
          body: { targetStatus, reason: "Approved capacity and dates", expectedVersion: 9 },
        },
      );
      expect(wrapper.get('[data-record="CLOSED"]').findAll("button")).toHaveLength(0);
      const validator = (vi.mocked(Swal.fire).mock.calls[0]?.[0] as any).inputValidator;
      expect(validator(" ")).toBe("Approval evidence is required.");
      expect(validator("Valid")).toBeUndefined();
    },
  );
  it.each([{ isConfirmed: false }, { isConfirmed: true, value: " " }, { isConfirmed: true }])(
    "does not progress without confirmed nonblank evidence: %j",
    async (result) => {
      vi.mocked(Swal.fire).mockResolvedValue(result as any);
      wrapper = await mountCampusPage(AccommodationPage);
      await clickButton(wrapper, "Application periods");
      await clickButton(wrapper, "Application Open");
      expect(context.request.mock.calls.filter(([, options]) => options?.method)).toEqual([]);
    },
  );
  it("preserves the form after failed saving and reports failed progression", async () => {
    wrapper = await mountCampusPage(AccommodationPage);
    context.request.mockRejectedValue(new Error("Conflict"));
    await clickButton(wrapper, "Create premise");
    await clickButton(wrapper, "Save record");
    expect(context.showError).toHaveBeenCalledWith("Premise could not be saved", "Conflict");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Application periods");
    await clickButton(wrapper, "Application Open");
    expect(context.showError).toHaveBeenCalledWith(
      "Application period could not be progressed",
      "Conflict",
    );
  });
  it("recovers failed setup loading independently from calendar availability", async () => {
    vi.stubGlobal("useAcademicSetup", () => ({
      overview: ref(null),
      ensureOverview: vi.fn().mockRejectedValue(new Error("Calendar offline")),
    }));
    context.request.mockRejectedValueOnce(new Error("Setup offline"));
    wrapper = await mountCampusPage(AccommodationPage);
    expect(wrapper.text()).toContain("Setup offline");
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Main campus");
    expect(wrapper.text()).not.toContain("Setup offline");
  });
});
