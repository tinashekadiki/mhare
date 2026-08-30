// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import NotificationsPage from "../../pages/operations/notifications.vue";
import type {
  NotificationRegister,
  NotificationTemplateSummary,
  NotificationConsentSummary,
  NotificationRequestSummary,
  NotificationEventInboxSummary,
} from "../../../../packages/portal-shell/types/notifications";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let register: NotificationRegister;
let failPath: string | undefined;
let users: {
  id: string;
  displayName: string;
  email: string;
  phoneNumber?: string;
  status: string;
}[];
const stamp = "2026-08-01T10:00:00Z";
const template = (
  overrides: Partial<NotificationTemplateSummary> = {},
): NotificationTemplateSummary => ({
  id: "template",
  code: "OFFER",
  templateVersion: 2,
  name: "Offer email",
  eventType: "offer.published",
  channel: "EMAIL",
  category: "TRANSACTIONAL",
  locale: "en-ZW",
  subjectTemplate: "Offer for {{name}}",
  bodyTemplate: "Dear {{name}}",
  status: "ACTIVE",
  preparedByUserId: "maker",
  approvedByUserId: "checker",
  approvedAt: stamp,
  approvalReason: "Approved",
  version: 7,
  ...overrides,
});
const consent = (
  overrides: Partial<NotificationConsentSummary> = {},
): NotificationConsentSummary => ({
  id: "consent",
  recipientUserId: "user",
  recipientKey: "stable-recipient",
  channel: "EMAIL",
  category: "MARKETING",
  status: "OPTED_IN",
  source: "SELF_SERVICE",
  evidenceReference: "consent-001",
  effectiveFrom: stamp,
  version: 6,
  ...overrides,
});
const request = (
  overrides: Partial<NotificationRequestSummary> = {},
): NotificationRequestSummary => ({
  id: "request",
  requestNumber: "NOT-001",
  idempotencyKey: "once",
  sourceService: "admissions",
  sourceEventId: "event",
  eventType: "offer.published",
  templateCode: "OFFER",
  templateVersion: 2,
  channel: "EMAIL",
  category: "TRANSACTIONAL",
  recipientUserId: "user",
  recipientKey: "user",
  recipientAddress: "user@example.test",
  subject: "Offer",
  body: "Dear student",
  priority: "NORMAL",
  status: "FAILED",
  consentDecision: "NOT_REQUIRED",
  scheduledAt: stamp,
  nextAttemptAt: null,
  attemptCount: 5,
  maxAttempts: 5,
  providerCode: "EMAIL",
  providerMessageId: null,
  providerDeliveryStatus: null,
  providerStatusAt: null,
  providerStatusDetail: null,
  sentAt: null,
  failedAt: stamp,
  lastErrorCode: "TIMEOUT",
  lastErrorMessage: "Provider unavailable",
  manualRetryByUserId: null,
  manualRetryAt: null,
  manualRetryReason: null,
  version: 9,
  ...overrides,
});
const event = (
  overrides: Partial<NotificationEventInboxSummary> = {},
): NotificationEventInboxSummary => ({
  id: "event",
  sourceService: "admissions",
  sourceEventId: "source-event",
  eventType: "offer.published",
  receivedAt: stamp,
  processedAt: null,
  status: "DEAD",
  processingError: "Unknown template",
  attemptCount: 5,
  maxAttempts: 5,
  nextAttemptAt: null,
  lastAttemptAt: stamp,
  manualRetryByUserId: null,
  manualRetryAt: null,
  manualRetryReason: null,
  version: 8,
  ...overrides,
});
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  confirm.mockResolvedValue({ isConfirmed: true, value: "Investigated and authorised" });
  failPath = undefined;
  register = {
    templates: [template()],
    consents: [consent()],
    requests: [request()],
    deliveryAttempts: [],
    eventInbox: [event()],
    providerCallbacks: [],
    inAppNotifications: [],
  };
  users = [
    {
      id: "user",
      displayName: "Applicant",
      email: "user@example.test",
      phoneNumber: "0771234567",
      status: "ACTIVE",
    },
    { id: "inactive", displayName: "Disabled", email: "disabled@example.test", status: "INACTIVE" },
  ];
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/notifications") return structuredClone(register);
    if (path === "/api/core/users") return structuredClone(users);
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(NotificationsPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function save() {
  await clickButton(wrapper, "Save");
}

describe("notification governance", () => {
  it("renders queue, sent, failure and consent suppression counts with state-specific actions", async () => {
    register.requests = [
      request(),
      request({
        id: "queued",
        status: "QUEUED",
        priority: "HIGH",
        providerDeliveryStatus: "ACCEPTED",
      }),
      request({
        id: "processing",
        status: "PROCESSING",
        priority: "URGENT",
        providerDeliveryStatus: "DELIVERED",
      }),
      request({ id: "sent", status: "SENT", providerDeliveryStatus: "BOUNCED" }),
      request({ id: "suppressed", status: "SUPPRESSED" }),
      request({ id: "cancelled", status: "CANCELLED" }),
    ];
    await render();
    expect(wrapper.text()).toContain("In delivery queue: 2");
    expect(wrapper.text()).toContain("Sent: 1");
    expect(wrapper.text()).toContain("Consent suppressed: 1");
    expect(
      wrapper.findAll("article button").filter((button) => button.text() === "Retry"),
    ).toHaveLength(1);
    expect(wrapper.get('[data-record-id="sent"]').findAll("button")).toHaveLength(0);
  });
  it("refreshes a failed register without losing retry access", async () => {
    failPath = "/api/notifications";
    await render();
    expect(wrapper.text()).toContain("Notifications unavailable");
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("Notifications unavailable");
    expect(wrapper.text()).toContain("NOT-001");
  });
  it("keeps notifications usable without the optional user directory", async () => {
    failPath = "/api/core/users";
    await render();
    await clickButton(wrapper, "Queue notification");
    expect(wrapper.get('[data-label="Recipient user"] select').findAll("option")).toHaveLength(1);
    expect(wrapper.text()).not.toContain("Notifications unavailable");
  });
  it.each(["", "Subject {{name}}"])(
    "creates a versioned template with optional subject %s",
    async (subject) => {
      await render();
      await clickButton(wrapper, "Templates");
      await clickButton(wrapper, "Prepare template");
      await setField(wrapper, "Code", "RESULTS");
      await setField(wrapper, "Version", "3");
      await setField(wrapper, "Name", "Results ready");
      await setField(wrapper, "Event type", "results.published");
      await setField(wrapper, "Channel", "SMS");
      await setField(wrapper, "Category", "WORKFLOW");
      await setField(wrapper, "Locale", "en");
      await setField(wrapper, "Subject template", subject);
      await setField(wrapper, "Body template", "Hello {{name}}");
      await save();
      expect(writes()[0]!).toEqual([
        "/api/notifications/templates",
        {
          method: "POST",
          body: {
            code: "RESULTS",
            templateVersion: 3,
            name: "Results ready",
            eventType: "results.published",
            channel: "SMS",
            category: "WORKFLOW",
            locale: "en",
            subjectTemplate: subject || null,
            bodyTemplate: "Hello {{name}}",
            expectedVersion: 0,
          },
        },
      ]);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Notification record saved" }),
      );
    },
  );
  it.each([null, "Existing subject"])(
    "edits only mutable draft fields and preserves version (%s)",
    async (subject) => {
      register.templates = [template({ status: "DRAFT", subjectTemplate: subject })];
      await render();
      await clickButton(wrapper, "Templates");
      await clickButton(wrapper, "Edit");
      expect(wrapper.get('[data-label="Code"] input').attributes("readonly")).toBeDefined();
      expect(wrapper.get('[data-label="Channel"] select').attributes("disabled")).toBeDefined();
      await setField(wrapper, "Name", "Revised offer");
      await save();
      expect(writes()[0]!).toEqual([
        "/api/notifications/templates/template",
        {
          method: "PUT",
          body: {
            name: "Revised offer",
            eventType: "offer.published",
            category: "TRANSACTIONAL",
            subjectTemplate: subject,
            bodyTemplate: "Dear {{name}}",
            expectedVersion: 7,
          },
        },
      ]);
    },
  );
  it.each([false, true])(
    "records %s identity-linked consent with optional evidence",
    async (linked) => {
      await render();
      await clickButton(wrapper, "Consent preferences");
      await clickButton(wrapper, "Record preference");
      if (linked) {
        await setField(wrapper, "Recipient user", "user");
        await setField(wrapper, "Evidence reference", "signed-form");
      } else await setField(wrapper, "Recipient key", "external@example.test");
      await setField(wrapper, "Preference", "OPTED_OUT");
      await save();
      expect(writes()[0]![1].body).toEqual(
        expect.objectContaining({
          recipientUserId: linked ? "user" : null,
          recipientKey: linked ? "user" : "external@example.test",
          evidenceReference: linked ? "signed-form" : null,
          status: "OPTED_OUT",
          expectedVersion: null,
        }),
      );
    },
  );
  it.each([false, true])(
    "updates existing %s identity consent without replacing its stable key",
    async (linked) => {
      register.consents = [
        consent({
          recipientUserId: linked ? "user" : null,
          evidenceReference: linked ? "signed" : null,
        }),
      ];
      await render();
      await clickButton(wrapper, "Consent preferences");
      await clickButton(wrapper, "Update");
      expect(wrapper.get('[data-label="Recipient key"] input').element).toHaveProperty(
        "value",
        "stable-recipient",
      );
      await setField(wrapper, "Preference", "OPTED_OUT");
      await save();
      expect(writes()[0]![1].body).toEqual(
        expect.objectContaining({
          recipientKey: "stable-recipient",
          recipientUserId: linked ? "user" : null,
          evidenceReference: linked ? "signed" : null,
          expectedVersion: 6,
        }),
      );
    },
  );
  it.each([
    ["EMAIL", "user@example.test"],
    ["SMS", "0771234567"],
    ["IN_APP", "user"],
  ] as const)(
    "queues %s to the directory-derived address with template metadata",
    async (channel, address) => {
      register.templates = [template({ channel, locale: "sn-ZW" })];
      await render();
      await clickButton(wrapper, "Queue notification");
      await setField(wrapper, "Recipient user", "user");
      expect(wrapper.get('[data-label="Delivery address"] input').element).toHaveProperty(
        "value",
        address,
      );
      expect(wrapper.get('[data-label="Recipient user"] select').text()).not.toContain("Disabled");
      await setField(wrapper, "Template variables (JSON)", ' {"name":"Tariro"} ');
      await save();
      expect(writes()[0]!).toEqual([
        "/api/notifications/requests",
        {
          method: "POST",
          body: expect.objectContaining({
            templateCode: "OFFER",
            channel,
            locale: "sn-ZW",
            eventType: "offer.published",
            recipientUserId: "user",
            recipientKey: "user",
            recipientAddress: address,
            sourceEventId: null,
            scheduledAt: null,
            variables: { name: "Tariro" },
            idempotencyKey: expect.any(String),
          }),
        },
      ]);
    },
  );
  it("updates a recipient address when switching template channel and tolerates missing phone", async () => {
    users[0]!.phoneNumber = undefined;
    register.templates.push(template({ id: "sms", channel: "SMS" }));
    await render();
    await clickButton(wrapper, "Queue notification");
    await setField(wrapper, "Recipient user", "user");
    await setField(wrapper, "Active template", "OFFER:SMS");
    expect(wrapper.get('[data-label="Delivery address"] input').element).toHaveProperty(
      "value",
      "",
    );
    await setField(wrapper, "Delivery address", "0770000000");
    await setField(wrapper, "Scheduled at", "2026-09-01T09:00");
    await setField(wrapper, "Priority", "HIGH");
    await save();
    expect(writes()[0]![1].body).toEqual(
      expect.objectContaining({
        channel: "SMS",
        recipientAddress: "0770000000",
        scheduledAt: new Date("2026-09-01T09:00").toISOString(),
        priority: "HIGH",
      }),
    );
  });
  it("rejects malformed variable JSON without enqueueing a request", async () => {
    await render();
    await clickButton(wrapper, "Queue notification");
    await setField(wrapper, "Template variables (JSON)", "not-json");
    await save();
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Notification record was not saved",
      "Template variables must be a valid JSON object.",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it.each([
    ["Templates", "Prepare template", "/api/notifications/templates"],
    ["Consent preferences", "Record preference", "/api/notifications/consents"],
    ["Delivery queue", "Queue notification", "/api/notifications/requests"],
  ])("preserves %s form on failed save", async (tab, button, path) => {
    await render();
    await clickButton(wrapper, tab);
    await clickButton(wrapper, button);
    failPath = path;
    await save();
    expect(context.showError).toHaveBeenCalledWith(
      "Notification record was not saved",
      "Unavailable",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it.each([
    [
      "Templates",
      "Activate",
      "/api/notifications/templates/template/transition",
      "Template transition failed",
      7,
    ],
    [
      "Templates",
      "Retire",
      "/api/notifications/templates/template/transition",
      "Template transition failed",
      7,
    ],
    [
      "Delivery queue",
      "Retry",
      "/api/notifications/requests/request/retry",
      "Notification action failed",
      9,
    ],
    [
      "Delivery queue",
      "Cancel",
      "/api/notifications/requests/request/cancel",
      "Notification action failed",
      9,
    ],
    [
      "Event inbox",
      "Retry",
      "/api/notifications/event-inbox/event/retry",
      "Notification event retry failed",
      8,
    ],
  ])(
    "governs %s %s with cancellation, evidence, version and failure recovery",
    async (tab, button, path, errorTitle, version) => {
      if (button === "Activate") register.templates = [template({ status: "DRAFT" })];
      await render();
      await clickButton(wrapper, tab);
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator(" ")).toBeTruthy();
      expect(options.inputValidator("Investigated and authorised")).toBeUndefined();
      if (tab === "Event inbox") expect(options.inputValidator("short")).toBeTruthy();
      failPath = String(path);
      await clickButton(wrapper, button);
      expect(context.showError).toHaveBeenCalledWith(errorTitle, "Unavailable");
      expect(writes()[0]![1].body).toEqual(
        expect.objectContaining({
          reason: "Investigated and authorised",
          expectedVersion: version,
        }),
      );
      failPath = undefined;
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(2);
      if (button === "Activate" || button === "Retire")
        expect(writes()[1]![1].body.targetStatus).toBe(
          button === "Activate" ? "ACTIVE" : "RETIRED",
        );
    },
  );
  it("shows append-only provider attempts, callbacks and read evidence without mutation controls", async () => {
    register.deliveryAttempts = [
      {
        id: "attempt",
        notificationRequestId: "request",
        attemptNumber: 1,
        providerCode: "SMTP",
        startedAt: stamp,
        completedAt: stamp,
        outcome: "PERMANENT_FAILURE",
        providerMessageId: null,
        errorCode: "BOUNCE",
        errorMessage: "Bad address",
        responseMetadata: {},
      },
    ];
    register.providerCallbacks = [
      {
        id: "callback",
        providerCode: "SMTP",
        providerEventId: "provider-event",
        providerMessageId: "message",
        deliveryStatus: "DELIVERED",
        occurredAt: stamp,
        receivedAt: stamp,
        notificationRequestId: "request",
        errorCode: null,
        errorMessage: null,
      },
    ];
    register.inAppNotifications = [
      {
        id: "unread",
        notificationRequestId: "request",
        recipientUserId: "user",
        recipientKey: "user",
        title: "Offer",
        body: "Ready",
        deliveredAt: stamp,
        readAt: null,
        readByUserId: null,
        version: 0,
      },
      {
        id: "read",
        notificationRequestId: "request",
        recipientUserId: "user",
        recipientKey: "user",
        title: "Result",
        body: "Ready",
        deliveredAt: stamp,
        readAt: stamp,
        readByUserId: "user",
        version: 1,
      },
    ];
    await render();
    await clickButton(wrapper, "Delivery attempts");
    expect(wrapper.text()).toContain("Permanent Failure");
    expect(wrapper.text()).toContain("Bad address");
    await clickButton(wrapper, "Provider evidence");
    expect(wrapper.text()).toContain("Delivered");
    await clickButton(wrapper, "In-app delivery");
    expect(wrapper.text()).toContain("Unread");
    expect(wrapper.text()).toContain("Read");
    expect(wrapper.findAll("article button")).toHaveLength(0);
    expect(writes()).toHaveLength(0);
  });
});
