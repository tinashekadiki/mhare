// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref, shallowRef } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CorePage from "../../pages/operations/core.vue";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import {
  RegisterDrawer,
  RegisterField,
  registerStubs,
} from "../../../../tests/unit/support/register-page";
let wrapper: VueWrapper,
  context: ReturnType<typeof operationalContext>,
  failPath: string | undefined;
type Profile = {
  id?: string;
  code: string;
  name: string;
  legalName: string;
  registrarName: string;
  defaultCurrencyCode: string;
  countryCode: string;
  timezone: string;
  contactDetailsJson?: string;
  brandingJson?: string;
  bankDetailsJson?: string;
  legacyCode?: string;
};
let profile: Profile;
type Task = {
  id: string;
  taskReference: string;
  workflowCode: string;
  title: string;
  description: string;
  subjectType: string;
  subjectReference: string;
  assigneeType: string;
  assignedUserId?: string;
  assignedUserName?: string;
  assignedRoleId?: string;
  assignedRoleName?: string;
  scopeType: string;
  academicUnitId?: string;
  dueAt?: string;
  status: string;
  version: number;
  claimedByUserName?: string;
  decisions: {
    id: string;
    decisionCode: string;
    comment: string;
    decidedByUserName: string;
    decidedAt: string;
  }[];
};
let tasks: Task[];
const showSuccess = vi.fn(),
  syncCoreUser = vi.fn(),
  confirmAction = vi.fn();
