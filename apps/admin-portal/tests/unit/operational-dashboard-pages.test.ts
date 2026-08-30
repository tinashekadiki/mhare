// Author: Tinashe K

import { computed, defineComponent, nextTick, onMounted, reactive, ref, watch } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OperationalDashboardSnapshot } from "../../../../packages/portal-shell/utils/operational-dashboard";

const dashboardLoaderMocks = vi.hoisted(() => ({
  loadOperationalDashboard: vi.fn(),
  loadOperationsOverview: vi.fn(),
}));

vi.mock("@emhare/portal-shell/utils/operational-dashboard", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../../packages/portal-shell/utils/operational-dashboard")
    >();
  return {
    ...actual,
    loadOperationalDashboard: dashboardLoaderMocks.loadOperationalDashboard,
    loadOperationsOverview: dashboardLoaderMocks.loadOperationsOverview,
  };
});

Object.assign(globalThis, { computed, onMounted, reactive, ref, watch });
vi.stubGlobal("defineOptions", vi.fn());
vi.stubGlobal("definePageMeta", vi.fn());
const selectedAcademicPeriodId = ref<string | null>("period-2026-7s1");
vi.stubGlobal("useAcademicPeriodContext", () => ({ selectedAcademicPeriodId }));

const financeSnapshot: OperationalDashboardSnapshot = {
  key: "finance",
  label: "Finance",
  shortLabel: "Finance",
  description: "Control governed fees, billing and cash collection.",
  icon: "i-lucide-landmark",
  dashboardPath: "/operations/dashboard/finance",
  workspacePath: "/operations/finance",
  available: true,
  generatedAt: "2026-08-16T08:30:00Z",
  scopeNote: "USD is the base currency; unrated ZWG transactions remain explicit.",
  metrics: [
    {
      label: "Posted invoices",
      value: 4,
      hint: "Governed invoice records",
      icon: "i-lucide-receipt",
      tone: "success",
    },
    {
      label: "Student accounts",
      value: 3,
      hint: "Finance-owned accounts",
      icon: "i-lucide-wallet-cards",
      tone: "primary",
    },
  ],
  actions: [
    {
      label: "Unrated payments",
      value: 2,
      description: "Payments awaiting an effective exchange rate.",
      icon: "i-lucide-scale",
      to: "/operations/finance-collections",
      tone: "warning",
    },
  ],
  distribution: [
    { label: "Reconciled", value: 4 },
    { label: "Pending", value: 0 },
  ],
  links: [
    {
      label: "Cash collections",
      description: "Reconcile receipts and payments.",
      icon: "i-lucide-banknote",
      to: "/operations/finance-collections",
    },
  ],
};

const SlotStub = defineComponent({
  setup(_, { slots }) {
    return () => [
      slots.header?.(),
      slots.leading?.(),
      slots.left?.(),
      slots.right?.(),
      slots.body?.(),
      slots.actions?.(),
      slots.default?.(),
      slots.footer?.(),
    ];
  },
});

const CardStub = defineComponent({
  inheritAttrs: false,
  template: '<article v-bind="$attrs"><slot /><slot name="footer" /></article>',
});

const NavbarStub = defineComponent({
  props: ["title"],
  template:
    '<header><h1>{{ title }}</h1><slot name="leading" /><slot name="right" /><slot /></header>',
});

const ContainerStub = defineComponent({
  inheritAttrs: false,
  template: '<main v-bind="$attrs"><slot /></main>',
});

const ButtonStub = defineComponent({
  inheritAttrs: false,
  props: ["label", "to", "loading"],
  emits: ["click"],
  template:
    '<a v-if="to" v-bind="$attrs" :href="to">{{ label }}<slot /></a><button v-else v-bind="$attrs" @click="$emit(\'click\')">{{ label }}<slot /></button>',
});

const AlertStub = defineComponent({
  props: ["title", "description"],
  template:
    '<section class="alert-stub"><h2>{{ title }}</h2><p>{{ description }}</p><slot name="actions" /></section>',
});

const BadgeStub = defineComponent({
  props: ["label"],
  template: '<span class="badge-stub">{{ label }}<slot /></span>',
});

