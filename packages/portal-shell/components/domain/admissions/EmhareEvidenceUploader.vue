<script setup lang="ts">
// Author: Tinashe K
import type {
  DocumentOcrExtractionSummary,
  UploadedDocumentSummary,
} from "../../../types/documents";

const props = withDefaults(
  defineProps<{
    applicationId: string;
    documentTypeCode: string;
    label: string;
    description?: string;
    required?: boolean;
    existingDocumentId?: string | null;
    existingState?: "MISSING" | "PENDING" | "VERIFIED" | "REJECTED" | null;
    linkToRequirement?: boolean;
    disabled?: boolean;
  }>(),
  {
    description: undefined,
    required: true,
    existingDocumentId: null,
    existingState: "MISSING",
    linkToRequirement: false,
    disabled: false,
  },
);

const emit = defineEmits<{
  uploaded: [document: UploadedDocumentSummary];
  extractionReady: [extraction: DocumentOcrExtractionSummary];
}>();

const api = useEmhareApi();
const { showError } = useEmhareConfirm();
const selectedFile = ref<File | null>(null);
const uploadState = ref<
  | "idle"
  | "uploading"
  | "scanning"
  | "attaching"
  | "queued"
  | "processing"
  | "completed"
  | "failed"
  | "deferred"
>("idle");
const uploadedDocument = ref<UploadedDocumentSummary | null>(null);
const uploadError = ref("");
const failureStage = ref<"upload" | "attachment" | "ocr" | null>(null);
let pollingGeneration = 0;
const acceptedContentTypes = new Set(["application/pdf", "image/jpeg", "image/png"]);

const statusLabel = computed(() => {
  if (uploadState.value === "uploading") return "Uploading securely";
  if (uploadState.value === "attaching") return "Attaching document";
  if (uploadState.value === "scanning") return "Malware scan and storage";
  if (uploadState.value === "deferred") return "Uploaded — enter details below";
  if (uploadState.value === "queued") return "Reading document";
  if (uploadState.value === "processing") return "Reading document";
  if (uploadState.value === "completed") return "Ready to review";
  if (uploadState.value === "failed" && failureStage.value === "upload") return "Upload failed";
  if (uploadState.value === "failed" && failureStage.value === "attachment")
    return "Uploaded — attachment failed";
  if (uploadState.value === "failed") return "Uploaded — enter details manually";
  if (props.existingState === "VERIFIED") return "Verified";
  if (props.existingState === "PENDING") return "Uploaded — verification pending";
  if (props.existingState === "REJECTED") return "Rejected — replace this file";
  return props.required ? "Required before continuing" : "Optional evidence";
});

function handleSelectedFile(value: File | File[] | null | undefined) {
  const file = Array.isArray(value) ? value[0] : value;
  selectedFile.value = file ?? null;
  if (file) void startUpload(file);
}

async function startUpload(file: File) {
  if (props.disabled || ["uploading", "scanning", "attaching"].includes(uploadState.value)) return;
  if (!acceptedContentTypes.has(file.type)) {
    selectedFile.value = null;
    await showError("Unsupported file type", "Choose a PDF, JPEG, or PNG document.");
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    selectedFile.value = null;
    await showError("File is too large", "Choose a PDF, JPEG, or PNG no larger than 10 MB.");
    return;
  }
  pollingGeneration++;
  uploadError.value = "";
  failureStage.value = null;
  uploadState.value = "uploading";
  const formData = new FormData();
  formData.append("ownerType", "APPLICATION");
  formData.append("ownerId", props.applicationId);
  formData.append("documentTypeCode", props.documentTypeCode);
  if (props.existingDocumentId) {
    formData.append("replacesDocumentId", props.existingDocumentId);
  }
  formData.append("file", file);
  let uploaded: UploadedDocumentSummary;
  try {
    uploadState.value = "scanning";
    uploaded = await api.request<UploadedDocumentSummary>("/api/documents/uploads", {
      method: "POST",
      body: formData,
    });
    uploadedDocument.value = uploaded;
  } catch (error) {
    failureStage.value = "upload";
    uploadState.value = "failed";
    uploadError.value = api.errorMessage(error);
    await showError("Document could not be uploaded", uploadError.value);
    return;
  }
  await attachUploadedDocument(uploaded);
}

async function attachUploadedDocument(uploaded: UploadedDocumentSummary) {
  uploadState.value = "attaching";
  uploadError.value = "";
  failureStage.value = null;
  try {
    if (props.linkToRequirement) {
      await api.request(`/api/admissions/applications/${props.applicationId}/documents`, {
        method: "POST",
        body: { documentId: uploaded.id, requirementCode: props.documentTypeCode },
      });
    }
  } catch (error) {
    failureStage.value = "attachment";
    uploadState.value = "failed";
    uploadError.value = api.errorMessage(error);
    await showError("Document could not be attached to this application", uploadError.value);
    return;
  }
  uploadState.value = (uploaded.extractionStatus?.toLowerCase() ??
    "queued") as typeof uploadState.value;
  emit("uploaded", uploaded);
  void pollExtraction(uploaded.id);
}

async function retryAttachment() {
  if (props.disabled || !uploadedDocument.value || uploadState.value === "attaching") return;
  await attachUploadedDocument(uploadedDocument.value);
}

