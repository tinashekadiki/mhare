// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DocumentsPage from "../../pages/operations/documents.vue";
import type {
  UploadedDocumentSummary,
  OfficialDocumentSummary,
} from "../../../../packages/portal-shell/types/documents";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import {
  RegisterDrawer,
  RegisterField,
  RegisterTable,
  registerStubs,
  installRegisterPeriodContext,
} from "../../../../tests/unit/support/register-page";

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let uploads: UploadedDocumentSummary[];
let official: (OfficialDocumentSummary & { academicPeriodId?: string })[];
let failPath: string | undefined;
const success = vi.fn();
const upload = (overrides: Partial<UploadedDocumentSummary> = {}): UploadedDocumentSummary => ({
  id: "evidence",
  ownerType: "APPLICATION",
  ownerId: "application",
  documentTypeCode: "ACADEMIC_CERTIFICATE",
  originalFileName: "certificate.pdf",
  mimeType: "application/pdf",
  fileSizeBytes: 2048,
  checksumSha256: "checksum",
  uploadedByUserId: "applicant",
  uploadedAt: "2026-08-01T10:00:00Z",
  verificationStatus: "PENDING",
  version: 7,
  ...overrides,
});
const record = (overrides: Partial<OfficialDocumentSummary> = {}): OfficialDocumentSummary => ({
  id: "official",
  documentNumber: "RESULT-001",
  documentType: "RESULT_SLIP",
  studentNumber: "R260001",
  academicPeriodCode: "2026-S1",
  decisionCode: "PROCEED",
  decisionLabel: "Proceed",
  status: "STORED",
  templateCode: "SLIP",
  templateVersion: 1,
  requestedAt: "2026-08-01T10:00:00Z",
  generatedAt: "2026-08-01T10:05:00Z",
  sizeBytes: 1048576,
  generationAttemptCount: 1,
  retryAvailable: false,
  version: 5,
  ...overrides,
});
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  installRegisterPeriodContext(context.selectedAcademicPeriodId);
  vi.stubGlobal("useEmhareConfirm", () => ({ showSuccess: success, showError: context.showError }));
  confirm.mockResolvedValue({ isConfirmed: true });
  uploads = [upload()];
  official = [record()];
  failPath = undefined;
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (path.endsWith("/download"))
      return { downloadUrl: "https://storage.example.test/signed-document" };
    if (options?.method) return upload();
    if (path === "/api/documents/uploads") return structuredClone(uploads);
    if (path === "/api/documents") return structuredClone(official);
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(DocumentsPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
async function switchOfficial() {
  await clickButton(wrapper, `Official records ${official.length}`);
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function fillUpload() {
  await clickButton(wrapper, "Upload document");
  await setField(wrapper, "Record type", "STUDENT");
  await setField(wrapper, "Record ID", "  student  ");
  await setField(wrapper, "Document type", "ACADEMIC_TRANSCRIPT");
  const input = wrapper.get('input[type="file"]');
  Object.defineProperty(input.element, "files", {
    value: [new File(["evidence"], "transcript.pdf", { type: "application/pdf" })],
    configurable: true,
  });
  await input.trigger("change");
}
async function submit() {
  const dialog = wrapper.get('[role="dialog"]');
  await dialog
    .findAll("button")
    .find((button) => button.text() !== "Cancel" && !button.text().includes("new tab"))!
    .trigger("click");
  await flushPromises();
}

describe("governed document workspace", () => {
  it("renders evidence states, sizes, timestamps, counts and filters independently", async () => {
    uploads = [
      upload({ fileSizeBytes: 0 }),
      upload({
        id: "verified",
        verificationStatus: "VERIFIED",
        fileSizeBytes: 10,
        verifiedAt: "2026-08-02T10:00:00Z",
      }),
      upload({ id: "rejected", verificationStatus: "REJECTED", fileSizeBytes: 1048576 }),
    ];
    await render();
    expect(wrapper.text()).toContain("Uploaded: 3");
    expect(wrapper.text()).toContain("Pending verification: 1");
    expect(wrapper.text()).toContain("10 B");
    expect(wrapper.text()).toContain("1.0 MB");
    for (const status of ["VERIFIED", "REJECTED", "PENDING"]) {
      await wrapper.get("select").setValue(status);
      expect(wrapper.findAll("article")).toHaveLength(1);
      expect(wrapper.get("article").text()).toContain(status);
    }
  });
  it.each(["/api/documents/uploads", "/api/documents"])(
    "retains the independent register when %s fails and refresh recovers",
    async (path) => {
      failPath = path;
      await render();
      if (path === "/api/documents") await clickButton(wrapper, "Official records 0");
      expect(wrapper.text()).toContain("could not be loaded");
      expect(wrapper.text()).toContain("Unavailable");
      failPath = undefined;
      await clickButton(wrapper, "Refresh");
      expect(wrapper.text()).not.toContain("could not be loaded");
      expect(wrapper.findAll("article")).toHaveLength(1);
    },
  );
  it("searches, sorts and pages evidence using the table's public state contract", async () => {
    uploads = Array.from({ length: 12 }, (_, index) =>
      upload({
        id: `evidence-${index}`,
        documentTypeCode: index === 0 ? "Z_TRANSCRIPT" : "A_CERTIFICATE",
        originalFileName: `file-${String(index).padStart(2, "0")}.pdf`,
        verificationComment: null,
      }),
    );
    await render();
    expect(wrapper.findAll("article")).toHaveLength(10);
    await clickButton(wrapper, "Next page");
    expect(wrapper.findAll("article")).toHaveLength(2);
    await wrapper.get('[aria-label="Search records"]').setValue("  FiLe-00  ");
    expect(wrapper.findAll("article")).toHaveLength(1);
    expect(wrapper.text()).toContain("1 records");
    await wrapper.get('[aria-label="Search records"]').setValue("");
    await clickButton(wrapper, "Sort ascending");
    expect(wrapper.findAll("article")[0]!.text()).toContain("A CERTIFICATE");
    await clickButton(wrapper, "Sort descending");
    expect(wrapper.findAll("article")[0]!.text()).toContain("Z TRANSCRIPT");
  });
  it("filters official records by period and status, including queued and failed states", async () => {
    official = [
      record(),
      record({ id: "queued", status: "REQUESTED", generatedAt: null, sizeBytes: null }),
      record({ id: "generating", status: "GENERATING", sizeBytes: 512 }),
      record({ id: "failed", status: "FAILED", sizeBytes: 2048 }),
      { ...record({ id: "old" }), academicPeriodId: "period-old" },
    ];
    await render();
    await switchOfficial();
    expect(wrapper.findAll("article")).toHaveLength(4);
    expect(wrapper.text()).toContain("In generation: 2");
    for (const status of ["REQUESTED", "GENERATING", "FAILED", "STORED"]) {
      await wrapper.get("select").setValue(status);
      expect(wrapper.findAll("article")).toHaveLength(1);
      expect(wrapper.get("article").text()).toContain(status);
    }
    await wrapper.get("select").setValue("ALL");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(wrapper.findAll("article")).toHaveLength(5);
  });
  it("blocks incomplete uploads and resets cancelled form fields", async () => {
    await render();
    await clickButton(wrapper, "Upload document");
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    wrapper.getComponent(RegisterDrawer).vm.$emit("submit");
    await flushPromises();
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Record ID", "discarded");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Upload document");
    expect(wrapper.get('[data-label="Record ID"] input').element).toHaveProperty("value", "");
  });
  it.each([false, true])(
    "uploads owned evidence with optional replacement %s",
    async (replacement) => {
      await render();
      await fillUpload();
      if (replacement) await setField(wrapper, "Rejected document being replaced", " rejected ");
      await submit();
      const [path, options] = writes()[0]!;
      expect(path).toBe("/api/documents/uploads");
      const form = options.body as FormData;
      expect(form.get("ownerType")).toBe("STUDENT");
      expect(form.get("ownerId")).toBe("student");
      expect(form.get("documentTypeCode")).toBe("ACADEMIC_TRANSCRIPT");
      expect(form.get("replacesDocumentId")).toBe(replacement ? "rejected" : null);
      expect(form.get("file")).toHaveProperty("name", "transcript.pdf");
      expect(success).toHaveBeenCalledWith("Document uploaded", expect.any(String));
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("accepts a single-file control value as well as an array", async () => {
    await render();
    await fillUpload();
    const field = wrapper
      .findAllComponents(RegisterField)
      .find((field) => field.props("name") === "file")!;
    field.vm.$emit("update:modelValue", new File(["proof"], "single.pdf"));
    await flushPromises();
    await submit();
    expect((writes()[0]![1].body as FormData).get("file")).toHaveProperty("name", "single.pdf");
  });
  it("keeps upload data available after a failed submission", async () => {
    await render();
    await fillUpload();
    failPath = "/api/documents/uploads";
    await submit();
    expect(context.showError).toHaveBeenCalledWith("Document could not be uploaded", "Unavailable");
    expect(wrapper.get('[data-label="Record ID"] input').element).toHaveProperty(
      "value",
      "  student  ",
    );
  });
  it.each(["  Checked against source  ", "   "])(
    "verifies evidence with optimistic version and normalized comment %s",
    async (comment) => {
      await render();
      await clickButton(wrapper, "Review decision");
      await setField(wrapper, "Verification comment", comment);
      await submit();
      expect(writes()[0]!).toEqual([
        "/api/documents/uploads/evidence/verify",
        { method: "POST", body: { expectedVersion: 7, comment: comment.trim() || null } },
      ]);
      expect(success).toHaveBeenCalledWith("Document verified", expect.any(String));
    },
  );
  it("requires a useful rejection reason then requests replacement with the original version", async () => {
    await render();
    await clickButton(wrapper, "Review decision");
    await setField(wrapper, "Decision", "REJECTED");
    await setField(wrapper, "Rejection reason", "short");
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    wrapper.getComponent(RegisterDrawer).vm.$emit("submit");
    await flushPromises();
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Rejection reason", "  Please replace the blurred certificate  ");
    await submit();
    expect(writes()[0]!).toEqual([
      "/api/documents/uploads/evidence/reject",
      {
        method: "POST",
        body: { expectedVersion: 7, reason: "Please replace the blurred certificate" },
      },
    ]);
  });
  it("retains the review after an API failure", async () => {
    await render();
    await clickButton(wrapper, "Review decision");
    failPath = "/api/documents/uploads/evidence/verify";
    await submit();
    expect(context.showError).toHaveBeenCalledWith(
      "Verification decision could not be recorded",
      "Unavailable",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it.each([
    ["VERIFIED", null, "Evidence verified."],
    ["VERIFIED", "Matched original", "Matched original"],
    ["REJECTED", null, "No rejection reason recorded."],
    ["REJECTED", "Image is unreadable", "Image is unreadable"],
  ] as const)("does not revise final %s evidence (%s)", async (status, reason, expected) => {
    uploads = [
      upload({
        verificationStatus: status,
        verificationComment: status === "VERIFIED" ? reason : null,
        rejectionReason: status === "REJECTED" ? reason : null,
      }),
    ];
    await render();
    await clickButton(wrapper, "Review decision");
    expect(wrapper.text()).toContain("Decision is final");
    expect(wrapper.text()).toContain(expected);
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    wrapper.getComponent(RegisterDrawer).vm.$emit("submit");
    await flushPromises();
    expect(writes()).toHaveLength(0);
  });
  it.each(["uploaded", "official"])("opens signed %s evidence securely", async (kind) => {
    const clicked: HTMLAnchorElement[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push(this);
    });
    await render();
    if (kind === "official") await switchOfficial();
    else await clickButton(wrapper, "Review decision");
    await clickButton(wrapper, kind === "official" ? "Open PDF" : "Open evidence in a new tab");
    expect(clicked).toHaveLength(1);
    expect(clicked[0]!.href).toBe("https://storage.example.test/signed-document");
    expect(clicked[0]!.target).toBe("_blank");
    expect(clicked[0]!.rel).toBe("noopener noreferrer");
  });
  it.each(["uploaded", "official"])("reports %s download errors", async (kind) => {
    await render();
    if (kind === "official") await switchOfficial();
    failPath =
      kind === "official"
        ? "/api/documents/official/download"
        : "/api/documents/uploads/evidence/download";
    await clickButton(wrapper, kind === "official" ? "Open PDF" : "Open evidence");
    expect(context.showError).toHaveBeenCalledWith(
      kind === "official"
        ? "Official document could not be opened"
        : "Uploaded document could not be opened",
      "Unavailable",
    );
  });
  it("does not download unfinished records or retry ineligible records", async () => {
    official = [record({ status: "GENERATING" })];
    await render();
    await switchOfficial();
    await clickButton(wrapper, "Open PDF");
    await clickButton(wrapper, "Retry generation");
    expect(context.notify.mock.calls.map(([value]) => value.title)).toEqual([
      "Document is not stored yet",
      "Retry is not available",
    ]);
    expect(context.request).toHaveBeenCalledTimes(2);
  });
  it.each(["cancel", "success", "failure"])(
    "handles generation retry %s without losing audit version",
    async (outcome) => {
      official = [record({ status: "FAILED", retryAvailable: true })];
      await render();
      await switchOfficial();
      confirm.mockResolvedValue({ isConfirmed: outcome !== "cancel" });
      if (outcome === "failure") failPath = "/api/documents/official/retry";
      await clickButton(wrapper, "Retry generation");
      expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ showCancelButton: true }));
      if (outcome === "cancel") expect(writes()).toHaveLength(0);
      else
        expect(writes()[0]!).toEqual([
          "/api/documents/official/retry",
          { method: "POST", body: { expectedVersion: 5 } },
        ]);
      if (outcome === "failure")
        expect(context.showError).toHaveBeenCalledWith(
          "Document retry could not be queued",
          "Unavailable",
        );
      if (outcome === "success")
        expect(context.notify).toHaveBeenCalledWith(
          expect.objectContaining({ title: "Document retry queued" }),
        );
    },
  );
  it("ignores unrecognized table actions", async () => {
    await render();
    wrapper
      .getComponent(RegisterTable)
      .vm.$emit("row-action", { action: { id: "unknown" }, row: uploads[0] });
    await switchOfficial();
    wrapper
      .getComponent(RegisterTable)
      .vm.$emit("row-action", { action: { id: "unknown" }, row: official[0] });
    await flushPromises();
    expect(context.request).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
});
