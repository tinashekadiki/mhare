// Author: Tinashe K

import { flushPromises, mount, shallowMount } from "@vue/test-utils";
import { computed, defineComponent, onMounted, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const recordAuthenticatedRead = vi.fn();
const home = vi.fn();
const item = vi.fn();
const loadUser = vi.fn();
const syncCoreUser = vi.fn();
const login = vi.fn();
const navigateTo = vi.fn();

const publicItem = {
  publicationId: "publication-1",
  itemId: "item-1",
  versionId: "version-1",
  kind: "EVENT",
  slug: "open-day",
  title: "University Open Day",
  summary: "Meet the university community.",
  schemaVersion: 1,
  structuredContent: [{ type: "PARAGRAPH", text: "Applicants are welcome." }],
  heroMediaAssetId: "asset-1",
  publishFrom: "2026-08-17T08:00:00Z",
  pinned: false,
  featured: false,
  event: {
    startsAt: "2026-09-12T07:00:00Z",
    endsAt: "2026-09-12T13:00:00Z",
    timezone: "Africa/Harare",
    attendanceMode: "IN_PERSON",
    venueName: "Great Hall",
  },
};

vi.stubGlobal("computed", computed);
vi.stubGlobal("ref", ref);
vi.stubGlobal("onMounted", onMounted);
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("useRoute", () => ({ params: { slug: "open-day" }, query: {} }));
vi.stubGlobal("portalDestinationUrl", (kind: string) => `/${kind}`);
vi.stubGlobal("navigateTo", navigateTo);
vi.stubGlobal("sanitizePortalReturnPath", (value: string) => value);
vi.stubGlobal("inferPortalKind", () => "student");
vi.stubGlobal(
  "createError",
  (details: { statusMessage: string }) => new Error(details.statusMessage),
);
vi.stubGlobal("useEmhareAuth", () => ({ loadUser, syncCoreUser, login }));
vi.stubGlobal("usePublicCommunications", () => ({
  home,
  item,
  recordAuthenticatedRead,
  calendarUrl: (slug: string) => `/events/${slug}/calendar.ics`,
  mediaUrl: (assetId: string) => `/media/${assetId}`,
}));
vi.stubGlobal("useAsyncData", async (_key: string, handler: () => Promise<unknown>) => {
  try {
    return { data: ref(await handler()), pending: ref(false), error: ref(null), refresh: vi.fn() };
  } catch (error) {
    return { data: ref(null), pending: ref(false), error: ref(error), refresh: vi.fn() };
  }
});

const commonStubs = {
  EmharePublicGatewayHeader: true,
  EmharePublicGatewayFooter: {
    template: '<footer data-testid="institutional-public-footer">Institutional footer</footer>',
  },
  EmharePublicGatewaySlider: true,
  EmhareStructuredContent: { props: ["blocks"], template: "<div>Structured content</div>" },
  EmharePortalDestinationCard: true,
  EmharePublicContentCard: true,
  EmharePublicEventCard: true,
  UAlert: true,
  UButton: true,
  USkeleton: true,
  UIcon: true,
  NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
};

async function mountAsync(component: object, props: Record<string, unknown> = {}) {
  const Host = defineComponent({
    components: { Target: component },
    setup: () => ({ props }),
    template: '<Suspense><Target v-bind="props" /></Suspense>',
  });
  const wrapper = mount(Host, { global: { stubs: commonStubs } });
  await flushPromises();
  return wrapper;
}

describe("public content pages", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    home.mockResolvedValue({
      urgentNotices: [],
      importantLinks: [],
      featuredCampaign: null,
      upcomingEvents: [publicItem],
      latestNews: [],
    });
    item.mockResolvedValue(publicItem);
    recordAuthenticatedRead.mockResolvedValue(undefined);
    loadUser.mockResolvedValue(null);
    syncCoreUser.mockResolvedValue(undefined);
    login.mockResolvedValue(undefined);
    navigateTo.mockResolvedValue(undefined);
    localStorage.clear();
  });

  it("renders the public home and event detail while recording only authenticated reads", async () => {
    const PublicHome = (await import("../../../../apps/student-portal/pages/index.vue")).default;
    const homeWrapper = await mountAsync(PublicHome);
    expect(home).toHaveBeenCalled();
    expect(homeWrapper.text()).toContain("Choose your portal");
    expect(homeWrapper.find('[data-testid="institutional-public-footer"]').exists()).toBe(false);

    const PublicDetail = (
      await import("../../components/domain/communications/EmharePublicContentDetail.vue")
    ).default;
    const detailWrapper = await mountAsync(PublicDetail, {
      slug: "open-day",
      allowedKinds: ["EVENT"],
    });
    expect(item).toHaveBeenCalledWith("open-day");
    expect(detailWrapper.text()).toContain("University Open Day");
    expect(detailWrapper.text()).toContain("Download calendar event");
    expect(detailWrapper.find('[data-testid="institutional-public-footer"]').exists()).toBe(false);
    expect(recordAuthenticatedRead).toHaveBeenCalledWith("publication-1");
  });

  it("wires each canonical slug page to its public detail component", async () => {
    for (const path of [
      "../../../../apps/student-portal/pages/news/[slug].vue",
      "../../../../apps/student-portal/pages/notices/[slug].vue",
      "../../../../apps/student-portal/pages/events/[slug].vue",
      "../../../../apps/student-portal/pages/campaigns/[slug].vue",
    ]) {
      const Page = (await import(/* @vite-ignore */ path)).default;
      const wrapper = shallowMount(Page, {
        global: { stubs: { EmharePublicContentDetail: true } },
      });
      expect(wrapper.findComponent({ name: "EmharePublicContentDetail" }).exists()).toBe(true);
    }
  });

  it("renders unavailable and minimal content without event or media assumptions", async () => {
    const PublicDetail = (
      await import("../../components/domain/communications/EmharePublicContentDetail.vue")
    ).default;
    item.mockRejectedValueOnce(new Error("expired"));
    const unavailable = await mountAsync(PublicDetail, {
      slug: "expired-item",
      allowedKinds: ["NOTICE"],
    });
    expect(unavailable.text()).toContain("Return to eMhare");

    item.mockResolvedValueOnce({
      ...publicItem,
      kind: "NOTICE",
      event: undefined,
      heroMediaAssetId: undefined,
    });
    recordAuthenticatedRead.mockRejectedValueOnce(new Error("anonymous"));
    const minimal = await mountAsync(PublicDetail, {
      slug: "simple-notice",
      allowedKinds: ["NOTICE"],
    });
    expect(minimal.text()).toContain("University Open Day");
    expect(minimal.text()).not.toContain("Download calendar event");
  });
});

describe("authentication redirect page", () => {
  it("continues an existing session to the stored safe path", async () => {
    localStorage.setItem("emhare:returnTo", "/student/records");
    loadUser.mockResolvedValue({ access_token: "token" });
    const RedirectPage = (await import("../../pages/auth/redirect.vue")).default;
    mount(RedirectPage, { global: { stubs: commonStubs } });
    await flushPromises();
    expect(syncCoreUser).toHaveBeenCalled();
    expect(navigateTo).toHaveBeenCalledWith("/student/records", { replace: true });
  });

  it("starts login when there is no reusable session", async () => {
    localStorage.clear();
    loadUser.mockResolvedValue(null);
    const RedirectPage = (await import("../../pages/auth/redirect.vue")).default;
    mount(RedirectPage, { global: { stubs: commonStubs } });
    await flushPromises();
    expect(login).toHaveBeenCalledWith("/");
  });
});