onMounted(async () => {
  if (!props.linkToRequirement || !props.existingDocumentId || props.disabled) return;
  try {
    const stored = await api.request<UploadedDocumentSummary[]>(
      `/api/documents/uploads?ownerType=APPLICATION&ownerId=${encodeURIComponent(props.applicationId)}`,
    );
    const replacement = stored
      .filter(
        (document) =>
          document.replacesDocumentId === props.existingDocumentId &&
          document.documentTypeCode === props.documentTypeCode &&
          document.verificationStatus === "PENDING",
      )
      .sort((left, right) => Date.parse(right.uploadedAt) - Date.parse(left.uploadedAt))[0];
    if (!replacement || uploadState.value !== "idle") return;
    uploadedDocument.value = replacement;
    failureStage.value = "attachment";
    uploadState.value = "failed";
  } catch {
    // A failed recovery lookup must not prevent choosing a new file.
  }
});

async function pollExtraction(documentId: string) {
  const generation = ++pollingGeneration;
  const deadline = Date.now() + 15000;
  uploadState.value = "queued";
  for (
    let attempt = 0;
    attempt < 10 && generation === pollingGeneration && Date.now() < deadline;
    attempt++
  ) {
    try {
      const extraction = await api.request<DocumentOcrExtractionSummary>(
        `/api/documents/uploads/${documentId}/ocr-extraction`,
        { timeout: Math.min(5000, deadline - Date.now()), retry: 0 },
      );
      if (generation !== pollingGeneration) return;
      uploadState.value = extraction.status.toLowerCase() as typeof uploadState.value;
      if (["COMPLETED", "FAILED", "UNSUPPORTED"].includes(extraction.status)) {
        if (["FAILED", "UNSUPPORTED"].includes(extraction.status)) {
          failureStage.value = "ocr";
          uploadState.value = "failed";
        }
        emit("extractionReady", extraction);
        return;
      }
    } catch {
      // The upload remains valid. A later retry or manual entry is still available.
    }
    if (generation !== pollingGeneration) return;
    await new Promise((resolve) =>
      window.setTimeout(resolve, Math.min(1500, Math.max(0, deadline - Date.now()))),
    );
  }
  if (generation === pollingGeneration) uploadState.value = "deferred";
}

function checkExtraction() {
  const documentId = uploadedDocument.value?.id ?? props.existingDocumentId;
  if (documentId) void pollExtraction(documentId);
}

onBeforeUnmount(() => {
  pollingGeneration++;
});

async function retryOcr() {
  const documentId = uploadedDocument.value?.id ?? props.existingDocumentId;
  if (!documentId) return;
  try {
    await api.request(`/api/documents/uploads/${documentId}/ocr-extraction/retry`, {
      method: "POST",
    });
    failureStage.value = null;
    uploadState.value = "queued";
    void pollExtraction(documentId);
  } catch (error) {
    await showError("OCR could not be retried", api.errorMessage(error));
  }
}
</script>

<template>
  <section class="overflow-hidden rounded-xl border border-default bg-default shadow-sm">
    <div class="flex items-start gap-3 border-b border-default px-4 py-3">
      <div class="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
        <UIcon name="i-lucide-scan-line" class="size-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h3 class="font-semibold text-highlighted">
            {{ label }} <span v-if="required" class="text-error">*</span>
          </h3>
          <UBadge
            :color="
              uploadState === 'completed' || existingState === 'VERIFIED'
                ? 'success'
                : uploadState === 'failed' || existingState === 'REJECTED'
                  ? 'warning'
                  : 'primary'
            "
            variant="subtle"
            :label="statusLabel"
          />
        </div>
        <p v-if="description" class="mt-1 text-sm text-muted">{{ description }}</p>
      </div>
    </div>

    <div class="space-y-3 p-4">
      <UFileUpload
        :model-value="selectedFile"
        @update:model-value="handleSelectedFile"
        accept="application/pdf,image/jpeg,image/png"
        variant="area"
        :disabled="disabled || ['uploading', 'scanning', 'attaching'].includes(uploadState)"
        :label="
          existingDocumentId
            ? 'Drop a replacement or click to choose'
            : 'Drop the document here or click to choose'
        "
        description="PDF, JPEG or PNG · maximum 10 MB · upload starts immediately"
        class="w-full"
      />
      <UProgress
        v-if="['uploading', 'scanning', 'attaching', 'queued', 'processing'].includes(uploadState)"
        animation="carousel"
        color="primary"
      />
      <UAlert
        v-if="uploadState === 'deferred'"
        color="info"
        variant="subtle"
        icon="i-lucide-info"
        title="Document reading is taking longer than expected"
        description="You can continue entering your details. Your document is saved."
        :actions="[
          { label: 'Check again', color: 'neutral', variant: 'outline', onClick: checkExtraction },
        ]"
      />
      <UAlert
        v-if="uploadState === 'failed'"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        :title="
          failureStage === 'attachment'
            ? 'The file is stored, but is not attached to this application'
            : failureStage === 'ocr'
              ? 'The file is stored, but OCR needs attention'
              : 'The file was not uploaded'
        "
        :description="
          failureStage === 'attachment'
            ? `${uploadedDocument?.originalFileName || 'Your file'} is already uploaded. Retry attachment without uploading it again.`
            : failureStage === 'ocr'
              ? 'You can enter the details manually now or retry document reading.'
              : 'Choose the file again to retry the secure upload.'
        "
        :actions="
          failureStage === 'attachment'
            ? [
                {
                  label: 'Retry attachment',
                  color: 'warning',
                  variant: 'outline',
                  onClick: retryAttachment,
                  disabled,
                },
              ]
            : failureStage === 'ocr'
              ? [{ label: 'Retry OCR', color: 'warning', variant: 'outline', onClick: retryOcr }]
              : []
        "
      />
      <p v-if="uploadError" class="text-sm text-error">{{ uploadError }}</p>
    </div>
  </section>
</template>
