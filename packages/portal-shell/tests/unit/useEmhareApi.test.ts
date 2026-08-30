// Author: Tinashe K
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import { useEmhareApi } from "../../composables/useEmhareApi";
const accessToken = ref<string | null>(null),
  requireUser = vi.fn(),
  restartLogin = vi.fn(),
  fetchRequest = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  accessToken.value = "current-token";
  vi.stubGlobal("useEmhareAuth", () => ({ accessToken, requireUser, restartLogin }));
  vi.stubGlobal("$fetch", fetchRequest);
  fetchRequest.mockResolvedValue({ id: "record" });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
describe("authenticated service requests", () => {
  it("defaults to the local gateway and preserves headers without allowing token override", async () => {
    const api = useEmhareApi();
    await expect(
      api.request("/api/core/me", {
        method: "POST",
        body: { name: "value" },
        headers: { "X-Request-Id": "trace", Authorization: "untrusted" },
      }),
    ).resolves.toEqual({ id: "record" });
    expect(fetchRequest).toHaveBeenCalledWith("http://localhost:8080/api/core/me", {
      method: "POST",
      body: { name: "value" },
      headers: { "X-Request-Id": "trace", Authorization: "Bearer current-token" },
    });
    expect(requireUser).not.toHaveBeenCalled();
  });
  it.each([true, false])(
    "requests authentication before fetching, token returned=%s",
    async (available) => {
      accessToken.value = null;
      requireUser.mockImplementation(async () => {
        if (available) accessToken.value = "new-token";
      });
      await useEmhareApi().request("/api/core/me");
      expect(requireUser).toHaveBeenCalledOnce();
      expect(fetchRequest.mock.calls[0]![1].headers).toEqual(
        available ? { Authorization: "Bearer new-token" } : {},
      );
    },
  );
  it("does not issue a request when authentication fails", async () => {
    accessToken.value = null;
    requireUser.mockRejectedValue(new Error("Login unavailable"));
    await expect(useEmhareApi().request("/api/core/me")).rejects.toThrow("Login unavailable");
    expect(fetchRequest).not.toHaveBeenCalled();
  });
  it.each([
    { response: { status: 401 } },
    { response: { statusCode: 401 } },
    { status: 401 },
    { statusCode: 401 },
  ])("restarts login on a propagated unauthorized response %j", async (error) => {
    fetchRequest.mockRejectedValue(error);
    await expect(useEmhareApi().request("/api/core/me")).rejects.toBe(error);
    expect(restartLogin).toHaveBeenCalledOnce();
  });
  it.each([null, "offline", { status: 403 }, { response: { status: 409 }, status: 401 }, {}])(
    "preserves non-authentication failures %j without restarting login",
    async (error) => {
      fetchRequest.mockRejectedValue(error);
      await expect(useEmhareApi().request("/api/core/me")).rejects.toBe(error);
      expect(restartLogin).not.toHaveBeenCalled();
    },
  );
});
describe("operator-facing API errors", () => {
  it.each([
    [
      {
        data: { detail: "Domain conflict", message: "lower priority", title: "title" },
        message: "fetch",
      },
      "Domain conflict",
    ],
    [{ data: { message: "Service message", title: "title" } }, "Service message"],
    [{ data: { title: "Service title" } }, "Service title"],
    [new Error("Transport failed"), "Transport failed"],
    [{ data: {} }, "Fallback"],
    [null, "Fallback"],
    ["raw text", "Fallback"],
  ])("selects the most useful error detail %j", (error, expected) => {
    expect(useEmhareApi().errorMessage(error, "Fallback")).toBe(expected);
  });
  it("translates indexed field violations without losing their business context", () => {
    expect(
      useEmhareApi().errorMessage({
        data: {
          violations: [{ field: "programmeChoices[0].programmeId", message: "is required" }],
        },
      }),
    ).toBe("Programme Choices 1 programme Id: is required");
    expect(
      useEmhareApi().errorMessage({
        data: { violations: [{}, { field: "email" }, { message: "Provide evidence" }] },
      }),
    ).toBe("• Email: Invalid value\n• Value: Provide evidence");
  });
  it("uses the normal fallback for empty violations or absent error", () => {
    expect(useEmhareApi().errorMessage({ data: { violations: [{}] } })).toBe(
      "The request could not be completed.",
    );
    expect(useEmhareApi().errorMessage(undefined)).toBe("The request could not be completed.");
  });
});
