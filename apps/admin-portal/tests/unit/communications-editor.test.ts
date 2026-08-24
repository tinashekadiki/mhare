// Author: Tinashe K

import { flushPromises, shallowMount } from "@vue/test-utils";
import { computed, onMounted, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  EditorialCommunicationDetail,
  EditorialCommunicationItem,
} from "@emhare/portal-shell/types/communications";

const request = vi.fn();
const errorMessage = vi.fn((_error: unknown, fallback = "Request failed") => fallback);
const toastAdd = vi.fn();
const confirmAction = vi.fn();
const showError = vi.fn();

vi.stubGlobal("computed", computed);
vi.stubGlobal("reactive", reactive);
vi.stubGlobal("ref", ref);
vi.stubGlobal("onMounted", onMounted);
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage }));
vi.stubGlobal("usePublicCommunications", () => ({
  mediaUrl: (assetId: string) => `http://localhost:8080/api/communications/public/media/${assetId}`,
}));
vi.stubGlobal("useToast", () => ({ add: toastAdd }));
vi.stubGlobal("useEmhareConfirm", () => ({ confirmAction, showError }));

const item: EditorialCommunicationItem = {
  itemId: "item-1",
  versionId: "version-1",
  kind: "EVENT",
  slug: "open-day",
  title: "Open Day",
  summary: "Meet the university community.",
  workflowStatus: "DRAFT",
  versionNumber: 1,
  expectedVersion: 0,
  authoredByUserId: "author-1",
  updatedAt: "2026-08-17T08:00:00Z",
};

function detail(overrides: Partial<EditorialCommunicationItem> = {}): EditorialCommunicationDetail {
  return {
    item: { ...item, ...overrides },
    structuredContent: [{ type: "PARAGRAPH", text: "Welcome" }],
    event: {
      startsAt: "2026-09-12T07:00:00Z",
      endsAt: "2026-09-12T13:00:00Z",
      timezone: "Africa/Harare",
      attendanceMode: "IN_PERSON",
      venueName: "Great Hall",
    },
  };
}

