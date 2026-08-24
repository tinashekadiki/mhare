// Author: Tinashe K

import { flushPromises, shallowMount } from "@vue/test-utils";
import { computed, onMounted, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const request = vi.fn();
const showError = vi.fn();
const confirmAction = vi.fn();
const toastAdd = vi.fn();

vi.stubGlobal("computed", computed);
vi.stubGlobal("onMounted", onMounted);
vi.stubGlobal("reactive", reactive);
vi.stubGlobal("ref", ref);
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("useEmhareApi", () => ({
  request,
  errorMessage: (_error: unknown, fallback = "Request failed") => fallback,
}));
vi.stubGlobal("usePublicCommunications", () => ({
  mediaUrl: (assetId: string) => `/api/communications/public/media/${assetId}`,
}));
vi.stubGlobal("useToast", () => ({ add: toastAdd }));
vi.stubGlobal("useEmhareConfirm", () => ({ confirmAction, showError }));

async function mountEditor() {
  const Editor = (await import("../../pages/operations/communications/index.vue")).default;
  const wrapper = shallowMount(Editor, {
    global: {
      stubs: {
        UDashboardPanel: { template: "<div><slot name='header'/><slot name='body'/></div>" },
        UDashboardNavbar: { template: "<div><slot name='leading'/><slot name='right'/></div>" },
        UButton: { props: ["label"], template: "<button>{{ label }}</button>" },
        UInput: true,
        USelect: true,
        UTextarea: true,
        UFileUpload: true,
        USwitch: true,
        USkeleton: true,
        UFormField: { template: "<div><slot /></div>" },
        UAlert: true,
        UIcon: true,
        EmhareStatusPill: true,
        EmhareStructuredContent: true,
        EmhareStructuredContentEditor: true,
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("Communications editor release-gate branches", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    confirmAction.mockResolvedValue(true);
    request.mockImplementation(async (url: string) => {
      if (url.includes("/editorial/items?")) {
        return { items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 };
      }
      if (url.endsWith("/categories")) return [];
      return {};
    });
  });

  it("normalizes collection blocks and covers every public route and event fallback", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;
    editor.startDraft();

    editor.form.title = "University Update";
    for (const [kind, route] of [
      ["LINK", "Internal key: university-update"],
      ["EVENT", "/events/university-update"],
      ["CAMPAIGN", "/campaigns/university-update"],
      ["NOTICE", "/notices/university-update"],
      ["ALERT", "/notices/university-update"],
    ]) {
      editor.form.kind = kind;
      expect(editor.publicRoutePreview).toBe(route);
    }
    editor.form.slug = "governed-address";
    expect(editor.generatedSlug).toBe("governed-address");

    expect(editor.normalizeBlock({ type: "LIST", items: [" First ", "", 42, " Second "] })).toEqual(
      { type: "LIST", items: ["First", "Second"] },
    );
    expect(
      editor.normalizeBlock({
        type: "LINKS",
        links: [
          null,
          "invalid",
          { label: "", url: "/empty" },
          { label: "UZ", url: "https://www.uz.ac.zw" },
        ],
      }),
    ).toEqual({
      type: "LINKS",
      links: [{ label: "UZ", url: "https://www.uz.ac.zw" }],
    });

    editor.form.kind = "EVENT";
    editor.form.eventStartsAt = "2026-09-12T09:00";
    editor.form.eventEndsAt = "2026-09-12T12:00";
    editor.form.eventTimezone = "Africa/Harare";
    editor.form.attendanceMode = "HYBRID";
    editor.form.venueName = "";
    editor.form.address = "";
    editor.form.onlineUrl = "";
    expect(editor.eventBody()).toEqual(
      expect.objectContaining({ venueName: null, address: null, onlineUrl: null }),
    );
    editor.form.venueName = "Great Hall";
    editor.form.address = "Mount Pleasant";
    editor.form.onlineUrl = "https://meet.example.test";
    expect(editor.eventBody()).toEqual(
      expect.objectContaining({
        venueName: "Great Hall",
        address: "Mount Pleasant",
        onlineUrl: "https://meet.example.test",
      }),
    );
    expect(editor.statusTone("LIVE")).toBe("success");
    expect(editor.statusTone("REJECTED")).toBe("error");
    expect(editor.statusTone("DRAFT")).toBe("warning");

    const item = {
      itemId: "item-1",
      versionId: "version-1",
      kind: "EVENT",
      slug: "university-update",
      title: "University Update",
      summary: "Summary",
      workflowStatus: "DRAFT",
      versionNumber: 1,
      expectedVersion: 0,
      authoredByUserId: "author-1",
      updatedAt: "2026-08-17T08:00:00Z",
    };
    request.mockResolvedValueOnce({
      item,
      categoryId: null,
      structuredContent: [],
      heroMediaAssetId: null,
      externalUrl: null,
      event: null,
    });
    await editor.openItem(item);
    expect(editor.form).toEqual(
      expect.objectContaining({
        eventStartsAt: "",
        eventEndsAt: "",
        eventTimezone: "Africa/Harare",
        attendanceMode: "IN_PERSON",
        venueName: "",
      }),
    );

    request.mockResolvedValueOnce({
      item,
      categoryId: "category-1",
      structuredContent: [{ type: "PARAGRAPH", text: "Event details" }],
      heroMediaAssetId: "media-1",
      externalUrl: "https://example.test",
      event: {
        startsAt: "2026-09-12T07:00:00Z",
        endsAt: "2026-09-12T10:00:00Z",
        timezone: "Africa/Harare",
        attendanceMode: "HYBRID",
        venueName: "Great Hall",
        address: "Mount Pleasant",
        onlineUrl: "https://meet.example.test",
      },
    });
    await editor.openItem(item);
    expect(editor.form).toEqual(
      expect.objectContaining({
        eventTimezone: "Africa/Harare",
        attendanceMode: "HYBRID",
        venueName: "Great Hall",
      }),
    );
  });

  it("keeps cover uploads, filters, and editor closing safe across empty states", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;
    editor.startDraft();

    await editor.uploadHeroImage();
    editor.heroImageFile = new File(["image"], "cover.webp", { type: "image/webp" });
    editor.heroAlternativeText = "";
    await editor.uploadHeroImage();
    expect(showError).toHaveBeenCalledWith("Image details required", expect.any(String));

    editor.heroAlternativeText = "Students learning";
    request.mockRejectedValueOnce(new Error("offline"));
    await editor.uploadHeroImage();
    expect(showError).toHaveBeenCalledWith("Cover image was not uploaded", "Request failed");

    editor.search = "  ";
    editor.kindFilter = undefined;
    await editor.applyFilters();
    expect(editor.currentPage).toBe(1);
    editor.closeEditor();
    expect(editor.creating).toBe(false);
    expect(editor.selected).toBeNull();
  });

  it("renders queue, draft, review, approved, live, and withdrawn template states", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;
    const baseItem = {
      itemId: "item-1",
      versionId: "version-1",
      kind: "NEWS",
      slug: "update",
      title: "Update",
      summary: "Summary",
      workflowStatus: "DRAFT",
      versionNumber: 1,
      expectedVersion: 0,
      authoredByUserId: "author-1",
      updatedAt: "2026-08-17T08:00:00Z",
    };

    editor.queue = {
      items: [baseItem, { ...baseItem, itemId: "item-2", publicationStatus: "LIVE" }],
      page: 0,
      size: 10,
      totalItems: 2,
      totalPages: 2,
    };
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("Page 1 of 2");

    for (const workflowStatus of ["DRAFT", "REJECTED", "IN_REVIEW", "APPROVED"]) {
      editor.selected = {
        item: { ...baseItem, workflowStatus },
        structuredContent: [],
      };
      await wrapper.vm.$nextTick();
      expect(wrapper.text()).toContain("Content editor");
    }
    editor.selected = {
      item: { ...baseItem, workflowStatus: "APPROVED", publicationId: "publication-1" },
      structuredContent: [],
    };
    editor.form.heroMediaAssetId = "media-1";
    editor.heroAlternativeText = "Gateway image";
    await wrapper.vm.$nextTick();
    expect(wrapper.find('img[src*="media-1"]').exists()).toBe(true);

    editor.heroAlternativeText = "";
    editor.form.kind = "LINK";
    editor.form.externalUrl = "https://example.test";
    editor.form.structuredContent = [];
    await wrapper.vm.$nextTick();
    expect(editor.publicRoutePreview).toContain("Internal key:");
    expect(wrapper.text()).toContain("Add a content block to preview the article body.");

    editor.form.kind = "EVENT";
    editor.form.structuredContent = [{ type: "PARAGRAPH", text: "Event details" }];
    await wrapper.vm.$nextTick();
    expect(editor.publicRoutePreview).toContain("/events/");
    expect(wrapper.text()).not.toContain("Add a content block to preview the article body.");
  });
});
