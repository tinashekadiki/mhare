// Author: Tinashe K
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  evaluateOverallCoverage,
  parseBackendTotals,
  parseFrontendTotals,
  checkOverallCoverage,
} from "../../scripts/overall-coverage.mjs";

const metric = (covered: number, total = 100) => ({ covered, total });
const totals = (lines = 81, branches = 81) => ({
  lines: metric(lines),
  branches: metric(branches),
});

describe("overall coverage report discovery", () => {
  const temporaryDirectories: string[] = [];
  afterEach(() => {
    for (const directory of temporaryDirectories.splice(0)) rmSync(directory, { recursive: true });
    vi.restoreAllMocks();
  });
  function repository() {
    const root = mkdtempSync(join(tmpdir(), "emhare-overall-coverage-"));
    temporaryDirectories.push(root);
    for (const directory of ["services", "libraries", "coverage/frontend"])
      mkdirSync(join(root, directory), { recursive: true });
    writeFileSync(
      join(root, "coverage/frontend/lcov.info"),
      "SF:apps/example.ts\nDA:1,1\nBRDA:1,0,0,1\nend_of_record\n",
    );
    vi.spyOn(console, "log").mockImplementation(() => undefined);
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    return root;
  }
  function module(root: string, path: string, covered: number, missed: number) {
    mkdirSync(join(root, path, "src/main/java"), { recursive: true });
    const report = join(root, path, "target/site/jacoco/jacoco.xml");
    mkdirSync(dirname(report), { recursive: true });
    writeFileSync(
      report,
      `<report><counter type="LINE" missed="${missed}" covered="${covered}"/><counter type="BRANCH" missed="${missed}" covered="${covered}"/></report>`,
    );
  }
  it("weights real executable totals across services and production libraries", () => {
    const root = repository();
    module(root, "services/admissions", 95, 5);
    module(root, "libraries/audit", 0, 1);
    expect(checkOverallCoverage(root)).toBe(true);
    expect(console.log).toHaveBeenCalledWith(expect.stringContaining("(95/101)"));
  });
  it("does not let a small fully covered service hide a large uncovered library", () => {
    const root = repository();
    module(root, "services/admissions", 1, 0);
    module(root, "libraries/audit", 79, 21);
    expect(checkOverallCoverage(root)).toBe(false);
    expect(console.error).toHaveBeenCalledWith(expect.stringContaining("backend lines"));
  });
  it.each(["services/admissions", "libraries/audit"])(
    "fails closed when %s has no report",
    (path) => {
      const root = repository();
      module(root, "services/covered", 90, 10);
      mkdirSync(join(root, path, "src/main/java"), { recursive: true });
      expect(() => checkOverallCoverage(root)).toThrow(`Missing coverage for ${path}`);
    },
  );
  it("ignores non-Java directories and files but does not accept a repository without backend modules", () => {
    const root = repository();
    mkdirSync(join(root, "services/resources-only"));
    writeFileSync(join(root, "services/notes.txt"), "not a Java module");
    expect(() => checkOverallCoverage(root)).toThrow("No backend modules found");
    module(root, "services/covered", 90, 10);
    expect(checkOverallCoverage(root)).toBe(true);
  });
  it("refuses missing frontend evidence even when every backend module passes", () => {
    const root = repository();
    module(root, "services/covered", 90, 10);
    rmSync(join(root, "coverage/frontend/lcov.info"));
    expect(() => checkOverallCoverage(root)).toThrow(/ENOENT/);
  });
});

describe("overall coverage gate", () => {
  it("requires both metrics above 80 percent in each stack independently", () => {
    expect(evaluateOverallCoverage(totals(), totals()).passed).toBe(true);
    expect(evaluateOverallCoverage(totals(100, 100), totals(79, 79)).passed).toBe(false);
  });

  it.each(["frontend", "backend"])("rejects exactly 80 percent in %s", (stack) => {
    const result = evaluateOverallCoverage(
      stack === "frontend" ? totals(80) : totals(),
      stack === "backend" ? totals(81, 80) : totals(),
    );
    expect(result.passed).toBe(false);
    expect(result.failures.join(" ")).toContain(stack);
  });

  it("compares unrounded ratios, so 80.004 percent passes", () => {
    const coverage = { lines: metric(80004, 100000), branches: metric(80004, 100000) };
    expect(evaluateOverallCoverage(coverage, coverage).passed).toBe(true);
  });

  it("does not penalize a branchless stack, but rejects missing executable lines", () => {
    expect(
      evaluateOverallCoverage({ lines: metric(81), branches: metric(0, 0) }, totals()).passed,
    ).toBe(true);
    expect(
      evaluateOverallCoverage({ lines: metric(0, 0), branches: metric(0, 0) }, totals()).passed,
    ).toBe(false);
  });

  it("sums covered statements and branch outcomes from every LCOV source", () => {
    const report =
      "SF:apps/a.vue\nDA:1,2\nDA:2,0\nBRDA:1,0,0,3\nBRDA:1,0,1,-\nend_of_record\nSF:packages/b.ts\nDA:1,1\nBRDA:1,0,0,0\nend_of_record\n";
    expect(parseFrontendTotals(report)).toEqual({ lines: metric(2, 3), branches: metric(1, 3) });
  });

  it("does not double count repeated LCOV source records", () => {
    const source = "SF:apps/a.vue\nDA:1,2\nBRDA:1,0,0,1\nend_of_record\n";
    expect(() => parseFrontendTotals(source + source)).toThrow(/duplicate/i);
  });

  it("reads only JaCoCo report-level totals, not nested class or package totals", () => {
    const report =
      '<report><package name="example"><class name="Rule"><counter type="LINE" missed="1" covered="4"/></class><counter type="LINE" missed="1" covered="4"/></package><counter type="LINE" missed="1" covered="4"/><counter type="BRANCH" missed="2" covered="8"/></report>';
    expect(parseBackendTotals(report)).toEqual({ lines: metric(4, 5), branches: metric(8, 10) });
  });

  it("accepts a branchless JaCoCo report", () => {
    expect(
      parseBackendTotals('<report><counter type="LINE" missed="0" covered="3"/></report>'),
    ).toEqual({ lines: metric(3, 3), branches: metric(0, 0) });
  });

  it.each([
    "",
    "<report/>",
    '<report><counter type="LINE" missed="-1" covered="2"/></report>',
    '<report><counter type="LINE" missed="0" covered="NaN"/></report>',
  ])("fails closed for missing or invalid backend evidence: %s", (report) => {
    expect(() => parseBackendTotals(report)).toThrow();
  });

  it("fails closed on an empty frontend report", () => {
    expect(() => parseFrontendTotals("")).toThrow(/empty/i);
  });
});
