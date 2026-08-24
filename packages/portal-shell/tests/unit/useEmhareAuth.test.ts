// Author: Tinashe K

import { computed, ref } from "vue";
import type { User } from "oidc-client-ts";
import { beforeEach, describe, expect, it, vi } from "vitest";

const oidc = vi.hoisted(() => ({
  currentUser: null as any,
  callbackUser: null as any,
  getUser: vi.fn(),
  removeUser: vi.fn(),
  signinRedirect: vi.fn(),
  signinCallback: vi.fn(),
  signoutRedirect: vi.fn(),
}));

vi.mock("oidc-client-ts", () => ({
  UserManager: vi.fn(function () {
    return oidc;
  }),
  WebStorageStateStore: vi.fn(function () {
    return {};
  }),
}));

const state = new Map<string, ReturnType<typeof ref>>();
const fetch = vi.fn();

vi.stubGlobal("computed", computed);
vi.stubGlobal("useState", (key: string, initializer: () => unknown) => {
  if (!state.has(key)) state.set(key, ref(initializer()));
  return state.get(key);
});
vi.stubGlobal("$fetch", fetch);

function user(overrides: Record<string, unknown> = {}): User {
  return {
    expired: false,
    access_token: "access-token",
    profile: { name: "Student One", email: "student@uz.ac.zw" },
    state: "/student/records",
    ...overrides,
  } as unknown as User;
}

function profile(overrides: Record<string, unknown> = {}) {
  return {
    user: {
      id: "user-1",
      keycloakUserId: "keycloak-1",
      username: "student",
      email: "student@uz.ac.zw",
      displayName: "Student One",
      status: "ACTIVE",
    },
    roleAssignments: [
      {
        id: "assignment-1",
        roleId: "role-1",
        roleCode: "STUDENT",
        roleName: "Student",
      },
    ],
    realmRoles: ["student"],
    effectivePermissionCodes: ["STUDENT_RECORDS_READ"],
    ...overrides,
  };
}