async function mountEditor() {
  const CommunicationsEditor = (await import("../../pages/operations/communications/index.vue"))
    .default;
  const wrapper = shallowMount(CommunicationsEditor, {
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
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("Communications editorial workspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    confirmAction.mockResolvedValue(true);
    showError.mockResolvedValue(undefined);
    request.mockImplementation(async (url: string) => {
      if (url.includes("?")) {
        return { items: [item], page: 0, size: 50, totalItems: 1, totalPages: 1 };
      }
      if (url.endsWith("/categories")) {
        return [
          {
            id: "category-1",
            code: "EVENTS",
            name: "Events",
            displayOrder: 1,
            active: true,
            expectedVersion: 0,
          },
        ];
      }
      if (url.endsWith("/items/item-1")) return detail();
      if (url.endsWith("/media")) {
        return {
          id: "media-1",
          fileName: "cover.webp",
          contentType: "image/webp",
          sizeBytes: 100,
          alternativeText: "Students in a laboratory",
          publicUrl: "/api/communications/public/media/media-1",
        };
      }
      return item;
    });
  });

  it("loads, creates, edits, reviews, publishes, and withdraws governed content", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;
    expect(request).toHaveBeenCalledWith(
      expect.stringContaining("/editorial/items?page=0&size=10"),
    );

    editor.startDraft();
    expect(editor.creating).toBe(true);
    editor.form.title = "New Event";
    expect(editor.publicRoutePreview).toBe("/news/new-event");
    editor.form.summary = "A public event.";
    editor.form.kind = "EVENT";
    editor.form.eventStartsAt = "2026-09-12T09:00";
    editor.form.eventEndsAt = "2026-09-12T12:00";
    editor.form.venueName = "Great Hall";
    await editor.save();
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: "Draft saved" }));
    expect(request).toHaveBeenCalledWith(
      "/api/communications/editorial/items",
      expect.objectContaining({
        method: "POST",
        body: expect.objectContaining({ slug: null }),
      }),
    );

    await editor.openItem(item);
    expect(editor.form.title).toBe("Open Day");
    editor.form.title = "Updated Open Day";
    await editor.save();
    expect(request).toHaveBeenCalledWith(
      "/api/communications/editorial/versions/version-1",
      expect.objectContaining({ method: "PUT" }),
    );

    editor.selected = detail({ workflowStatus: "DRAFT" });
    await editor.transition("submit");
    editor.selected = detail({ workflowStatus: "IN_REVIEW", expectedVersion: 1 });
    await editor.transition("approve");
    editor.decisionReason = "Needs a clearer source";
    await editor.transition("reject");
    expect(confirmAction).toHaveBeenCalledTimes(3);

    editor.selected = detail({ workflowStatus: "APPROVED", expectedVersion: 2 });
    editor.publishFrom = "2026-08-17T10:00";
    editor.publishUntil = "2026-08-18T10:00";
    await editor.schedule();
    expect(request).toHaveBeenCalledWith(
      expect.stringContaining("/publications"),
      expect.objectContaining({ method: "POST" }),
    );

    editor.selected = detail({
      workflowStatus: "APPROVED",
      publicationId: "publication-1",
      publicationStatus: "LIVE",
      publicationExpectedVersion: 0,
    });
    editor.decisionReason = "Event cancelled";
    await editor.withdraw();
    expect(request).toHaveBeenCalledWith(
      expect.stringContaining("publication-1/withdraw"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("keeps invalid and cancelled actions safe and reports request failures", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;

    await editor.transition("submit");
    editor.selected = detail({ workflowStatus: "IN_REVIEW" });
    editor.decisionReason = "";
    await editor.transition("reject");
    expect(showError).toHaveBeenCalledWith("Rejection reason required", expect.any(String));

    confirmAction.mockResolvedValueOnce(false);
    await editor.transition("approve");
    editor.publishFrom = "";
    await editor.schedule();
    editor.selected = detail({ publicationId: "publication-1", publicationStatus: "LIVE" });
    await editor.withdraw();
    expect(showError).toHaveBeenCalledWith("Withdrawal reason required", expect.any(String));

    request.mockRejectedValueOnce(new Error("offline"));
    await editor.load();
    expect(showError).toHaveBeenCalledWith("Communications could not be loaded", "Request failed");

    editor.startDraft();
    request.mockRejectedValueOnce(new Error("invalid article"));
    await editor.save();
    expect(showError).toHaveBeenCalledWith("Draft was not saved", expect.any(String));

    expect(editor.statusTone("APPROVED")).toBe("success");
    expect(editor.statusTone("WITHDRAWN")).toBe("error");
    expect(editor.statusTone("IN_REVIEW")).toBe("warning");
  });

  it("covers filtered queues, preview fallbacks, non-event drafts, and failed actions", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;

    editor.search = " open day ";
    editor.kindFilter = "EVENT";
    await editor.load();
    expect(request).toHaveBeenCalledWith(expect.stringContaining("query=open+day&kind=EVENT"));

    editor.categories = [
      { id: "active", name: "Active", active: true },
      { id: "inactive", name: "Inactive", active: false },
    ];
    expect(editor.categoryItems).toEqual([{ label: "Active", value: "active" }]);
    editor.form.structuredContent = [];
    expect(editor.parsedBlocks).toEqual([]);
    editor.form.structuredContent = [{ type: "PARAGRAPH", text: "Visible text" }];
    expect(editor.parsedBlocks).toEqual([{ type: "PARAGRAPH", text: "Visible text" }]);

    request.mockRejectedValueOnce(new Error("detail failed"));
    await editor.openItem(item);
    expect(showError).toHaveBeenCalledWith("Content item could not be opened", "Request failed");

    editor.creating = false;
    editor.selected = null;
    editor.form.kind = "NEWS";
    await editor.save();
    editor.startDraft();
    editor.form.kind = "NEWS";
    editor.form.externalUrl = "";
    expect(editor.eventBody()).toBeNull();

    editor.selected = detail({ workflowStatus: "IN_REVIEW" });
    request.mockRejectedValueOnce(new Error("transition failed"));
    await editor.transition("approve");
    expect(showError).toHaveBeenCalledWith("Workflow action failed", "Request failed");

    editor.selected = detail({ workflowStatus: "APPROVED" });
    editor.publishFrom = "2026-08-17T10:00";
    editor.publishUntil = "";
    confirmAction.mockResolvedValueOnce(false);
    await editor.schedule();
    request.mockRejectedValueOnce(new Error("schedule failed"));
    await editor.schedule();
    expect(showError).toHaveBeenCalledWith("Publication could not be scheduled", "Request failed");

    editor.selected = detail({
      workflowStatus: "APPROVED",
      publicationId: "publication-1",
      publicationStatus: "LIVE",
      publicationExpectedVersion: undefined,
    });
    editor.decisionReason = " Superseded ";
    confirmAction.mockResolvedValueOnce(false);
    await editor.withdraw();
    request.mockRejectedValueOnce(new Error("withdraw failed"));
    await editor.withdraw();
    expect(showError).toHaveBeenCalledWith("Publication could not be withdrawn", "Request failed");
  });

  it("uploads an accessible cover image and pages the article list", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;

    editor.startDraft();
    editor.heroImageFile = new File(["image"], "cover.webp", { type: "image/webp" });
    editor.heroAlternativeText = "Students in a laboratory";
    await editor.uploadHeroImage();
    expect(editor.form.heroMediaAssetId).toBe("media-1");
    expect(request).toHaveBeenCalledWith(
      "/api/communications/editorial/media",
      expect.objectContaining({ method: "POST", body: expect.any(FormData) }),
    );

    await editor.changePage(2);
    expect(request).toHaveBeenCalledWith(expect.stringContaining("page=1&size=10"));
  });
});
