// Author: Tinashe K

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = readFileSync(
  resolve("apps/applicant-portal/pages/applications/[applicationId].vue"),
  "utf8",
);
const identityMismatchPanelSource = readFileSync(
  resolve("packages/portal-shell/components/domain/admissions/EmhareIdentityNameMismatchPanel.vue"),
  "utf8",
);

describe("applicant evidence-first qualification capture", () => {
  it("keeps identity evidence in Personal Details and hides an empty generic document step", () => {
    expect(source).toContain("personalEvidenceRequirements");
    expect(source).toContain('requirement.captureSectionCode === "PERSONAL_DETAILS"');
    expect(source).toContain(
      'section.code !== "DOCUMENTS" || supportingDocumentRequirements.value.length > 0',
    );
    expect(source).toContain("personalFieldsDisabled");
  });

  it("starts school qualifications with eight or three rows and saves one aggregate request", () => {
    expect(source).toContain('kind === "O_LEVEL" ? 8 : 3');
    expect(source).toContain("qualification-aggregates");
    expect(source).toContain("results: resultForms.value.map");
    expect(source).not.toContain(
      "Capture the examination sitting before adding its subject results",
    );
  });

  it("maps certificate and masters awards onto the existing eligibility levels", () => {
    expect(source).toContain('CERTIFICATE: { level: "OTHER", awardTypeCode: "CERTIFICATE" }');
    expect(source).toContain('MASTERS: { level: "DEGREE", awardTypeCode: "MASTERS" }');
  });

  it("keeps a persistent identity mismatch workflow without blocking the draft", () => {
    expect(source).toContain("EmhareIdentityNameMismatchPanel");
    expect(source).toContain("identityNameMismatch");
    expect(identityMismatchPanelSource).toContain("Identity name mismatch");
    expect(identityMismatchPanelSource).toContain("Replace document");
    expect(identityMismatchPanelSource).toContain("Correct OCR reading");
    expect(identityMismatchPanelSource).toContain("Request official-name correction");
    expect(identityMismatchPanelSource).toContain("You can continue completing this draft");
    expect(source).toContain("hydrateExistingIdentityNameMismatch");
    expect(source).toContain("hydratedIdentityDocumentIds");
    expect(source).not.toContain("identityNameMismatch.value ? false");
  });
});
