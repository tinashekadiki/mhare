// Author: Tinashe K

import { describe, expect, it, vi } from "vitest";
import {
  isOperationalDashboardKey,
  loadOperationalDashboard,
  loadOperationsOverview,
  operationalDashboardModules,
} from "../../../../packages/portal-shell/utils/operational-dashboard";

describe("Operational dashboards", () => {
  it("catalogues every implemented operational domain with a dedicated dashboard destination", () => {
    expect(operationalDashboardModules.map((module) => module.key)).toEqual([
      "core",
      "academic-setup",
      "admissions",
      "finance",
      "student-records",
      "assessment-results",
      "exams-timetabling",
      "accommodation",
      "dining",
      "documents",
      "notifications",
    ]);
    expect(new Set(operationalDashboardModules.map((module) => module.dashboardPath)).size).toBe(
      11,
    );
    expect(
      operationalDashboardModules.find((module) => module.key === "admissions")?.dashboardPath,
    ).toBe("/operations/admissions-dashboard");
    expect(isOperationalDashboardKey("finance")).toBe(true);
    expect(isOperationalDashboardKey("unknown")).toBe(false);
  });

  it("derives Finance workload from governed pricing, billing and collection state", async () => {
    const request = vi.fn(async (path: string) => {
      if (path === "/api/finance/fee-catalogues") {
        return {
          catalogues: [
            { status: "ACTIVE", rules: [{ status: "APPROVED", ratingStatus: "RATED" }] },
            { status: "DRAFT", rules: [{ status: "PENDING_RATE", ratingStatus: "UNRATED" }] },
          ],
        };
      }
      if (path === "/api/finance/billing") {
        return {
          billingPolicies: [{ status: "ACTIVE" }],
          billingEvents: [{ status: "PENDING_APPROVAL" }, { status: "INVOICED" }],
          invoices: [{ status: "POSTED" }],
        };
      }
      if (path === "/api/finance/collections") {
        return {
          exchangeRates: [],
          payments: [
            { reconciliationStatus: "PENDING", ratingStatus: "UNRATED", reversed: false },
            { reconciliationStatus: "RECONCILED", ratingStatus: "RATED", reversed: false },
          ],
          receipts: [],
          allocations: [],
          creditNotes: [{ status: "DRAFT" }],
        };
      }
      if (path === "/api/finance/collections/accounts") {
        return [{ id: "account-1" }, { id: "account-2" }];
      }
      throw new Error(`Unexpected request: ${path}`);
    });

    const snapshot = await loadOperationalDashboard({ request }, "finance");

    expect(snapshot.metrics.map((metric) => [metric.label, metric.value])).toEqual([
      ["Active fee definitions", 1],
      ["Posted invoices", 1],
      ["Student accounts", 2],
      ["Reconciled payments", 1],
    ]);
    expect(snapshot.actions.map((action) => [action.label, action.value])).toEqual(
      expect.arrayContaining([
        ["Pricing attention", 1],
        ["Billing approval", 1],
        ["Cash reconciliation", 1],
        ["Unrated payments", 1],
        ["Draft credit notes", 1],
      ]),
    );
  });

  it("keeps the main dashboard available when one service cannot provide metrics", async () => {
    const request = vi.fn(async (path: string) => {
      if (path.startsWith("/api/core/")) throw new Error("Core is unavailable");
      if (path === "/api/academic/overview") {
        return {
          academicUnitTypes: [],
          academicUnits: [],
          academicYears: [],
          academicPeriodTypes: [],
          academicPeriods: [],
          intakes: [],
          programmeLevels: [],
          programmeTypes: [],
          programmes: [],
          modules: [],
        };
      }
      throw new Error(`Unavailable in this focused test: ${path}`);
    });

    const overview = await loadOperationsOverview({ request }, ["core", "academic-setup"]);

    expect(overview).toHaveLength(2);
    expect(overview.find((module) => module.key === "core")).toMatchObject({ available: false });
    expect(overview.find((module) => module.key === "academic-setup")).toMatchObject({
      available: true,
    });
  });

  it("groups active academic units by the configured unit-type vocabulary", async () => {
    const request = vi.fn(async () => ({
      academicUnitTypes: [
        { id: "department-type", name: "Department", levelOrder: 3, status: "ACTIVE" },
        { id: "faculty-type", name: "Faculty", levelOrder: 2, status: "ACTIVE" },
        { id: "legacy-type", name: "Legacy school", levelOrder: 1, status: "INACTIVE" },
      ],
      academicUnits: [
        { academicUnitTypeId: "faculty-type", status: "ACTIVE" },
        { academicUnitTypeId: "faculty-type", status: "ACTIVE" },
        { academicUnitTypeId: "department-type", status: "ACTIVE" },
        { academicUnitTypeId: "department-type", status: "INACTIVE" },
        { academicUnitTypeId: "legacy-type", status: "ACTIVE" },
      ],
      academicYears: [],
      academicPeriodTypes: [],
      academicPeriods: [],
      intakes: [],
      programmeLevels: [],
      programmeTypes: [],
      programmes: [],
      modules: [],
    }));

    const snapshot = await loadOperationalDashboard({ request }, "academic-setup");

    expect(snapshot.summaryMetrics).toEqual([
      { label: "Faculty", value: 2 },
      { label: "Department", value: 1 },
    ]);
  });

  it("builds live snapshots for every implemented service contract", async () => {
    const responses: Record<string, unknown> = {
      "/api/core/reports/overview": {
        generatedAt: "2026-08-16T10:00:00Z",
        inventory: { userCount: 4, roleCount: 3, permissionCount: 8, lookupSetCount: 2 },
        loginSessionsLast24Hours: 2,
        auditEventsLast24Hours: 5,
      },
      "/api/core/workflows/tasks": ["OPEN", "CLAIMED", "COMPLETED", "CANCELLED"].map((status) => ({
        status,
      })),
      "/api/academic/overview": {
        academicUnitTypes: [],
        academicUnits: [{ status: "ACTIVE" }, { status: "INACTIVE" }],
        academicYears: [{ status: "DRAFT" }, { status: "OPEN" }],
        academicPeriodTypes: [],
        academicPeriods: [{ status: "DRAFT" }, { status: "OPEN" }],
        intakes: [{ status: "DRAFT" }, { status: "OPEN" }],
        programmeLevels: [],
        programmeTypes: [],
        programmes: [{ status: "ACTIVE" }, { status: "DRAFT" }],
        modules: [{ status: "ACTIVE" }, { status: "INACTIVE" }],
      },
      "/api/admissions/reports/pipeline-summary": {
        generatedAt: "2026-08-16T10:00:00Z",
        totalApplications: 4,
        totalApplicants: 3,
        statusCounts: [
          { code: "SUBMITTED", count: 1 },
          { code: "UNDER_ACADEMIC_REVIEW", count: 1 },
          { code: "ACCEPTED", count: 1 },
        ],
        paymentCounts: [
          { code: "PAID", count: 1 },
          { code: "WAIVED", count: 1 },
          { code: "PENDING", count: 2 },
        ],
        categoryCounts: [],
        genderCounts: [],
        rankedChoiceCounts: [{ rank: 1, choices: 3 }],
        intakeStatistics: [],
        programmeStatistics: [],
        filterOptions: {
          intakes: [],
          applicationTypes: [],
          programmes: [],
          categories: [],
          genders: [],
        },
      },
      "/api/finance/fee-catalogues": {
        catalogues: [
          { status: "ACTIVE", rules: [{ status: "APPROVED", ratingStatus: "RATED" }] },
          { status: "DRAFT", rules: [{ status: "PENDING_RATE", ratingStatus: "UNRATED" }] },
        ],
      },
      "/api/finance/billing": {
        billingPolicies: [],
        billingEvents: [{ status: "PENDING_APPROVAL" }, { status: "INVOICED" }],
        invoices: [{ status: "POSTED" }],
      },
      "/api/finance/collections": {
        exchangeRates: [],
        payments: [
          { reconciliationStatus: "PENDING", ratingStatus: "UNRATED", reversed: false },
          { reconciliationStatus: "RECONCILED", ratingStatus: "RATED", reversed: false },
          { reconciliationStatus: "REJECTED", ratingStatus: "RATED", reversed: true },
        ],
        receipts: [],
        allocations: [],
        creditNotes: [{ status: "DRAFT" }, { status: "POSTED" }],
      },
      "/api/finance/collections/accounts": [{ id: "account-1" }],
      "/api/student-records/conversions": [
        { status: "COMPLETED" },
        { status: "PROVISIONING" },
        { status: "FAILED" },
      ],
      "/api/student-records/registrations": [
        { status: "DRAFT", initiatedAt: "2026-05-05T08:00:00Z", modules: [] },
        { status: "SUBMITTED", initiatedAt: "2026-06-10T08:00:00Z", modules: [] },
        { status: "ACADEMIC_APPROVED", initiatedAt: "2026-06-18T08:00:00Z", modules: [] },
        {
          status: "CONFIRMED",
          initiatedAt: "2026-07-02T08:00:00Z",
          modules: [{ id: "module-1" }, { id: "module-2" }],
        },
      ],
      "/api/assessment-results/offerings": [
        { status: "ACTIVE", schemes: [{ status: "APPROVED" }, { status: "DRAFT" }] },
        { status: "CLOSED", schemes: [] },
      ],
      "/api/assessment-results/amendments": [{ status: "REQUESTED" }, { status: "APPROVED" }],
      "/api/results/batches": [
        { status: "DRAFT", resultCount: 1 },
        { status: "SUBMITTED", resultCount: 1 },
        { status: "MODERATED", resultCount: 1 },
        { status: "APPROVED", resultCount: 1 },
        { status: "PUBLISHED", resultCount: 2 },
        { status: "REJECTED", resultCount: 1 },
      ],
      "/api/results/progression/decisions": [
        { status: "CALCULATED" },
        { status: "REVIEWED" },
        { status: "APPROVED" },
        { status: "PUBLISHED" },
        { status: "REJECTED" },
      ],
      "/api/exams/setup": {
        venueTypes: [],
        venues: [{ active: true }, { active: false }],
        sessions: [{ status: "DRAFT" }, { status: "APPROVED" }],
        requirements: [{ status: "DRAFT" }, { status: "APPROVED" }],
      },
      "/api/timetabling/runs": ["GENERATED", "REVIEWED", "APPROVED", "PUBLISHED", "REJECTED"].map(
        (status) => ({ status }),
      ),
      "/api/exams/invigilation": {
        venueOperations: [
          {
            attendanceSession: {
              status: "OPEN",
              incidents: [{ status: "REPORTED" }, { status: "RESOLVED" }],
            },
          },
          { attendanceSession: { status: "CLOSED", incidents: [] } },
          { attendanceSession: null },
        ],
      },
      "/api/accommodation/setup": {
        premises: [],
        roomTypes: [],
        residenceHalls: [],
        applicationPeriods: [],
        rooms: [
          { active: true, conditionStatus: "AVAILABLE", capacity: 2 },
          { active: true, conditionStatus: "MAINTENANCE", capacity: 3 },
          { active: false, conditionStatus: "AVAILABLE", capacity: 4 },
        ],
      },
      "/api/accommodation/operations": {
        rates: [
          { status: "DRAFT", ratingStatus: "UNRATED" },
          { status: "ACTIVE", ratingStatus: "RATED" },
        ],
        applications: [{ status: "SUBMITTED" }, { status: "ELIGIBLE" }, { status: "ALLOCATED" }],
        waitlistEntries: [{ status: "ACTIVE" }, { status: "ALLOCATED" }],
        allocations: [
          { status: "PROPOSED", billingStatus: "FAILED" },
          { status: "ALLOCATED", billingStatus: "ACCEPTED" },
          { status: "CHECKED_IN", billingStatus: "ACCEPTED" },
          { status: "CHECKED_OUT", billingStatus: "ACCEPTED" },
        ],
        allocationEvents: [],
      },
      "/api/dining/setup": {
        diningHalls: [{ active: true }, { active: false }],
        mealOptions: [],
        serviceTimes: [],
        diningPlans: [{ status: "DRAFT" }, { status: "ACTIVE" }],
        planMeals: [],
        hallAssignmentRules: [],
        attendantAssignments: [],
      },
      "/api/dining/operations": {
        assignments: [{ status: "ACTIVE" }, { status: "ENDED" }],
        dietaryRequirements: [
          { status: "ACTIVE", severity: "CRITICAL" },
          { status: "ACTIVE", severity: "INFORMATION" },
          { status: "RESOLVED", severity: "CRITICAL" },
        ],
        sessions: [{ status: "PLANNED" }, { status: "OPEN" }, { status: "CLOSED" }],
        attendanceEvents: [
          { outcome: "ADMITTED", reversed: false },
          { outcome: "ADMITTED", reversed: true },
          { outcome: "DENIED", reversed: false },
        ],
        reversals: [],
        workflowEvents: [],
        attendanceStatistics: [],
      },
      "/api/documents/uploads": [
        { verificationStatus: "PENDING" },
        { verificationStatus: "VERIFIED" },
        { verificationStatus: "REJECTED" },
      ],
      "/api/documents": [
        { status: "REQUESTED", retryAvailable: false },
        { status: "GENERATING", retryAvailable: false },
        { status: "STORED", retryAvailable: false },
        { status: "FAILED", retryAvailable: true },
      ],
      "/api/notifications": {
        templates: [{ status: "ACTIVE" }, { status: "DRAFT" }],
        consents: [],
        deliveryAttempts: [],
        requests: [
          { status: "QUEUED" },
          { status: "PROCESSING" },
          { status: "RETRY_SCHEDULED" },
          { status: "SENT" },
          { status: "FAILED" },
        ],
        eventInbox: [{ status: "DEAD" }, { status: "PROCESSED" }],
        providerCallbacks: [{ deliveryStatus: "DELIVERED" }, { deliveryStatus: "BOUNCED" }],
        inAppNotifications: [{ readAt: null }, { readAt: "2026-08-16T10:00:00Z" }],
      },
    };
    const request = vi.fn(async (path: string) => {
      if (!(path in responses)) throw new Error(`Unexpected request: ${path}`);
      return responses[path];
    });

    const overview = await loadOperationsOverview({ request });

    expect(overview).toHaveLength(11);
    expect(overview.every((module) => module.available)).toBe(true);
    expect(overview.every((module) => module.metrics.length === 4)).toBe(true);
    expect(overview.find((module) => module.key === "exams-timetabling")?.actions).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: "Unresolved incidents", value: 1 }),
      ]),
    );
    expect(overview.find((module) => module.key === "documents")?.actions).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: "Retry official records", value: 1 }),
      ]),
    );
    expect(overview.find((module) => module.key === "notifications")?.actions).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ label: "Dead integration events", value: 1 }),
      ]),
    );
    expect(overview.find((module) => module.key === "student-records")?.trend).toEqual([
      { label: "May", value: 1 },
      { label: "Jun", value: 2 },
      { label: "Jul", value: 1 },
    ]);
  });
});
