// Author: Tinashe K

import { flushPromises, mount } from "@vue/test-utils";
import { File as NodeFile } from "node:buffer";
import { computed, defineComponent, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EmhareEvidenceUploader from "../../components/domain/admissions/EmhareEvidenceUploader.vue";

const request = vi.fn();
const showError = vi.fn();

Object.assign(globalThis, { computed, onBeforeUnmount, onMounted, ref, watch });
vi.stubGlobal("File", NodeFile);
vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage: (error: unknown) => String(error) }));
vi.stubGlobal("useEmhareConfirm", () => ({ showError }));

const fileUploadStub = defineComponent({
  props: ["modelValue", "disabled"],
  emits: ["update:modelValue"],
  setup(_props, { emit }) {
    return {
      choose: () =>
        emit(
          "update:modelValue",
          new NodeFile(["scan"], "identity.pdf", { type: "application/pdf" }),
        ),
    };
  },
  template: '<button data-testid="file-input" @click="choose">choose</button>',
});

describe("EmhareEvidenceUploader", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    request.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("stops the spinner after a short wait and can check the stored document again", async () => {
    vi.useFakeTimers();
    request.mockResolvedValue({ status: "QUEUED" });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "IDENTITY_DOCUMENT",
        label: "Identity document",
        existingDocumentId: "stored",
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: defineComponent({
            props: ["title", "description", "actions"],
            template:
              '<div>{{ title }} {{ description }}<button v-for="action in actions" @click="action.onClick">{{ action.label }}</button></div>',
          }),
        },
      },
    });
    const vm = wrapper.vm as unknown as { pollExtraction: (id: string) => Promise<void> };
    void vm.pollExtraction("stored");
    await vi.advanceTimersByTimeAsync(16000);
    await flushPromises();
    expect(wrapper.findComponent({ name: "UProgress" }).exists()).toBe(false);
    expect(wrapper.text()).toContain("You can continue entering your details");
    const calls = request.mock.calls.length;
    await vi.advanceTimersByTimeAsync(60000);
    expect(request).toHaveBeenCalledTimes(calls);
    request.mockResolvedValue({ status: "COMPLETED", documentId: "stored" });
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "Check again")!
      .trigger("click");
    await flushPromises();
    expect(wrapper.emitted("extractionReady")?.[0]?.[0]).toMatchObject({ status: "COMPLETED" });
    expect(request.mock.calls.every(([, options]) => options?.method !== "POST")).toBe(true);
    wrapper.unmount();
  });

  it("starts upload and requirement linking immediately after file selection", async () => {
    request
      .mockResolvedValueOnce({
        id: "document-1",
        extractionStatus: "QUEUED",
        verificationStatus: "PENDING",
      })
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({
        documentId: "document-1",
        status: "COMPLETED",
        warningsJson: "[]",
      });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "NATIONAL_ID",
        label: "National ID",
        linkToRequirement: true,
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: true,
        },
      },
    });

    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();

    expect(request).toHaveBeenNthCalledWith(
      1,
      "/api/documents/uploads",
      expect.objectContaining({ method: "POST", body: expect.any(FormData) }),
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      "/api/admissions/applications/application-1/documents",
      expect.objectContaining({
        method: "POST",
        body: { documentId: "document-1", requirementCode: "NATIONAL_ID" },
      }),
    );
    expect(wrapper.emitted("uploaded")?.[0]?.[0]).toMatchObject({ id: "document-1" });
    expect(wrapper.emitted("extractionReady")?.[0]?.[0]).toMatchObject({ status: "COMPLETED" });
  });

  it("rejects files larger than ten megabytes before calling the API", async () => {
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "PASSPORT",
        label: "Passport",
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: true,
        },
      },
    });
    const viewModel = wrapper.vm as unknown as { startUpload: (file: File) => Promise<void> };

    await viewModel.startUpload(
      new File([new Uint8Array(10 * 1024 * 1024 + 1)], "large.pdf", { type: "application/pdf" }),
    );

    expect(request).not.toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "File is too large",
      "Choose a PDF, JPEG, or PNG no larger than 10 MB.",
    );
  });

  it("rejects unsupported file types before calling the API", async () => {
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "NATIONAL_ID",
        label: "National ID",
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: true,
        },
      },
    });
    const viewModel = wrapper.vm as unknown as { startUpload: (file: File) => Promise<void> };

    await viewModel.startUpload(new File(["text"], "identity.txt", { type: "text/plain" }));

    expect(request).not.toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "Unsupported file type",
      "Choose a PDF, JPEG, or PNG document.",
    );
  });

  it("keeps a transport failure distinct from a stored OCR failure", async () => {
    request.mockRejectedValueOnce(new Error("network unavailable"));
    const alertStub = defineComponent({
      props: ["title", "description", "actions"],
      template: '<div data-testid="alert">{{ title }} {{ description }}</div>',
    });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "BIRTH_CERTIFICATE",
        label: "Birth certificate",
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: alertStub,
        },
      },
    });

    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('[data-testid="alert"]').text()).toContain("The file was not uploaded");
    expect(wrapper.get('[data-testid="alert"]').text()).not.toContain("file is stored");
    expect(showError).toHaveBeenCalledWith(
      "Document could not be uploaded",
      "Error: network unavailable",
    );
  });

  it("retries OCR for an existing stored document", async () => {
    request
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ documentId: "document-existing", status: "FAILED" });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "PASSPORT",
        label: "Passport",
        existingDocumentId: "document-existing",
        existingState: "PENDING",
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: true,
        },
      },
    });
    const viewModel = wrapper.vm as unknown as { retryOcr: () => Promise<void> };

    await viewModel.retryOcr();
    await flushPromises();

    expect(request).toHaveBeenNthCalledWith(
      1,
      "/api/documents/uploads/document-existing/ocr-extraction/retry",
      { method: "POST" },
    );
  });

  it("identifies an existing pending document when its replacement uploads", async () => {
    request
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({
        id: "document-replacement",
        extractionStatus: "QUEUED",
        verificationStatus: "PENDING",
      })
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ documentId: "document-replacement", status: "COMPLETED" });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "IDENTITY_DOCUMENT",
        label: "Identity document",
        existingDocumentId: "document-pending",
        existingState: "PENDING",
        linkToRequirement: true,
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: true,
        },
      },
    });

    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();

    const uploadOptions = request.mock.calls[1]![1] as { body: FormData };
    expect(uploadOptions.body.get("replacesDocumentId")).toBe("document-pending");
  });

  it("does not report an application attachment failure as an OCR failure", async () => {
    request
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({
        id: "document-stored",
        extractionStatus: "QUEUED",
        verificationStatus: "PENDING",
      })
      .mockRejectedValueOnce(new Error("application attachment failed"));
    const alertStub = defineComponent({
      props: ["title", "description", "actions"],
      template: '<div data-testid="alert">{{ title }} {{ description }}</div>',
    });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "IDENTITY_DOCUMENT",
        label: "Identity document",
        existingDocumentId: "document-pending",
        existingState: "PENDING",
        linkToRequirement: true,
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: alertStub,
        },
      },
    });

    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('[data-testid="alert"]').text()).toContain(
      "not attached to this application",
    );
    expect(wrapper.get('[data-testid="alert"]').text()).not.toContain("OCR");
    expect(showError).toHaveBeenCalledWith(
      "Document could not be attached to this application",
      "Error: application attachment failed",
    );
  });

  it("retries a stored attachment without uploading the file again", async () => {
    request
      .mockResolvedValueOnce({ id: "stored", extractionStatus: "QUEUED" })
      .mockRejectedValueOnce(new Error("temporary conflict"))
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ documentId: "stored", status: "COMPLETED" });
    const wrapper = mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "IDENTITY_DOCUMENT",
        label: "Identity document",
        linkToRequirement: true,
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: defineComponent({
            props: ["actions"],
            template:
              '<div><button v-for="action in actions" @click="action.onClick">{{ action.label }}</button></div>',
          }),
        },
      },
    });
    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();
    expect(wrapper.emitted("uploaded")).toBeUndefined();

    const retry = wrapper.findAll("button").find((button) => button.text() === "Retry attachment");
    expect(retry).toBeDefined();
    await retry!.trigger("click");
    await flushPromises();

    expect(request.mock.calls.filter(([path]) => path === "/api/documents/uploads")).toHaveLength(
      1,
    );
    expect(request).toHaveBeenNthCalledWith(
      3,
      "/api/admissions/applications/application-1/documents",
      { method: "POST", body: { documentId: "stored", requirementCode: "IDENTITY_DOCUMENT" } },
    );
    expect(wrapper.emitted("uploaded")?.[0]?.[0]).toMatchObject({ id: "stored" });
    expect(wrapper.emitted("extractionReady")?.[0]?.[0]).toMatchObject({ status: "COMPLETED" });
  });
  function mountRecoverableUploader(disabled = false) {
    return mount(EmhareEvidenceUploader, {
      props: {
        applicationId: "application-1",
        documentTypeCode: "IDENTITY_DOCUMENT",
        label: "Identity document",
        existingDocumentId: "previous",
        linkToRequirement: true,
        disabled,
      },
      global: {
        stubs: {
          UFileUpload: fileUploadStub,
          UIcon: true,
          UBadge: true,
          UProgress: true,
          UAlert: defineComponent({
            props: ["title", "description", "actions"],
            template:
              '<div>{{ title }} {{ description }}<button v-for="action in actions" :disabled="action.disabled" @click="action.onClick">{{ action.label }}</button></div>',
          }),
        },
      },
    });
  }

  const storedReplacement = {
    id: "recovered",
    replacesDocumentId: "previous",
    documentTypeCode: "IDENTITY_DOCUMENT",
    verificationStatus: "PENDING",
    originalFileName: "replacement.pdf",
    uploadedAt: "2026-08-30T10:00:00Z",
  };

  it("recovers only the latest pending replacement for this requirement after refresh", async () => {
    request
      .mockResolvedValueOnce([
        { ...storedReplacement, id: "older", uploadedAt: "2026-08-29T10:00:00Z" },
        { ...storedReplacement, id: "unrelated", replacesDocumentId: "different" },
        { ...storedReplacement, id: "wrong-type", documentTypeCode: "PASSPORT" },
        { ...storedReplacement, id: "verified", verificationStatus: "VERIFIED" },
        storedReplacement,
      ])
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ status: "COMPLETED" });
    const wrapper = mountRecoverableUploader();
    await flushPromises();
    expect(wrapper.text()).toContain("replacement.pdf is already uploaded");
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "Retry attachment")!
      .trigger("click");
    await flushPromises();
    expect(request).toHaveBeenNthCalledWith(
      2,
      "/api/admissions/applications/application-1/documents",
      { method: "POST", body: { documentId: "recovered", requirementCode: "IDENTITY_DOCUMENT" } },
    );
    expect(wrapper.emitted("uploaded")?.[0]?.[0]).toMatchObject({ id: "recovered" });
  });

  it("keeps file selection available if recovery lookup fails", async () => {
    request.mockRejectedValueOnce(new Error("offline"));
    const wrapper = mountRecoverableUploader();
    await flushPromises();
    expect(wrapper.text()).not.toContain("Retry attachment");
    expect(showError).not.toHaveBeenCalled();
    expect(wrapper.findComponent(fileUploadStub).props("disabled")).toBe(false);
  });

  it("does not overwrite a new upload with a late recovery lookup", async () => {
    let finishLookup!: (value: unknown) => void;
    request
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishLookup = resolve;
        }),
      )
      .mockResolvedValueOnce({ id: "new-upload", extractionStatus: "COMPLETED" })
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ status: "COMPLETED" });
    const wrapper = mountRecoverableUploader();
    await wrapper.get('[data-testid="file-input"]').trigger("click");
    await flushPromises();
    finishLookup([storedReplacement]);
    await flushPromises();
    expect(wrapper.text()).not.toContain("Retry attachment");
    expect(wrapper.emitted("uploaded")?.[0]?.[0]).toMatchObject({ id: "new-upload" });
  });

  it("prevents retries when disabled, missing a stored upload, or already attaching", async () => {
    request.mockResolvedValueOnce([storedReplacement]);
    const wrapper = mountRecoverableUploader(true);
    const vm = wrapper.vm as unknown as { retryAttachment: () => Promise<void> };
    await vm.retryAttachment();
    expect(request).not.toHaveBeenCalled();
    await wrapper.setProps({ disabled: false });
    await vm.retryAttachment();
    expect(request).not.toHaveBeenCalled();
    wrapper.unmount();
    request.mockReset();
    let finishAttachment!: (value: unknown) => void;
    request
      .mockResolvedValueOnce([storedReplacement])
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishAttachment = resolve;
        }),
      )
      .mockResolvedValueOnce({ status: "COMPLETED" });
    const active = mountRecoverableUploader();
    await flushPromises();
    const activeVm = active.vm as unknown as { retryAttachment: () => Promise<void> };
    const retry = activeVm.retryAttachment();
    await activeVm.retryAttachment();
    expect(request).toHaveBeenCalledTimes(2);
    finishAttachment({});
    await retry;
    await flushPromises();
    expect(active.emitted("uploaded")).toHaveLength(1);
  });
});
