<script setup lang="ts">
import type {
  CommunicationMediaAsset,
  StructuredContentBlock,
} from "@emhare/portal-shell/types/communications";

// Author: Tinashe K
const blocks = defineModel<StructuredContentBlock[]>({ required: true });
const props = withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const communications = usePublicCommunications();
const draggedIndex = ref<number | null>(null);
const uploadingBlockIndex = ref<number | null>(null);
const imageFiles = reactive<Record<number, File | null>>({});

const blockChoices = [
  { type: "HEADING", label: "Heading", icon: "i-lucide-heading-2" },
  { type: "PARAGRAPH", label: "Text", icon: "i-lucide-align-left" },
  { type: "LIST", label: "List", icon: "i-lucide-list" },
  { type: "QUOTE", label: "Quote", icon: "i-lucide-quote" },
  { type: "CALLOUT", label: "Callout", icon: "i-lucide-message-square-warning" },
  { type: "IMAGE", label: "Image", icon: "i-lucide-image" },
  { type: "LINKS", label: "Links", icon: "i-lucide-link" },
] as const;

function addBlock(type: StructuredContentBlock["type"]) {
  const defaults: Record<StructuredContentBlock["type"], StructuredContentBlock> = {
    HEADING: { type, text: "" },
    PARAGRAPH: { type, text: "" },
    LIST: { type, ordered: false, items: [""] },
    QUOTE: { type, text: "", attribution: "" },
    CALLOUT: { type, title: "", text: "" },
    IMAGE: { type, mediaAssetId: "", alternativeText: "", caption: "" },
    LINKS: { type, links: [{ label: "", url: "" }] },
  };
  blocks.value = [...blocks.value, defaults[type]];
}

function removeBlock(index: number) {
  blocks.value = blocks.value.filter((_, blockIndex) => blockIndex !== index);
}

function moveBlock(index: number, direction: -1 | 1) {
  const destination = index + direction;
  if (destination < 0 || destination >= blocks.value.length) return;
  const reordered = [...blocks.value];
  [reordered[index], reordered[destination]] = [reordered[destination]!, reordered[index]!];
  blocks.value = reordered;
}

function startDragging(index: number) {
  if (!props.disabled) draggedIndex.value = index;
}

function dropBlock(destinationIndex: number) {
  const sourceIndex = draggedIndex.value;
  draggedIndex.value = null;
  if (sourceIndex === null || sourceIndex === destinationIndex) return;
  const reordered = [...blocks.value];
  const [movedBlock] = reordered.splice(sourceIndex, 1);
  if (!movedBlock) return;
  reordered.splice(destinationIndex, 0, movedBlock);
  blocks.value = reordered;
}

function textValue(block: StructuredContentBlock, field = "text") {
  const value = block[field];
  return typeof value === "string" ? value : "";
}

function setTextValue(block: StructuredContentBlock, value: string, field = "text") {
  block[field] = value;
}

function listText(block: StructuredContentBlock) {
  return Array.isArray(block.items) ? block.items.join("\n") : "";
}

function setListText(block: StructuredContentBlock, value: string) {
  block.items = value.split("\n");
}

function links(block: StructuredContentBlock) {
  if (!Array.isArray(block.links)) block.links = [];
  return block.links as Array<{ label: string; url: string }>;
}

function addLink(block: StructuredContentBlock) {
  links(block).push({ label: "", url: "" });
}

function removeLink(block: StructuredContentBlock, index: number) {
  block.links = links(block).filter((_, linkIndex) => linkIndex !== index);
}

async function uploadBlockImage(block: StructuredContentBlock, index: number) {
  const file = imageFiles[index];
  const alternativeText = textValue(block, "alternativeText").trim();
  if (!file || !alternativeText) {
    await showError(
      "Image details required",
      "Choose an image and describe it for people who use screen readers.",
    );
    return;
  }
  uploadingBlockIndex.value = index;
  try {
    const body = new FormData();
    body.set("file", file);
    body.set("alternativeText", alternativeText);
    const media = await api.request<CommunicationMediaAsset>(
      "/api/communications/editorial/media",
      { method: "POST", body },
    );
    block.mediaAssetId = media.id;
    imageFiles[index] = null;
    toast.add({ title: "Image uploaded", color: "success", icon: "i-lucide-image-up" });
  } catch (error) {
    await showError("Image was not uploaded", api.errorMessage(error));
  } finally {
    uploadingBlockIndex.value = null;
  }
}

function imageUrl(block: StructuredContentBlock) {
  return typeof block.mediaAssetId === "string" && block.mediaAssetId
    ? communications.mediaUrl(block.mediaAssetId)
    : "";
}
</script>

