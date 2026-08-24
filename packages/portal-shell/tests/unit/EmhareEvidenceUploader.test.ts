// Author: Tinashe K

import { flushPromises, mount } from "@vue/test-utils";
import { File as NodeFile } from "node:buffer";
import { computed, defineComponent, ref, watch } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EmhareEvidenceUploader from "../../components/domain/admissions/EmhareEvidenceUploader.vue";

const request = vi.fn();
const showError = vi.fn();

Object.assign(globalThis, { computed, ref, watch });
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

    const uploadOptions = request.mock.calls[0]![1] as { body: FormData };
    expect(uploadOptions.body.get("replacesDocumentId")).toBe("document-pending");
  });

  it("does not report an application attachment failure as an OCR failure", async () => {
    request
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
});
