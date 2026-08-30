#!/usr/bin/env node
// Author: Tinashe K
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseLcov } from "./changed-coverage.mjs";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const emptyTotals = () => ({ lines: { covered: 0, total: 0 }, branches: { covered: 0, total: 0 } });

export function parseFrontendTotals(report) {
  const sources = [...report.matchAll(/^SF:(.+)$/gm)].map((match) => match[1]);
  if (!sources.length) throw new Error("Frontend coverage report is empty.");
  if (new Set(sources).size !== sources.length)
    throw new Error("Duplicate frontend coverage source.");
  const totals = emptyTotals();
  for (const lines of parseLcov(report, repositoryRoot).values()) {
    for (const line of lines.values()) {
      if (line.lineCovered !== null) {
        totals.lines.total += 1;
        totals.lines.covered += Number(line.lineCovered);
      }
      totals.branches.total += line.branchesTotal;
      totals.branches.covered += line.branchesCovered;
    }
  }
  return totals;
}

export function parseBackendTotals(report) {
  if (!report.trim().endsWith("</report>")) throw new Error("Incomplete JaCoCo report.");
  const packageEnd = report.lastIndexOf("</package>");
  const reportCounters = packageEnd < 0 ? report : report.slice(packageEnd + "</package>".length);
  const totals = emptyTotals();
  for (const [type, name] of [
    ["LINE", "lines"],
    ["BRANCH", "branches"],
  ]) {
    const counter = [...reportCounters.matchAll(/<counter\s+([^>]+)\/>/g)].find((match) =>
      match[1].includes(`type="${type}"`),
    );
    if (!counter && type === "LINE") throw new Error("JaCoCo line totals are missing.");
    if (!counter) continue;
    const covered = Number(counter[1].match(/covered="([^"]+)"/)?.[1]);
    const missed = Number(counter[1].match(/missed="([^"]+)"/)?.[1]);
    if (![covered, missed].every((value) => Number.isSafeInteger(value) && value >= 0))
      throw new Error("Invalid JaCoCo counters.");
    totals[name] = { covered, total: covered + missed };
  }
  return totals;
}

export function evaluateOverallCoverage(frontend, backend) {
  const failures = [];
  for (const [stack, totals] of [
    ["frontend", frontend],
    ["backend", backend],
  ]) {
    if (!totals.lines.total) failures.push(`${stack}: no executable lines in coverage evidence`);
    for (const name of ["lines", "branches"]) {
      const { covered, total } = totals[name];
      if (total > 0 && covered * 100 <= total * 80)
        failures.push(
          `${stack} ${name}: ${((covered / total) * 100).toFixed(2)}% must be above 80%`,
        );
    }
  }
  return { passed: failures.length === 0, failures };
}

export function checkOverallCoverage(root = repositoryRoot) {
  const backend = emptyTotals();
  let moduleCount = 0;
  for (const group of ["services", "libraries"]) {
    for (const entry of readdirSync(join(root, group), { withFileTypes: true })) {
      const modulePath = join(root, group, entry.name);
      if (!entry.isDirectory() || !existsSync(join(modulePath, "src/main/java"))) continue;
      const reportPath = join(modulePath, "target/site/jacoco/jacoco.xml");
      if (!existsSync(reportPath))
        throw new Error(`Missing coverage for ${group}/${entry.name}; run mvn verify first.`);
      const totals = parseBackendTotals(readFileSync(reportPath, "utf8"));
      for (const name of ["lines", "branches"]) {
        backend[name].covered += totals[name].covered;
        backend[name].total += totals[name].total;
      }
      moduleCount += 1;
    }
  }
  if (!moduleCount) throw new Error("No backend modules found.");
  const frontend = parseFrontendTotals(
    readFileSync(join(root, "coverage/frontend/lcov.info"), "utf8"),
  );
  for (const [stack, totals] of [
    ["Frontend", frontend],
    ["Backend", backend],
  ]) {
    const display = (metric) =>
      metric.total
        ? `${((metric.covered / metric.total) * 100).toFixed(2)}% (${metric.covered}/${metric.total})`
        : "N/A";
    console.log(`${stack}: lines ${display(totals.lines)}; branches ${display(totals.branches)}`);
  }
  const result = evaluateOverallCoverage(frontend, backend);
  for (const failure of result.failures) console.error(failure);
  return result.passed;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    process.exitCode = checkOverallCoverage() ? 0 : 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