const KpiStub = defineComponent({
  props: ["label", "value", "hint", "tone"],
  template:
    '<article class="kpi-stub"><h3>{{ label }}</h3><strong>{{ value }}</strong><p>{{ hint }}</p><span>{{ tone }}</span></article>',
});

const LinkStub = defineComponent({
  inheritAttrs: false,
  props: ["to"],
  template: '<a v-bind="$attrs" :href="to"><slot /></a>',
});

const OperationalDashboardStub = defineComponent({
  props: ["snapshot"],
  template: '<section class="operational-dashboard-stub">{{ snapshot.label }} dashboard</section>',
});

const commonStubs = {
  UDashboardPanel: SlotStub,
  UDashboardNavbar: NavbarStub,
  UDashboardToolbar: SlotStub,
  UDashboardSidebarCollapse: true,
  UContainer: ContainerStub,
  UCard: CardStub,
  UButton: ButtonStub,
  UAlert: AlertStub,
  UBadge: BadgeStub,
  UIcon: true,
  USkeleton: defineComponent({ template: '<div class="skeleton-stub" />' }),
  EmhareKpiCard: KpiStub,
  NuxtLink: LinkStub,
  EmhareOperationalDashboard: OperationalDashboardStub,
};

describe("Main Operations dashboard page", () => {
  const route = reactive({ query: { access: "restricted" } as Record<string, string> });

  beforeEach(() => {
    vi.clearAllMocks();
    selectedAcademicPeriodId.value = "period-2026-7s1";
    route.query = { access: "restricted" };
    vi.stubGlobal("useRoute", () => route);
    vi.stubGlobal("useEmhareApi", () => ({ errorMessage: vi.fn() }));
    dashboardLoaderMocks.loadOperationsOverview.mockImplementation(
      async (_api: unknown, keys: string[]) => {
        const key = keys[0];
        return [
          {
            ...financeSnapshot,
            key,
            label: key === "finance" ? "Finance" : `Live ${key}`,
            dashboardPath:
              key === "admissions"
                ? "/operations/admissions-dashboard"
                : `/operations/dashboard/${key}`,
            available: key !== "notifications",
            errorMessage: key === "notifications" ? "Notifications cannot be read." : undefined,
            summaryMetrics:
              key === "academic-setup"
                ? [
                    { label: "Faculty", value: 3 },
                    { label: "Department", value: 5 },
                  ]
                : undefined,
            trend: key === "student-records" ? [{ label: "Aug", value: 23 }] : undefined,
          },
        ];
      },
    );
  });

  it("renders all module destinations progressively and identifies unavailable services explicitly", async () => {
    const DashboardPage = (await import("../../pages/operations/index.vue")).default;
    const wrapper = mount(DashboardPage, { global: { stubs: commonStubs } });

    expect(wrapper.findAll(".skeleton-stub").length).toBeGreaterThan(0);
    await flushPromises();

    expect(wrapper.text()).toContain("Access restricted");
    expect(wrapper.text()).not.toContain("Cross-module control");
    expect(wrapper.text()).not.toContain("Operational pulse");
    expect(wrapper.text()).not.toContain("University operations");
    expect(wrapper.text()).not.toContain("Every count comes from its owning service");
    expect(wrapper.text()).not.toContain("Owned service evidence");
    expect(wrapper.text()).not.toContain("Control governed fees, billing and cash collection.");
    expect(wrapper.findAll("h1")).toHaveLength(1);
    expect(wrapper.get("h1").text()).toBe("Operations");
    expect(wrapper.findAll('[data-testid^="operations-module-"]')).toHaveLength(11);
    expect(wrapper.findAll('[data-testid="operations-analytics-widget"]')).toHaveLength(12);
    expect(wrapper.get('[data-testid="operations-executive-overview"]').text()).toContain(
      "Institution pulse",
    );
    expect(wrapper.get('[data-testid="document-verification-donut"]')).toBeDefined();
    expect(wrapper.get('[data-testid="registration-trend-chart"]')).toBeDefined();
    expect(
      wrapper.get('[data-testid="registration-trend-chart"] polyline').attributes("points"),
    ).toBe("10,20.0 230,20.0");
    expect(wrapper.get('[data-testid="academic-capacity-bars"]')).toBeDefined();
    expect(wrapper.get('[data-testid="admissions-conversion-funnel"]')).toBeDefined();
    const academicUnitMetrics = wrapper
      .get('[data-testid="operations-module-academic-setup"]')
      .findAll('[data-testid="operations-summary-metric"]');
    expect(
      academicUnitMetrics.map((metric) => ({
        label: metric.get('[data-testid="operations-summary-label"]').text(),
        value: metric.get('[data-testid="operations-summary-value"]').text(),
      })),
    ).toEqual([
      { label: "Faculty", value: "3" },
      { label: "Department", value: "5" },
    ]);
    expect(wrapper.get('[data-testid="operations-dashboard-content"]').classes()).toContain(
      "max-w-none",
    );
    expect(wrapper.text()).toContain("Notifications cannot be read.");
    expect(wrapper.text()).toContain("Unavailable");
    expect(wrapper.text()).toContain("Updated");
    expect(
      wrapper
        .findAll("a")
        .some((link) => link.attributes("href") === "/operations/admissions-dashboard"),
    ).toBe(true);
  });

  it("refreshes on demand and hides the restricted-access notice outside that route state", async () => {
    route.query = {};
    const DashboardPage = (await import("../../pages/operations/index.vue")).default;
    const wrapper = mount(DashboardPage, { global: { stubs: commonStubs } });
    await flushPromises();

    expect(wrapper.text()).not.toContain("Access restricted");
    expect(dashboardLoaderMocks.loadOperationsOverview).toHaveBeenCalledTimes(11);
    await wrapper.get('[aria-label="Refresh Operations dashboard"]').trigger("click");
    await flushPromises();
    expect(dashboardLoaderMocks.loadOperationsOverview).toHaveBeenCalledTimes(22);
  });

  it("reloads period-scoped metrics when the selected academic period changes", async () => {
    const DashboardPage = (await import("../../pages/operations/index.vue")).default;
    const wrapper = mount(DashboardPage, { global: { stubs: commonStubs } });
    await flushPromises();
    dashboardLoaderMocks.loadOperationsOverview.mockClear();

    selectedAcademicPeriodId.value = "period-2026-7s2";
    await nextTick();
    await flushPromises();

    expect(dashboardLoaderMocks.loadOperationsOverview).toHaveBeenCalled();
    expect(
      dashboardLoaderMocks.loadOperationsOverview.mock.calls.every(
        ([, , scope]) => scope.academicPeriodId === "period-2026-7s2",
      ),
    ).toBe(true);
    wrapper.unmount();
  });
});