const section = defineComponent({
  props: ["title"],
  template: "<section><h3>{{title}}</h3><slot/></section>",
});
const account = (currencyCode: string) => ({
  currencyCode,
  bankName: " CBZ ",
  accountNumber: currencyCode === "USD" ? " USD123 " : " ZWG123 ",
  accountName: " University ",
  branchName: " Main ",
  branchSortCode: "",
  swiftCode: "",
  paymentReferenceInstructions: " Quote registration number ",
});
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  vi.stubGlobal("shallowRef", shallowRef);
  vi.stubGlobal("useEmhareAuth", () => ({
    hasPermission: () => true,
    hasRole: () => true,
    syncCoreUser,
  }));
  vi.stubGlobal("useAcademicSetup", () => ({
    overview: ref({
      academicUnits: [
        {
          id: "unit",
          code: "SCI",
          name: "Science",
          academicUnitTypeCode: "FACULTY",
          status: "ACTIVE",
        },
      ],
    }),
    ensureOverview: vi.fn(),
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({
    showSuccess,
    showError: context.showError,
    confirmAction,
  }));
  profile = {
    id: "institution",
    code: "UZ",
    name: "University",
    legalName: "University of Zimbabwe",
    registrarName: "Registrar",
    defaultCurrencyCode: "USD",
    countryCode: "ZW",
    timezone: "Africa/Harare",
    contactDetailsJson: '{"email":"old@example.test","extension":true}',
    brandingJson:
      '{"documentHeader":"Official UZ","primaryColor":"#001f6e","secondaryColor":"#cb920e","custom":true}',
    bankDetailsJson: JSON.stringify({
      accounts: [account("USD"), account("ZWG")],
      custom: "preserved",
    }),
  };
  tasks = [
    {
      id: "task",
      taskReference: "TASK-001",
      workflowCode: "ADMISSIONS",
      title: "Review evidence",
      description: "Independent document checks",
      subjectType: "APPLICATION",
      subjectReference: "APP-001",
      assigneeType: "USER",
      assignedUserId: "user",
      assignedUserName: "Officer",
      scopeType: "INSTITUTION",
      status: "OPEN",
      version: 7,
      decisions: [],
    },
  ];
  failPath = undefined;
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:preview");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  context.request.mockImplementation(
    async (path: string, options?: { method?: string; body?: unknown }) => {
      if (path === failPath) throw new Error("Unavailable");
      if (options?.method) {
        if (path === "/api/core/institution-profile") {
          profile = { ...profile, ...(options.body as object) };
          return structuredClone(profile);
        }
        if (path === "/api/documents/uploads") {
          const form = options.body as FormData;
          return {
            id:
              form.get("documentTypeCode") === "INSTITUTION_LOGO"
                ? "uploaded-logo"
                : "uploaded-signature",
          };
        }
        return {};
      }
      if (path === "/api/core/institution-profile") return structuredClone(profile);
      if (path === "/api/core/countries")
        return [
          {
            id: "country",
            iso2Code: "ZW",
            iso3Code: "ZWE",
            name: "Zimbabwe",
            nationalityName: "Zimbabwean",
          },
        ];
      if (path === "/api/core/statistics")
        return { userCount: 4, roleCount: 3, permissionCount: 5, lookupSetCount: 2 };
      if (path.includes("/api/documents/uploads/")) {
        if (path.includes("/download"))
          return { downloadUrl: "https://storage.example.test/asset" };
        return { id: path.split("/").at(-1), originalFileName: "stored-image.png" };
      }
      if (path === "/api/core/users")
        return [
          {
            id: "user",
            username: "officer",
            displayName: "Officer",
            email: "officer@example.test",
            status: "ACTIVE",
          },
        ];
      if (path === "/api/core/roles")
        return [{ id: "role", code: "REGISTRY", name: "Registry", scope: "SYSTEM" }];
      if (path === "/api/core/workflows/tasks") return structuredClone(tasks);
      if (path === "/api/core/audit-events")
        return [
          {
            id: "audit",
            actorUserId: null,
            eventType: "PROFILE_UPDATED",
            subjectType: "INSTITUTION",
            subjectId: "institution",
            summary: "Branding updated",
            occurredAt: "2026-08-01T08:00:00Z",
          },
          {
            id: "actor",
            actorUserId: "user",
            eventType: "ROLE_ASSIGNED",
            subjectType: "USER",
            summary: "Role assigned",
            occurredAt: "2026-08-01T09:00:00Z",
          },
        ];
      if (path === "/api/core/login-events")
        return [
          {
            id: "login",
            username: "officer",
            occurredAt: "2026-08-01T08:00:00Z",
            outcome: "SUCCESS",
          },
        ];
      if (path === "/api/core/reports/overview")
        return {
          inventory: { userCount: 4, roleCount: 3 },
          auditEventsLast24Hours: 2,
          loginSessionsLast24Hours: 1,
        };
      throw new Error(`Unexpected ${path}`);
    },
  );
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CorePage, {
    global: {
      stubs: {
        ...registerStubs,
        EmhareFormSection: section,
        USeparator: true,
        EmhareJourneyStepper: true,
      },
    },
  });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function fileValue(label: string, value: unknown) {
  wrapper
    .findAllComponents(RegisterField)
    .find((field) => field.props("label") === label)!
    .vm.$emit("update:modelValue", value);
  await flushPromises();
}
async function save() {
  await clickButton(wrapper, "Save record");
}
describe("institution profile governance", () => {
  it("saves bank/contact/branding changes without dropping independent JSON metadata", async () => {
    await render();
    await clickButton(wrapper, "Edit profile");
    await setField(wrapper, "Operating name", "UZ revised");
    await setField(wrapper, "Email address", " new@example.test ");
    await setField(wrapper, "Phone number", " ");
    await setField(wrapper, "Website", " https://uz.example.test ");
    await setField(wrapper, "Official document header", " Official header ");
    await save();
    const body = writes()[0]![1].body;
    expect(body.name).toBe("UZ revised");
    expect(JSON.parse(body.contactDetailsJson)).toEqual({
      email: "new@example.test",
      website: "https://uz.example.test",
      extension: true,
    });
    expect(JSON.parse(body.brandingJson)).toEqual(
      expect.objectContaining({ documentHeader: "Official header", custom: true }),
    );
    const bank = JSON.parse(body.bankDetailsJson);
    expect(bank.custom).toBe("preserved");
    expect(bank.accounts[0]).toEqual(
      expect.objectContaining({ bankName: "CBZ", accountNumber: "USD123" }),
    );
    expect(bank.accounts[0]).not.toHaveProperty("swiftCode");
    expect(syncCoreUser).toHaveBeenCalledOnce();
    expect(showSuccess).toHaveBeenCalledWith(
      "Institution profile saved",
      "Institution details are updated.",
    );
  });
  it.each([
    "Institution code",
    "Operating name",
    "Legal name",
    "Registrar name",
    "Timezone",
    "Country",
  ])("blocks profiles without required %s", async (label) => {
    if (label === "Timezone") profile.timezone = "";
    if (label === "Country") profile.countryCode = "";
    await render();
    await clickButton(wrapper, "Edit profile");
    if (label !== "Timezone" && label !== "Country") await setField(wrapper, label, "");
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    wrapper.getComponent(RegisterDrawer).vm.$emit("submit");
    await flushPromises();
    expect(writes()).toHaveLength(0);
  });
  it.each([" ", "malformed", "null", "[]", "42"])(
    "tolerates invalid legacy JSON %s without exposing garbage fields",
    async (json) => {
      profile.contactDetailsJson = json;
      profile.brandingJson = json;
      profile.bankDetailsJson = json;
      await render();
      await clickButton(wrapper, "Edit profile");
      expect(wrapper.get('[data-label="Email address"] input').element).toHaveProperty("value", "");
      expect(wrapper.get('[data-label="Official document header"] input').element).toHaveProperty(
        "value",
        "University of Zimbabwe",
      );
      expect(wrapper.findAll('[data-label="Bank name"]')).toHaveLength(2);
      expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    },
  );
  it("converts legacy single-account bank data to currency-specific accounts on save", async () => {
    profile.bankDetailsJson = JSON.stringify({ ...account("USD"), extra: "preserved" });
    await render();
    await clickButton(wrapper, "Edit profile");
    expect(wrapper.findAll('[data-label="Account number"] input')[0]!.element).toHaveProperty(
      "value",
      " USD123 ",
    );
    await setField(wrapper, "Bank name", "ZWG bank", 1);
    await setField(wrapper, "Account number", "123ZWG", 1);
    await save();
    const bank = JSON.parse(writes()[0]![1].body.bankDetailsJson);
    expect(bank).not.toHaveProperty("bankName");
    expect(bank).not.toHaveProperty("accountNumber");
    expect(bank.extra).toBe("preserved");
    expect(bank.accounts).toHaveLength(2);
  });
  it("ignores malformed/unsupported bank entries while retaining valid USD and ZWG accounts", async () => {
    profile.bankDetailsJson = JSON.stringify({
      accounts: [
        null,
        3,
        [],
        { currencyCode: "EUR" },
        { ...account("USD"), currencyCode: "usd" },
        account("ZWG"),
      ],
    });
    await render();
    await clickButton(wrapper, "Edit profile");
    expect(wrapper.findAll('[data-label="Account number"]')).toHaveLength(2);
    await save();
    expect(
      JSON.parse(writes()[0]![1].body.bankDetailsJson).accounts.map(
        (item: { currencyCode: string }) => item.currencyCode,
      ),
    ).toEqual(["USD", "ZWG"]);
  });
  it("balances added currencies and prevents saving without a complete account in each", async () => {
    await render();
    await clickButton(wrapper, "Edit profile");
    await clickButton(wrapper, "Remove", 1);
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    await clickButton(wrapper, "Add bank account");
    expect(wrapper.findAll('[data-label="Account currency"] select')[1]!.element).toHaveProperty(
      "value",
      "ZWG",
    );
    await setField(wrapper, "Bank name", "Bank", 1);
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    await setField(wrapper, "Account number", "123", 1);
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(false);
    await clickButton(wrapper, "Add bank account");
    expect(wrapper.findAll('[data-label="Account currency"] select')[2]!.element).toHaveProperty(
      "value",
      "USD",
    );
    await clickButton(wrapper, "Remove", 2);
    await clickButton(wrapper, "Remove", 0);
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
  });
  it.each(["Choose logo", "Choose registrar signature"])(
    "validates %s file type, size and cleared values",
    async (label) => {
      await render();
      await clickButton(wrapper, "Edit profile");
      await fileValue(label, new File(["image"], "preview.png", { type: "image/png" }));
      expect(URL.createObjectURL).toHaveBeenCalledOnce();
      await fileValue(label, null);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:preview");
      await fileValue(label, new File(["pdf"], "invalid.pdf", { type: "application/pdf" }));
      expect(context.showError).toHaveBeenCalledWith(
        label === "Choose logo" ? "Logo not accepted" : "Signature not accepted",
        expect.any(String),
      );
      const large = new File(["image"], "large.png", { type: "image/png" });
      Object.defineProperty(large, "size", { value: 2 * 1024 * 1024 + 1 });
      await fileValue(label, [large]);
      expect(context.showError).toHaveBeenCalledWith(
        label === "Choose logo" ? "Logo is too large" : "Signature is too large",
        expect.any(String),
      );
    },
  );
  it.each(["logo", "signature", "both"])(
    "uploads %s with institution ownership then refreshes the shared shell",
    async (kind) => {
      await render();
      await clickButton(wrapper, "Edit profile");
      if (kind !== "signature")
        await fileValue("Choose logo", [new File(["logo"], "logo.png", { type: "image/png" })]);
      if (kind !== "logo")
        await fileValue(
          "Choose registrar signature",
          new File(["signature"], "signature.jpg", { type: "image/jpeg" }),
        );
      await save();
      const uploads = writes().filter(([path]) => path === "/api/documents/uploads");
      expect(uploads).toHaveLength(kind === "both" ? 2 : 1);
      for (const [, options] of uploads) {
        expect(options.body.get("ownerType")).toBe("INSTITUTION");
        expect(options.body.get("ownerId")).toBe("institution");
      }
      const profileWrites = writes().filter(([path]) => path === "/api/core/institution-profile");
      expect(profileWrites).toHaveLength(2);
      const branding = JSON.parse(profileWrites[1]![1].body.brandingJson);
      if (kind !== "signature") expect(branding.logoDocumentId).toBe("uploaded-logo");
      if (kind !== "logo") expect(branding.registrarSignatureDocumentId).toBe("uploaded-signature");
      expect(syncCoreUser).toHaveBeenCalledOnce();
      expect(showSuccess).toHaveBeenCalledWith(
        "Institution profile saved",
        "Institution details and brand assets are updated.",
      );
      expect(URL.revokeObjectURL).toHaveBeenCalled();
    },
  );
  it.each(["Choose logo", "Choose registrar signature"])(
    "refuses %s uploads if profile save returned no owning identity",
    async (label) => {
      delete profile.id;
      await render();
      await clickButton(wrapper, "Edit profile");
      await fileValue(label, new File(["image"], "image.png", { type: "image/png" }));
      await save();
      expect(context.showError).toHaveBeenCalledWith(
        "Save failed",
        expect.stringContaining("must be saved before"),
      );
      expect(writes().some(([path]) => path === "/api/documents/uploads")).toBe(false);
    },
  );
  it("loads and removes stored branding assets while preserving other settings", async () => {
    profile.brandingJson = JSON.stringify({
      logoDocumentId: "logo",
      registrarSignatureDocumentId: "signature",
      other: "keep",
    });
    await render();
    expect(wrapper.get("[data-emhare-institution-logo] img").attributes("src")).toBe(
      "https://storage.example.test/asset",
    );
    await clickButton(wrapper, "Edit profile");
    await clickButton(wrapper, "Remove", 3);
    await clickButton(wrapper, "Remove", 2);
    await save();
    const branding = JSON.parse(writes()[0]![1].body.brandingJson);
    expect(branding).not.toHaveProperty("logoDocumentId");
    expect(branding).not.toHaveProperty("registrarSignatureDocumentId");
    expect(branding.other).toBe("keep");
  });
  it.each(["logo", "signature"])("tolerates failed stored %s downloads", async (kind) => {
    profile.brandingJson = JSON.stringify({
      [kind === "logo" ? "logoDocumentId" : "registrarSignatureDocumentId"]: kind,
    });
    failPath = `/api/documents/uploads/${kind}`;
    await render();
    expect(context.showError).not.toHaveBeenCalled();
    await clickButton(wrapper, "Edit profile");
    expect(wrapper.find("img").exists()).toBe(false);
  });
  it("cleans temporary previews on cancel and unmount, restoring saved values", async () => {
    await render();
    await clickButton(wrapper, "Edit profile");
    await setField(wrapper, "Operating name", "Discard");
    await fileValue("Choose logo", new File(["logo"], "logo.png", { type: "image/png" }));
    await clickButton(wrapper, "Cancel");
    expect(URL.revokeObjectURL).toHaveBeenCalled();
    await clickButton(wrapper, "Edit profile");
    expect(wrapper.get('[data-label="Operating name"] input').element).toHaveProperty(
      "value",
      "University",
    );
    await fileValue(
      "Choose registrar signature",
      new File(["signature"], "signature.png", { type: "image/png" }),
    );
    wrapper.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });
  it("retains unsaved profile values when persistence fails", async () => {
    await render();
    await clickButton(wrapper, "Edit profile");
    await setField(wrapper, "Operating name", "Unsaved");
    failPath = "/api/core/institution-profile";
    await save();
    expect(context.showError).toHaveBeenCalledWith("Save failed", "Unavailable");
    expect(wrapper.get('[data-label="Operating name"] input').element).toHaveProperty(
      "value",
      "Unsaved",
    );
  });
});
describe("Core workflow and audit evidence", () => {
  it("claims an open task with the persisted version and no premature decision", async () => {
    await render();
    await clickButton(wrapper, "Workflow Tasks");
    await clickButton(wrapper, "Open task");
    expect(wrapper.text()).toContain("Institution-wide");
    await clickButton(wrapper, "Claim task");
    expect(writes()[0]).toEqual([
      "/api/core/workflows/tasks/task/claim",
      { method: "POST", body: { expectedVersion: 7 } },
    ]);
    expect(showSuccess).toHaveBeenCalledWith("Workflow task claimed", expect.any(String));
  });
  it.each(["APPROVED", "REJECTED", "RETURNED", "COMPLETED"])(
    "records immutable %s workflow evidence only after a comment",
    async (decisionCode) => {
      tasks[0] = {
        ...tasks[0]!,
        status: "CLAIMED",
        assigneeType: "ROLE",
        assignedRoleId: "role",
        assignedRoleName: "Registry",
        scopeType: "ACADEMIC_UNIT",
        academicUnitId: "unit",
        dueAt: "2026-08-01T08:00:00Z",
        claimedByUserName: "Officer",
      };
      await render();
      await clickButton(wrapper, "Workflow Tasks");
      await clickButton(wrapper, "Open task");
      expect(wrapper.text()).toContain("Science");
      expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
      await setField(wrapper, "Decision", decisionCode);
      await setField(wrapper, "Decision evidence", "  Source checked  ");
      await clickButton(wrapper, "Record decision");
      expect(writes()[0]).toEqual([
        "/api/core/workflows/tasks/task/decision",
        { method: "POST", body: { expectedVersion: 7, decisionCode, comment: "Source checked" } },
      ]);
    },
  );
  it.each(["COMPLETED", "CANCELLED"])("keeps %s task decisions read-only", async (status) => {
    tasks[0] = {
      ...tasks[0]!,
      status,
      scopeType: "ACADEMIC_UNIT",
      academicUnitId: "missing",
      decisions: [
        {
          id: "decision",
          decisionCode: "APPROVED",
          comment: "Approved evidence",
          decidedByUserName: "Officer",
          decidedAt: "2026-08-01T08:00:00Z",
        },
      ],
    };
    await render();
    await clickButton(wrapper, "Workflow Tasks");
    await clickButton(wrapper, "Open task");
    expect(wrapper.text()).toContain("Approved evidence");
    expect(wrapper.text()).toContain("Academic unit");
    expect(wrapper.getComponent(RegisterDrawer).props("submitDisabled")).toBe(true);
    wrapper.getComponent(RegisterDrawer).vm.$emit("submit");
    await flushPromises();
    expect(writes()).toHaveLength(0);
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("reports workflow load/save errors and allows refresh recovery", async () => {
    await render();
    failPath = "/api/core/workflows/tasks";
    await clickButton(wrapper, "Workflow Tasks");
    expect(context.showError).toHaveBeenCalledWith("Workspace could not be loaded", "Unavailable");
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    await clickButton(wrapper, "Open task");
    failPath = "/api/core/workflows/tasks/task/claim";
    await clickButton(wrapper, "Claim task");
    expect(context.showError).toHaveBeenCalledWith("Save failed", "Unavailable");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it("renders actor/system audit records and login history without mutations", async () => {
    await render();
    await clickButton(wrapper, "Audit & Reports");
    expect(wrapper.text()).toContain("Branding updated");
    expect(wrapper.text()).toContain("System");
    expect(wrapper.text()).toContain("user");
    expect(wrapper.text()).toContain("SUCCESS");
    expect(wrapper.text()).toContain("Audit activity · 24h: 2");
    expect(writes()).toHaveLength(0);
  });
});