describe("useEmhareAuth path-scoped sessions", () => {
  beforeEach(() => {
    state.clear();
    vi.clearAllMocks();
    (window as unknown as { happyDOM: { setURL: (url: string) => void } }).happyDOM.setURL(
      "http://localhost:3002/student",
    );
    localStorage.clear();
    sessionStorage.clear();
    oidc.currentUser = user();
    oidc.callbackUser = user();
    oidc.getUser.mockImplementation(async () => oidc.currentUser);
    oidc.signinCallback.mockImplementation(async () => oidc.callbackUser);
    fetch.mockResolvedValue(profile());
  });

  it("logs in to a safe student return path and synchronizes access", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();

    await auth.login("/student/records?year=2026");
    expect(oidc.signinRedirect).toHaveBeenCalledWith({
      state: "/student/records?year=2026",
    });
    expect(sessionStorage.getItem("emhare:returnTo")).toBe("/student/records?year=2026");

    await auth.loadUser();
    await auth.syncCoreUser();
    expect(auth.authenticated.value).toBe(true);
    expect(auth.accessToken.value).toBe("access-token");
    expect(auth.displayName.value).toBe("Student One");
    expect(auth.hasRole("STUDENT")).toBe(true);
    expect(auth.hasPermission("STUDENT_RECORDS_READ")).toBe(true);
    expect(auth.hasPermissionPrefix("STUDENT_RECORDS")).toBe(true);
    expect(auth.operationalAccess.value).toBe(false);
    expect(await auth.requireUser("/student")).toBe(true);
  });

  it("handles callback, signup, rejected sessions, expiry, and logout", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();

    expect(await auth.handleCallback()).toBe("/student/records");
    expect(auth.loading.value).toBe(false);
    await auth.signup("https://attacker.test/student");
    expect(oidc.signinRedirect).toHaveBeenLastCalledWith({
      state: "/student",
      prompt: "create",
    });

    fetch.mockRejectedValueOnce({ response: { status: 401 } });
    expect(await auth.syncCoreUser()).toBeNull();
    expect(oidc.removeUser).toHaveBeenCalled();

    oidc.currentUser = user({ expired: true });
    expect(await auth.loadUser()).toBeNull();
    await auth.restartLogin("/student");
    expect(oidc.signinRedirect).toHaveBeenCalled();

    await auth.logout();
    expect(oidc.signoutRedirect).toHaveBeenCalled();
  });

  it("recognizes operational and system administrator access", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();
    auth.currentUserProfile.value = profile({
      realmRoles: ["communications-author"],
      operationalAccess: true,
      roleAssignments: [],
    });
    expect(auth.operationalAccess.value).toBe(true);
    expect(auth.hasRole("communications-author")).toBe(true);

    auth.currentUserProfile.value = profile({ realmRoles: ["system-admin"] });
    expect(auth.isSystemAdministrator.value).toBe(true);
    expect(auth.hasPermission("ANY_PERMISSION")).toBe(true);
    expect(auth.hasPermissionPrefix("ANY")).toBe(true);
    expect(auth.hasRole("ANY_ROLE")).toBe(true);
  });

  it("covers profile fallbacks, inferred operations access, and guarded failures", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();

    auth.oidcUser.value = user({ profile: { email: "fallback@uz.ac.zw" } });
    expect(auth.displayName.value).toBe("fallback@uz.ac.zw");
    auth.oidcUser.value = user({ profile: {} });
    expect(auth.displayName.value).toBe("Operator");
    auth.currentUserProfile.value = profile({
      realmRoles: [],
      operationalAccess: undefined,
      roleAssignments: [
        { id: "assignment-1", roleId: "role-1", roleCode: "REGISTRY", roleName: "Registry" },
      ],
      effectivePermissionCodes: undefined,
    });
    expect(auth.operationalAccess.value).toBe(true);
    expect(auth.hasPermission("MISSING")).toBe(false);
    expect(auth.hasPermissionPrefix("MISSING")).toBe(false);
    expect(auth.hasRole("missing-role")).toBe(false);

    auth.oidcUser.value = null;
    expect(await auth.syncCoreUser()).toBeNull();
    fetch.mockRejectedValueOnce({ statusCode: 503 });
    auth.oidcUser.value = user();
    await expect(auth.syncCoreUser()).rejects.toEqual({ statusCode: 503 });

    oidc.currentUser = null;
    expect(await auth.requireUser("/student/records")).toBe(false);
    expect(oidc.signinRedirect).toHaveBeenCalledWith({ state: "/student/records" });
  });

  it("reuses stored portal paths and recovers when callback profile synchronization is rejected", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();
    localStorage.setItem("emhare:returnTo", "/student/finance");

    await auth.login("/");
    expect(oidc.signinRedirect).toHaveBeenCalledWith({ state: "/student/finance" });

    oidc.callbackUser = user({ state: undefined });
    sessionStorage.setItem("emhare:returnTo", "/student/modules");
    fetch.mockRejectedValueOnce({ response: { statusCode: 401 } });
    expect(await auth.handleCallback()).toBe("/student/modules");
    expect(oidc.removeUser).toHaveBeenCalled();
    expect(oidc.signinRedirect).toHaveBeenCalledWith({ state: "/student/modules" });
  });

  it("covers absent profile collections and default login and signup targets", async () => {
    const { useEmhareAuth } = await import("../../composables/useEmhareAuth");
    const auth = useEmhareAuth();
    auth.currentUserProfile.value = {
      ...profile(),
      roleAssignments: [],
      realmRoles: undefined,
      effectivePermissionCodes: undefined,
      operationalAccess: undefined,
    };
    expect([...auth.realmRoles.value]).toEqual([]);
    expect([...auth.localRoleCodes.value]).toEqual([]);
    expect([...auth.effectivePermissionCodes.value]).toEqual([]);
    expect(auth.operationalAccess.value).toBe(false);

    await auth.login();
    expect(oidc.signinRedirect).toHaveBeenCalledWith({ state: "/student" });
    localStorage.setItem("emhare:returnTo", "/student/finance");
    await auth.signup("/");
    expect(oidc.signinRedirect).toHaveBeenLastCalledWith({
      state: "/student/finance",
      prompt: "create",
    });

    auth.currentUserProfile.value = profile({
      realmRoles: ["registry-officer"],
      operationalAccess: undefined,
      roleAssignments: [],
    });
    expect(auth.operationalAccess.value).toBe(true);
  });
});
