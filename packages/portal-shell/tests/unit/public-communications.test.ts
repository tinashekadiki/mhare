// Author: Tinashe K

import { mount } from "@vue/test-utils";
import { computed, nextTick, ref, watch } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EmharePublicContentCard from "../../components/domain/communications/EmharePublicContentCard.vue";
import EmharePublicEventCard from "../../components/domain/communications/EmharePublicEventCard.vue";
import EmhareStructuredContent from "../../components/domain/communications/EmhareStructuredContent.vue";
import EmharePortalDestinationCard from "../../components/public/EmharePortalDestinationCard.vue";
import EmhareProductBrand from "../../components/brand/EmhareProductBrand.vue";
import EmharePublicGatewayHeader from "../../components/public/EmharePublicGatewayHeader.vue";
import EmharePublicGatewaySlider from "../../components/public/EmharePublicGatewaySlider.vue";
import type { PublicCommunicationItem } from "../../types/communications";

const loadUser = vi.fn();
const fetch = vi.fn();
const accessToken = ref<string | null>(null);

vi.stubGlobal("computed", computed);
vi.stubGlobal("ref", ref);
vi.stubGlobal("watch", watch);
vi.stubGlobal("useEmhareAuth", () => ({ loadUser, accessToken }));
vi.stubGlobal("$fetch", fetch);
vi.stubGlobal("usePublicCommunications", () => ({
  mediaUrl: (id: string) => `/media/${id}`,
  calendarUrl: (slug: string) => `/events/${slug}/calendar.ics`,
}));

const globalComponents = {
  components: { EmhareProductBrand },
  stubs: {
    UIcon: { template: '<span class="icon" />' },
    NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
  },
};

function communicationItem(
  kind: PublicCommunicationItem["kind"],
  overrides: Partial<PublicCommunicationItem> = {},
): PublicCommunicationItem {
  return {
    publicationId: `publication-${kind}`,
    itemId: `item-${kind}`,
    versionId: `version-${kind}`,
    kind,
    slug: `${kind.toLowerCase()}-item`,
    title: `${kind} title`,
    summary: `${kind} summary`,
    schemaVersion: 1,
    structuredContent: [],
    publishFrom: "2026-08-17T08:00:00Z",
    pinned: false,
    featured: false,
    ...overrides,
  };
}