<template>
  <div>
    <div class="rounded-xl border border-uzazure-200 bg-uzazure-50 p-4">
      <p class="text-sm font-bold text-uzazure-950">Add a content block</p>
      <div class="mt-3 flex flex-wrap gap-2">
        <UButton
          v-for="choice in blockChoices"
          :key="choice.type"
          :label="choice.label"
          :icon="choice.icon"
          color="neutral"
          variant="outline"
          size="sm"
          :disabled="disabled"
          @click="addBlock(choice.type)"
        />
      </div>
    </div>

    <div class="mt-4 space-y-3">
      <article
        v-for="(block, index) in blocks"
        :key="index"
        draggable="true"
        class="rounded-xl border border-slate-200 bg-white shadow-sm transition"
        :class="draggedIndex === index ? 'opacity-50 ring-2 ring-uzazure-500' : ''"
        @dragstart="startDragging(index)"
        @dragover.prevent
        @drop.prevent="dropBlock(index)"
      >
        <header
          class="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-2.5"
        >
          <div
            class="flex items-center gap-2 text-xs font-extrabold uppercase tracking-wide text-uzazure-800"
          >
            <UIcon name="i-lucide-grip-vertical" class="size-4 cursor-grab" />
            {{ block.type.toLowerCase() }}
          </div>
          <div class="flex items-center gap-1">
            <UButton
              icon="i-lucide-arrow-up"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="disabled || index === 0"
              aria-label="Move block up"
              @click="moveBlock(index, -1)"
            />
            <UButton
              icon="i-lucide-arrow-down"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="disabled || index === blocks.length - 1"
              aria-label="Move block down"
              @click="moveBlock(index, 1)"
            />
            <UButton
              icon="i-lucide-trash-2"
              color="error"
              variant="ghost"
              size="xs"
              :disabled="disabled"
              aria-label="Remove block"
              @click="removeBlock(index)"
            />
          </div>
        </header>

        <div class="grid gap-3 p-4">
          <UTextarea
            v-if="['HEADING', 'PARAGRAPH'].includes(block.type)"
            :model-value="textValue(block)"
            :rows="block.type === 'HEADING' ? 2 : 5"
            :placeholder="
              block.type === 'HEADING' ? 'Section heading' : 'Write the public text here…'
            "
            :disabled="disabled"
            autoresize
            class="w-full"
            @update:model-value="setTextValue(block, String($event))"
          />
          <template v-else-if="block.type === 'LIST'">
            <USwitch
              :model-value="block.ordered === true"
              label="Numbered list"
              :disabled="disabled"
              @update:model-value="block.ordered = $event"
            />
            <UTextarea
              :model-value="listText(block)"
              :rows="5"
              placeholder="Enter one list item per line"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setListText(block, String($event))"
            />
          </template>
          <template v-else-if="block.type === 'QUOTE'">
            <UTextarea
              :model-value="textValue(block)"
              :rows="4"
              placeholder="Quotation"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event))"
            />
            <UInput
              :model-value="textValue(block, 'attribution')"
              placeholder="Attribution (optional)"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event), 'attribution')"
            />
          </template>
          <template v-else-if="block.type === 'CALLOUT'">
            <UInput
              :model-value="textValue(block, 'title')"
              placeholder="Callout title (optional)"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event), 'title')"
            />
            <UTextarea
              :model-value="textValue(block)"
              :rows="4"
              placeholder="Important information"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event))"
            />
          </template>
          <template v-else-if="block.type === 'IMAGE'">
            <img
              v-if="imageUrl(block)"
              :src="imageUrl(block)"
              :alt="textValue(block, 'alternativeText')"
              class="max-h-64 w-full rounded-lg bg-slate-100 object-contain"
            />
            <UFileUpload
              v-model="imageFiles[index]"
              accept="image/jpeg,image/png,image/webp"
              label="Choose an article image"
              description="JPEG, PNG or WebP"
              :disabled="disabled"
              class="w-full"
            />
            <UInput
              :model-value="textValue(block, 'alternativeText')"
              placeholder="Describe the image for screen readers"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event), 'alternativeText')"
            />
            <UInput
              :model-value="textValue(block, 'caption')"
              placeholder="Caption (optional)"
              :disabled="disabled"
              class="w-full"
              @update:model-value="setTextValue(block, String($event), 'caption')"
            />
            <UButton
              label="Upload image"
              icon="i-lucide-upload"
              class="w-fit"
              :disabled="disabled || !imageFiles[index]"
              :loading="uploadingBlockIndex === index"
              @click="uploadBlockImage(block, index)"
            />
          </template>
          <template v-else-if="block.type === 'LINKS'">
            <div
              v-for="(link, linkIndex) in links(block)"
              :key="linkIndex"
              class="grid gap-2 rounded-lg bg-slate-50 p-3 sm:grid-cols-[minmax(0,.8fr)_minmax(0,1.4fr)_auto]"
            >
              <UInput v-model="link.label" placeholder="Link label" :disabled="disabled" />
              <UInput v-model="link.url" placeholder="https://…" :disabled="disabled" />
              <UButton
                icon="i-lucide-x"
                color="error"
                variant="ghost"
                :disabled="disabled"
                aria-label="Remove link"
                @click="removeLink(block, linkIndex)"
              />
            </div>
            <UButton
              label="Add link"
              icon="i-lucide-plus"
              color="neutral"
              variant="outline"
              class="w-fit"
              :disabled="disabled"
              @click="addLink(block)"
            />
          </template>
        </div>
      </article>

      <p
        v-if="!blocks.length"
        class="rounded-xl border border-dashed border-slate-300 px-5 py-8 text-center text-sm text-slate-500"
      >
        Add a heading, text, list, image, or another block to build the article body.
      </p>
    </div>
  </div>
</template>
