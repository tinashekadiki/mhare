// Author: Tinashe K

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  inferPortalKind,
  portalCallbackPath,
  portalDestinationUrl,
  portalPrefix,
  sanitizePortalReturnPath,
} from "../../utils/portal-routing";

afterEach(() => vi.unstubAllGlobals());

describe("path-scoped portal routing", () => {
  it("uses production path callbacks for applicant, staff, and student portals", () => {
    expect(portalCallbackPath("student")).toBe("/auth/callback");
    expect(portalCallbackPath("applicant")).toBe("/applicant/auth/callback");
    expect(portalCallbackPath("staff")).toBe("/staff/auth/callback");
  });

  it("preserves only same-origin paths inside the active production portal", () => {
    const origin = "https://emhare.uz.ac.zw";
    expect(sanitizePortalReturnPath("/student/registration?step=modules", origin, "student")).toBe(
      "/student/registration?step=modules",
    );
    expect(sanitizePortalReturnPath("/staff/operations/communications", origin, "staff")).toBe(
      "/staff/operations/communications",
    );
    expect(sanitizePortalReturnPath("/student", origin, "applicant")).toBe("/applicant");
    expect(sanitizePortalReturnPath("https://attacker.test/staff", origin, "staff")).toBe("/staff");
    expect(sanitizePortalReturnPath(undefined, origin, "student")).toBe("/student");
    expect(sanitizePortalReturnPath("http://[", origin, "student")).toBe("/student");
    expect(sanitizePortalReturnPath("/student/records?year=2026#result", origin, "student")).toBe(
      "/student/records?year=2026#result",
    );
  });

  it("keeps local port development compatible without weakening production prefixes", () => {
    const localStaff = { hostname: "localhost", port: "3000" } as Location;
    const localApplicant = { hostname: "localhost", port: "3001" } as Location;
    expect(portalCallbackPath("staff", localStaff)).toBe("/auth/callback");
    expect(
      sanitizePortalReturnPath(
        "/operations/communications",
        "http://localhost:3000",
        "staff",
        localStaff,
      ),
    ).toBe("/operations/communications");
    expect(
      sanitizePortalReturnPath(
        "/applications/one",
        "http://localhost:3001",
        "applicant",
        localApplicant,
      ),
    ).toBe("/applications/one");
    expect(
      sanitizePortalReturnPath("/auth/callback", "http://localhost:3000", "staff", localStaff),
    ).toBe("/operations");
    expect(
      sanitizePortalReturnPath(null, "http://localhost:3001", "applicant", localApplicant),
    ).toBe("/");
  });

  it("infers the current portal from its path before using local ports", () => {
    expect(inferPortalKind({ pathname: "/staff/operations", port: "" } as Location)).toBe("staff");
    expect(inferPortalKind({ pathname: "/applicant/applications", port: "" } as Location)).toBe(
      "applicant",
    );
    expect(inferPortalKind({ pathname: "/", port: "3002" } as Location)).toBe("student");
    expect(inferPortalKind({ pathname: "/anything", port: "3001" } as Location)).toBe("applicant");
    expect(inferPortalKind({ pathname: "/anything", port: "3000" } as Location)).toBe("staff");
    expect(portalPrefix("student")).toBe("/student");
    expect(portalPrefix("applicant")).toBe("/applicant");
  });

  it("infers a portal from its explicit development identity", () => {
    expect(
      inferPortalKind(
        {
          origin: "http://127.0.0.1:3010",
          pathname: "/operations/academic-structure",
          port: "3010",
        } as Location,
        { VITE_EMHARE_PORTAL_KIND: "staff" },
      ),
    ).toBe("staff");
    expect(
      inferPortalKind(
        {
          origin: "http://localhost:3101",
          pathname: "/applications",
          port: "3101",
        } as Location,
        { VITE_EMHARE_APPLICANT_PORTAL_URL: "http://localhost:3101" },
      ),
    ).toBe("applicant");
  });

  it("uses explicit Vite portal destinations for isolated local servers", () => {
    const environment = {
      VITE_EMHARE_STAFF_PORTAL_URL: "http://localhost:3100",
      VITE_EMHARE_APPLICANT_PORTAL_URL: "http://localhost:3101",
      VITE_EMHARE_STUDENT_PORTAL_URL: "http://localhost:3102",
    };

    expect(portalDestinationUrl("staff", environment)).toBe("http://localhost:3100");
    expect(portalDestinationUrl("applicant", environment)).toBe("http://localhost:3101");
    expect(portalDestinationUrl("student", environment)).toBe("http://localhost:3102");
  });

  it("generates local destination URLs from the active browser origin", () => {
    vi.stubGlobal("window", {
      location: { hostname: "localhost", protocol: "http:" },
    });
    expect(portalDestinationUrl("student")).toBe("http://localhost:3002/student");
    expect(portalDestinationUrl("applicant")).toBe("http://localhost:3001/");
    expect(portalDestinationUrl("staff")).toBe("http://localhost:3000/");
  });

  it("recognizes loopback aliases and uses production path destinations for remote hosts", () => {
    const loopback = { hostname: "127.0.0.1", port: "3001" } as Location;
    expect(portalCallbackPath("applicant", loopback)).toBe("/auth/callback");
    expect(
      sanitizePortalReturnPath("/applications", "http://127.0.0.1:3001", "applicant", loopback),
    ).toBe("/applications");

    vi.stubGlobal("window", {
      location: { hostname: "emhare.uz.ac.zw", protocol: "https:" },
    });
    expect(portalDestinationUrl("student")).toBe("/student");
    expect(portalDestinationUrl("staff")).toBe("/staff");
  });
});