describe("public Communications components", () => {
  it("renders each canonical content route and external service action", () => {
    const expectations: Array<[PublicCommunicationItem, string]> = [
      [communicationItem("NEWS"), "/news/news-item"],
      [communicationItem("EVENT"), "/events/event-item"],
      [communicationItem("CAMPAIGN"), "/campaigns/campaign-item"],
      [communicationItem("NOTICE"), "/notices/notice-item"],
      [communicationItem("ALERT"), "/notices/alert-item"],
      [
        communicationItem("LINK", { externalUrl: "https://library.uz.ac.zw" }),
        "https://library.uz.ac.zw",
      ],
    ];

    for (const [item, route] of expectations) {
      const wrapper = mount(EmharePublicContentCard, {
        props: { item, compact: item.kind === "ALERT" },
        global: globalComponents,
      });
      expect(wrapper.get("a").attributes("href")).toBe(route);
      expect(wrapper.text()).toContain(item.kind === "LINK" ? "Open service" : "Read more");
    }

    const itemMedia = mount(EmharePublicContentCard, {
      props: {
        item: communicationItem("NEWS", { mediaUrl: "/media/news-1" }),
        fallbackImage: "/images/gateway/research-innovation.webp",
      },
      global: globalComponents,
    });
    expect(itemMedia.get("img").attributes("src")).toBe("/media/news-1");
    expect(itemMedia.get("img").attributes("alt")).toBe("NEWS title");

    const fallbackMedia = mount(EmharePublicContentCard, {
      props: {
        item: communicationItem("NEWS"),
        fallbackImage: "/images/gateway/library-learning.webp",
      },
      global: globalComponents,
    });
    expect(fallbackMedia.get("img").attributes("src")).toBe(
      "/images/gateway/library-learning.webp",
    );
    expect(fallbackMedia.get("img").attributes("alt")).toBe("");
  });

  it("renders event time, location, detail, and calendar URLs", () => {
    const event = communicationItem("EVENT", {
      event: {
        startsAt: "2026-09-12T07:00:00Z",
        endsAt: "2026-09-12T13:00:00Z",
        timezone: "Africa/Harare",
        attendanceMode: "IN_PERSON",
        venueName: "Great Hall",
      },
    });
    const wrapper = mount(EmharePublicEventCard, {
      props: { item: event },
      global: globalComponents,
    });
    expect(wrapper.text()).toContain("Great Hall");
    expect(wrapper.get("a[download]").attributes("href")).toBe("/events/event-item/calendar.ics");

    const noOccurrence = mount(EmharePublicEventCard, {
      props: { item: communicationItem("EVENT") },
      global: globalComponents,
    });
    expect(noOccurrence.text()).not.toContain("Great Hall");
  });

  it("renders every structured block and ignores malformed collection values", () => {
    const wrapper = mount(EmhareStructuredContent, {
      props: {
        blocks: [
          { type: "HEADING", text: "Welcome" },
          { type: "PARAGRAPH", text: "Public information" },
          { type: "LIST", ordered: true, items: ["First", 2] },
          { type: "LIST", ordered: false, items: ["Second"] },
          { type: "QUOTE", text: "Knowledge", attribution: "UZ" },
          { type: "QUOTE", text: "Learning without attribution" },
          { type: "CALLOUT", title: "Important", text: "Apply early" },
          { type: "CALLOUT", text: "A callout without a title" },
          {
            type: "IMAGE",
            mediaAssetId: "asset-1",
            alternativeText: "Students walking on campus",
            caption: "Campus",
          },
          {
            type: "IMAGE",
            mediaAssetId: "asset-2",
            alternativeText: "The university library",
          },
          {
            type: "LINKS",
            links: [{ label: "Library", url: "https://library.uz.ac.zw" }, null],
          },
          { type: "LIST", items: "invalid" },
          { type: "LINKS", links: "invalid" },
          { type: "IMAGE" },
        ],
      },
      global: globalComponents,
    });
    expect(wrapper.text()).toContain("Welcome");
    expect(wrapper.text()).toContain("First");
    expect(wrapper.text()).toContain("— UZ");
    expect(wrapper.get("img").attributes("alt")).toBe("Students walking on campus");
    expect(wrapper.get('a[href="https://library.uz.ac.zw"]').text()).toContain("Library");

    const structured = wrapper.vm as unknown as {
      text: (block: Record<string, unknown>, key?: string) => string;
      listItems: (block: Record<string, unknown>) => string[];
      links: (block: Record<string, unknown>) => Array<{ label: string; url: string }>;
      imageUrl: (block: Record<string, unknown>) => string;
    };
    expect(structured.text({ type: "PARAGRAPH", text: 42 })).toBe("");
    expect(structured.text({ type: "PARAGRAPH" }, "missing")).toBe("");
    expect(structured.listItems({ type: "LIST", items: null })).toEqual([]);
    expect(
      structured.links({
        type: "LINKS",
        links: [null, "bad", { label: "Missing URL" }, { url: "/missing-label" }],
      }),
    ).toEqual([]);
    expect(structured.imageUrl({ type: "IMAGE", mediaAssetId: 42 })).toBe("");
  });

  it("renders portal access and toggles accessible mobile navigation", async () => {
    const destination = mount(EmharePortalDestinationCard, {
      props: {
        title: "Students",
        href: "/student",
        icon: "i-lucide-graduation-cap",
        actionLabel: "Open student portal",
      },
      global: globalComponents,
    });
    expect(destination.get("a").attributes("href")).toBe("/student");

    const header = mount(EmharePublicGatewayHeader, { global: globalComponents });
    expect(header.get("[data-emhare-institution-logo]").attributes("src")).toBe(
      "/images/brand/university-of-zimbabwe-logo.png",
    );
    expect(header.get("[data-emhare-institution-logo]").attributes("alt")).toBe(
      "University of Zimbabwe",
    );
    expect(header.get("[data-emhare-product-logo]").attributes("src")).toBe(
      "/images/brand/emhare-wordmark-blue-gold.png",
    );
    expect(header.find('a[href="https://www.uz.ac.zw"]').exists()).toBe(false);
    expect(header.find('a[href="#contact"]').exists()).toBe(false);
    expect(header.get('a[href="/"]').classes()).toEqual(
      expect.arrayContaining(["flex-1", "justify-between"]),
    );
    expect(header.get("[data-emhare-product-brand]").classes()).toContain("ml-auto");
    const toggle = header.get('button[aria-label="Toggle public navigation"]');
    expect(toggle.attributes("aria-expanded")).toBe("false");
    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    await header.get('nav[aria-label="Mobile public gateway"] a').trigger("click");
    await nextTick();
    expect(toggle.attributes("aria-expanded")).toBe("false");
  });

  it("slides featured images only when the user requests it", async () => {
    const wrapper = mount(EmharePublicGatewaySlider, {
      props: {
        slides: [
          { image: "/one.webp", alternativeText: "First image", title: "First update" },
          { image: "/two.webp", alternativeText: "Second image", title: "Second update" },
        ],
      },
      global: globalComponents,
    });

    const track = wrapper.get("section > div");
    expect(track.attributes("style")).toContain("translateX(-0%)");
    await wrapper.get('button[aria-label="Show next image"]').trigger("click");
    expect(track.attributes("style")).toContain("translateX(-100%)");
    await wrapper.get('button[aria-label="Show previous image"]').trigger("click");
    expect(track.attributes("style")).toContain("translateX(-0%)");
    await wrapper.get("section").trigger("keydown", { key: "ArrowRight" });
    expect(track.attributes("style")).toContain("translateX(-100%)");
  });
});

describe("usePublicCommunications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    accessToken.value = null;
    loadUser.mockResolvedValue(null);
    fetch.mockResolvedValue({});
  });

  it("builds public URLs and skips anonymous read receipts", async () => {
    const { usePublicCommunications } = await import("../../composables/usePublicCommunications");
    const communications = usePublicCommunications();
    await communications.home();
    await communications.item("open day/2026");
    expect(communications.calendarUrl("open day/2026")).toContain("open%20day%2F2026/calendar.ics");
    expect(communications.mediaUrl("asset/1")).toContain("asset%2F1");
    await communications.recordAuthenticatedRead("publication-1");
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("records an authenticated read with its bearer token", async () => {
    const { usePublicCommunications } = await import("../../composables/usePublicCommunications");
    accessToken.value = "token-1";
    await usePublicCommunications().recordAuthenticatedRead("publication-1");
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/publications/publication-1/read"),
      expect.objectContaining({
        method: "PUT",
        headers: { Authorization: "Bearer token-1" },
      }),
    );
  });
});
