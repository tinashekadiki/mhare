// Author: Tinashe K

import { describe, expect, it } from "vitest";
import { communicationSlugPreview } from "../../utils/communication-slug";

describe("communicationSlugPreview", () => {
  it("creates a readable URL-safe preview without asking an author for a slug", () => {
    expect(communicationSlugPreview("Café research showcase 2026")).toBe(
      "cafe-research-showcase-2026",
    );
    expect(communicationSlugPreview("   ")).toBe("public-item");
  });

  it("keeps the preview inside the public slug limit", () => {
    expect(communicationSlugPreview("A".repeat(240))).toHaveLength(180);
  });
});
