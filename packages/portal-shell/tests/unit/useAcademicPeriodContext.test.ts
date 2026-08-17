// Author: Tinashe K

import { beforeEach, describe, expect, it, vi } from "vitest";
import { computed, ref } from "vue";
import { useAcademicPeriodContext } from "../../composables/useAcademicPeriodContext";

const selectedAcademicPeriodId = ref<string | null>("period-1");
const overview = ref({
  academicPeriods: [
    {
      id: "period-1",
      code: "2027-S1",
      academicYearId: "year-2027",
    },
  ],
  intakes: [],
});

beforeEach(() => {
  selectedAcademicPeriodId.value = "period-1";
  vi.stubGlobal("computed", computed);
  vi.stubGlobal("useCookie", () => selectedAcademicPeriodId);
  vi.stubGlobal("useAcademicSetup", () => ({
    overview,
    ensureOverview: vi.fn(),
  }));
});

describe("useAcademicPeriodContext", () => {
  it("matches business records by their explicit academic period code", () => {
    const context = useAcademicPeriodContext();

    expect(
      context.matchesAcademicPeriod({
        id: "result-batch-1",
        academicPeriodCode: "2027-S1",
      }),
    ).toBe(true);
    expect(
      context.matchesAcademicPeriod({
        id: "result-batch-2",
        academicPeriodCode: "2027-S2",
      }),
    ).toBe(false);
  });

  it("matches academic period records by their own identity", () => {
    const context = useAcademicPeriodContext();

    expect(context.matchesAcademicPeriod({ id: "period-1", code: "2027-S1" })).toBe(true);
    expect(context.matchesAcademicPeriod({ id: "period-2", code: "2027-S2" })).toBe(false);
  });
});
