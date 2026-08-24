// Author: Tinashe K

import { flushPromises, shallowMount, type VueWrapper } from "@vue/test-utils";
import { reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { StructuredContentBlock } from "../../types/communications";

const request = vi.fn();
const showError = vi.fn();
const toastAdd = vi.fn();

vi.stubGlobal("reactive", reactive);
vi.stubGlobal("ref", ref);
vi.stubGlobal("useEmhareApi", () => ({
  request,
  errorMessage: () => "Upload failed",
}));
vi.stubGlobal("useToast", () => ({ add: toastAdd }));
vi.stubGlobal("useEmhareConfirm", () => ({ showError }));
vi.stubGlobal("usePublicCommunications", () => ({
  mediaUrl: (assetId: string) => `/api/communications/public/media/${assetId}`,
}));

const initialBlocks: StructuredContentBlock[] = [
  { type: "HEADING", text: "Welcome" },
  { type: "PARAGRAPH", text: "Public information" },
  { type: "LIST", ordered: false, items: ["First"] },
  { type: "QUOTE", text: "Learn", attribution: "UZ" },
  { type: "CALLOUT", title: "Remember", text: "Register" },
  { type: "IMAGE", mediaAssetId: "", alternativeText: "Campus", caption: "" },
  { type: "LINKS", links: [{ label: "UZ", url: "https://www.uz.ac.zw" }] },
];

async function mountEditor() {
  const Editor = (
    await import("../../components/domain/communications/EmhareStructuredContentEditor.vue")
  ).default;
  let wrapper: VueWrapper<any>;
  wrapper = shallowMount(Editor, {
    props: {
      modelValue: structuredClone(initialBlocks),
      "onUpdate:modelValue": (value: StructuredContentBlock[]) =>
        wrapper.setProps({ modelValue: value }),
    },
    global: {
      stubs: {
        UButton: true,
        UIcon: true,
        UInput: true,
        UTextarea: true,
        USwitch: true,
        UFileUpload: true,
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("EmhareStructuredContentEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    request.mockResolvedValue({
      id: "media-1",
      fileName: "article.webp",
      contentType: "image/webp",
      sizeBytes: 10,
      alternativeText: "Campus",
      publicUrl: "/api/communications/public/media/media-1",
    });
  });

  it("adds, reorders, edits, and removes article tiles without exposing JSON", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;

    for (const type of ["HEADING", "PARAGRAPH", "LIST", "QUOTE", "CALLOUT", "IMAGE", "LINKS"]) {
      editor.addBlock(type);
      await flushPromises();
    }
    expect((wrapper.props("modelValue") as StructuredContentBlock[]).length).toBe(14);

    editor.moveBlock(1, -1);
    await flushPromises();
    expect((wrapper.props("modelValue") as StructuredContentBlock[])[0]?.type).toBe("PARAGRAPH");
    editor.moveBlock(0, -1);
    editor.startDragging(0);
    editor.dropBlock(2);
    await flushPromises();
    expect((wrapper.props("modelValue") as StructuredContentBlock[])[2]?.type).toBe("PARAGRAPH");
    editor.dropBlock(2);

    const listBlock = (wrapper.props("modelValue") as StructuredContentBlock[]).find(
      (block) => block.type === "LIST",
    )!;
    editor.setListText(listBlock, "One\nTwo");
    expect(editor.listText(listBlock)).toBe("One\nTwo");
    editor.setTextValue(listBlock, "value", "note");
    expect(editor.textValue(listBlock, "note")).toBe("value");
    expect(editor.textValue(listBlock, "missing")).toBe("");

    const linkBlock = (wrapper.props("modelValue") as StructuredContentBlock[]).find(
      (block) => block.type === "LINKS",
    )!;
    editor.addLink(linkBlock);
    expect(editor.links(linkBlock)).toHaveLength(2);
    editor.removeLink(linkBlock, 0);
    expect(editor.links(linkBlock)).toHaveLength(1);
    editor.removeBlock(0);
    await flushPromises();
    expect((wrapper.props("modelValue") as StructuredContentBlock[]).length).toBe(13);
    expect(wrapper.text()).not.toContain("structuredContent");
  });

  it("uploads accessible block images and reports validation or request failures", async () => {
    const wrapper = await mountEditor();
    const editor = wrapper.vm as unknown as Record<string, any>;
    const imageBlock = (wrapper.props("modelValue") as StructuredContentBlock[])[5]!;

    await editor.uploadBlockImage(imageBlock, 5);
    expect(showError).toHaveBeenCalledWith("Image details required", expect.any(String));

    editor.imageFiles[5] = new File(["image"], "article.webp", { type: "image/webp" });
    await editor.uploadBlockImage(imageBlock, 5);
    expect(imageBlock.mediaAssetId).toBe("media-1");
    expect(editor.imageUrl(imageBlock)).toContain("media-1");
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ title: "Image uploaded" }));

    editor.imageFiles[5] = new File(["image"], "article.webp", { type: "image/webp" });
    request.mockRejectedValueOnce(new Error("offline"));
    await editor.uploadBlockImage(imageBlock, 5);
    expect(showError).toHaveBeenCalledWith("Image was not uploaded", "Upload failed");
  });
});