describe("Operational module dashboard page", () => {
  let route: { params: { module: string } };
  const navigateTo = vi.fn(async () => undefined);
  const apiErrorMessage = vi.fn((_error: unknown, fallback: string) => fallback);

  beforeEach(() => {
    vi.clearAllMocks();
    selectedAcademicPeriodId.value = "period-2026-7s1";
    route = reactive({ params: { module: "finance" } });
    vi.stubGlobal("useRoute", () => route);
    vi.stubGlobal("useEmhareApi", () => ({ errorMessage: apiErrorMessage }));
    vi.stubGlobal("navigateTo", navigateTo);
    vi.stubGlobal("createError", (details: { statusCode: number; statusMessage: string }) =>
      Object.assign(new Error(details.statusMessage), details),
    );
    dashboardLoaderMocks.loadOperationalDashboard.mockResolvedValue(financeSnapshot);
  });

  it("loads a governed module snapshot and exposes its full-width workspace", async () => {
    const DashboardPage = (await import("../../pages/operations/dashboard/[module].vue")).default;
    const wrapper = mount(DashboardPage, { global: { stubs: commonStubs } });

    expect(wrapper.findAll(".skeleton-stub")).toHaveLength(8);
    await flushPromises();

    expect(dashboardLoaderMocks.loadOperationalDashboard).toHaveBeenCalledWith(
      expect.anything(),
      "finance",
      { academicPeriodId: "period-2026-7s1" },
    );
    expect(wrapper.text()).toContain("Finance overview");
    expect(wrapper.text()).toContain("Finance dashboard");
    expect(wrapper.text()).toContain("Updated");
    expect(wrapper.get('[data-testid="operational-dashboard-finance"]').classes()).toContain(
      "max-w-none",
    );
    expect(wrapper.text()).toContain("Open workspace");
  });

  it("shows a recoverable service error and retries the same dashboard", async () => {
    dashboardLoaderMocks.loadOperationalDashboard
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(financeSnapshot);
    const DashboardPage = (await import("../../pages/operations/dashboard/[module].vue")).default;
    const wrapper = mount(DashboardPage, { global: { stubs: commonStubs } });
    await flushPromises();

    expect(wrapper.text()).toContain("Finance overview unavailable");
    expect(wrapper.text()).toContain("Finance overview could not be loaded.");
    expect(wrapper.text()).toContain("Not yet refreshed");
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("Finance dashboard");
    expect(dashboardLoaderMocks.loadOperationalDashboard).toHaveBeenCalledTimes(2);
  });

  it("redirects the Admissions module to its richer dedicated dashboard", async () => {
    route.params.module = "admissions";
    const DashboardPage = (await import("../../pages/operations/dashboard/[module].vue")).default;
    mount(DashboardPage, { global: { stubs: commonStubs } });
    await flushPromises();

    expect(navigateTo).toHaveBeenCalledWith("/operations/admissions-dashboard", { replace: true });
    expect(dashboardLoaderMocks.loadOperationalDashboard).not.toHaveBeenCalled();
  });

  it("reloads for a valid route change and ignores an invalid watched key", async () => {
    const DashboardPage = (await import("../../pages/operations/dashboard/[module].vue")).default;
    mount(DashboardPage, { global: { stubs: commonStubs } });
    await flushPromises();

    route.params.module = "documents";
    await nextTick();
    await flushPromises();
    expect(dashboardLoaderMocks.loadOperationalDashboard).toHaveBeenLastCalledWith(
      expect.anything(),
      "documents",
      { academicPeriodId: "period-2026-7s1" },
    );

    route.params.module = "not-a-module";
    await nextTick();
    await flushPromises();
    expect(dashboardLoaderMocks.loadOperationalDashboard).toHaveBeenCalledTimes(2);
  });

  it("rejects an unknown dashboard key with a 404", async () => {
    route.params.module = "missing";
    const DashboardPage = (await import("../../pages/operations/dashboard/[module].vue")).default;

    expect(() => mount(DashboardPage, { global: { stubs: commonStubs } })).toThrow(
      "Operational dashboard not found",
    );
  });
});

describe("Shared operational dashboard presentation", () => {
  it("renders metrics, queues, governed state bars and workspace destinations", async () => {
    const Dashboard = (
      await import("../../../../packages/portal-shell/components/domain/operations/EmhareOperationalDashboard.vue")
    ).default;
    const wrapper = mount(Dashboard, {
      props: { snapshot: financeSnapshot },
      global: { stubs: commonStubs },
    });

    expect(wrapper.text()).toContain("Module snapshot");
    expect(wrapper.text()).toContain("Operational workload");
    expect(wrapper.text()).toContain("Current position");
    expect(wrapper.text()).toContain("Continue the work");
    expect(wrapper.text()).toContain("Unrated payments");
    expect(wrapper.text()).toContain("Reconciled");
    expect(wrapper.findAll("[style]").map((element) => element.attributes("style"))).toEqual(
      expect.arrayContaining([expect.stringContaining("100%"), expect.stringContaining("0%")]),
    );
    expect(wrapper.get("a").attributes("href")).toBe("/operations/finance-collections");
  });

  it("renders the governed empty distribution state safely", async () => {
    const Dashboard = (
      await import("../../../../packages/portal-shell/components/domain/operations/EmhareOperationalDashboard.vue")
    ).default;
    const wrapper = mount(Dashboard, {
      props: { snapshot: { ...financeSnapshot, distribution: [] } },
      global: { stubs: commonStubs },
    });

    expect(wrapper.text()).toContain("No governed status records are present yet.");
  });
});
