#!/usr/bin/env node

// Author: Tinashe K

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawnSync } from "node:child_process";

const REQUIRED_COVERAGE_PERCENTAGE = 90;
const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function normalizePath(filePath) {
  return filePath.split(sep).join("/").replace(/^\.\//, "");
}

function decodeXml(value) {
  return value
    .replaceAll("&quot;", '"')
    .replaceAll("&apos;", "'")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&amp;", "&");
}

function parseAttributes(element) {
  const attributes = new Map();
  for (const match of element.matchAll(/([A-Za-z][A-Za-z0-9_-]*)="([^"]*)"/g)) {
    attributes.set(match[1], decodeXml(match[2]));
  }
  return attributes;
}

function isBackendProductionSource(filePath) {
  return /^(services|libraries)\/[^/]+\/src\/main\/java\/.+\.java$/.test(filePath);
}

function isFrontendProductionSource(filePath) {
  if (!/^(apps|packages)\/[^/]+\/.+\.(ts|vue)$/.test(filePath)) return false;
  return (
    !/(^|\/)(node_modules|\.nuxt|\.output|coverage|dist)(\/|$)/.test(filePath) &&
    !/\.(test|spec)\.ts$/.test(filePath) &&
    !filePath.endsWith(".d.ts")
  );
}

export function isProductionSource(filePath) {
  const normalizedPath = normalizePath(filePath);
  return isBackendProductionSource(normalizedPath) || isFrontendProductionSource(normalizedPath);
}

function backendModulePath(filePath) {
  const pathSegments = normalizePath(filePath).split("/");
  return pathSegments.slice(0, 2).join("/");
}

export function findMissingBackendCoverageModules(changedLinesByFile, coveredModulePaths) {
  const changedModulePaths = new Set(
    [...changedLinesByFile.keys()].filter(isBackendProductionSource).map(backendModulePath),
  );
  return [...changedModulePaths].filter((modulePath) => !coveredModulePaths.has(modulePath)).sort();
}

function normalizedDiffPath(rawPath) {
  const trimmedPath = rawPath.trim();
  if (trimmedPath === "/dev/null") return null;
  const unquotedPath =
    trimmedPath.startsWith('"') && trimmedPath.endsWith('"')
      ? JSON.parse(trimmedPath)
      : trimmedPath;
  return normalizePath(unquotedPath.replace(/^[ab]\//, ""));
}

export function parseUnifiedDiff(diffText) {
  const changedLinesByFile = new Map();
  let currentFile = null;
  let currentHunk = null;

  function recordCurrentHunk() {
    if (!currentHunk || !currentFile || !isProductionSource(currentFile)) return;
    const removedWithoutWhitespace = currentHunk.removedLines.join("").replaceAll(/\s/g, "");
    const addedWithoutWhitespace = currentHunk.addedLines.join("").replaceAll(/\s/g, "");
    const isFormattingOnly =
      currentHunk.removedLines.length > 0 &&
      currentHunk.addedLines.length > 0 &&
      removedWithoutWhitespace === addedWithoutWhitespace;
    if (isFormattingOnly) return;

    const changedLines = changedLinesByFile.get(currentFile);
    currentHunk.addedLineNumbers.forEach((lineNumber) => changedLines.add(lineNumber));
  }

  for (const line of diffText.split(/\r?\n/)) {
    if (line.startsWith("diff --git ")) {
      recordCurrentHunk();
      currentHunk = null;
      currentFile = null;
      continue;
    }

    if (line.startsWith("+++ ")) {
      recordCurrentHunk();
      currentHunk = null;
      currentFile = normalizedDiffPath(line.slice(4));
      if (currentFile && isProductionSource(currentFile) && !changedLinesByFile.has(currentFile)) {
        changedLinesByFile.set(currentFile, new Set());
      }
      continue;
    }

    if (line.startsWith("@@")) {
      recordCurrentHunk();
      const hunk = line.match(/\+(\d+)(?:,(\d+))?\s/);
      currentHunk = hunk
        ? {
            nextAddedLineNumber: Number(hunk[1]),
            removedLines: [],
            addedLines: [],
            addedLineNumbers: [],
          }
        : null;
      continue;
    }
    if (!currentHunk || !currentFile || !isProductionSource(currentFile)) continue;
    if (line.startsWith("+") && !line.startsWith("+++")) {
      currentHunk.addedLines.push(line.slice(1));
      currentHunk.addedLineNumbers.push(currentHunk.nextAddedLineNumber);
      currentHunk.nextAddedLineNumber += 1;
    } else if (line.startsWith("-") && !line.startsWith("---")) {
      currentHunk.removedLines.push(line.slice(1));
    } else if (line.startsWith(" ")) {
      currentHunk.nextAddedLineNumber += 1;
    }
  }

  recordCurrentHunk();

  for (const [filePath, changedLines] of changedLinesByFile) {
    if (changedLines.size === 0) changedLinesByFile.delete(filePath);
  }
  return changedLinesByFile;
}

export function parseWordDiff(diffText) {
  const changedLinesByFile = new Map();
  let currentFile = null;
  let currentLineNumber = null;

  for (const line of diffText.split(/\r?\n/)) {
    if (line.startsWith("+++ ")) {
      currentFile = normalizedDiffPath(line.slice(4));
      if (currentFile && isProductionSource(currentFile) && !changedLinesByFile.has(currentFile)) {
        changedLinesByFile.set(currentFile, new Set());
      }
      currentLineNumber = null;
      continue;
    }
    if (line.startsWith("@@")) {
      const hunk = line.match(/\+(\d+)(?:,(\d+))?\s/);
      currentLineNumber = hunk ? Number(hunk[1]) : null;
      continue;
    }
    if (line === "~") {
      if (currentLineNumber !== null) currentLineNumber += 1;
      continue;
    }
    if (
      currentFile &&
      currentLineNumber !== null &&
      isProductionSource(currentFile) &&
      line.startsWith("+") &&
      !line.startsWith("+++")
    ) {
      changedLinesByFile.get(currentFile).add(currentLineNumber);
    }
  }

  for (const [filePath, changedLines] of changedLinesByFile) {
    if (changedLines.size === 0) changedLinesByFile.delete(filePath);
  }
  return changedLinesByFile;
}

export function addUntrackedFiles(changedLinesByFile, repositoryRoot, untrackedFiles) {
  for (const untrackedFile of untrackedFiles) {
    const normalizedFile = normalizePath(untrackedFile);
    if (!isProductionSource(normalizedFile)) continue;
    const absoluteFile = resolve(repositoryRoot, normalizedFile);
    if (!existsSync(absoluteFile)) continue;
    const fileContents = readFileSync(absoluteFile, "utf8");
    const sourceLines = fileContents === "" ? [] : fileContents.split(/\r?\n/);
    if (sourceLines.at(-1) === "") sourceLines.pop();
    const changedLines = changedLinesByFile.get(normalizedFile) ?? new Set();
    sourceLines.forEach((_, index) => changedLines.add(index + 1));
    if (changedLines.size > 0) changedLinesByFile.set(normalizedFile, changedLines);
  }
  return changedLinesByFile;
}

function runGit(repositoryRoot, argumentsList) {
  const result = spawnSync("git", argumentsList, {
    cwd: repositoryRoot,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(result.stderr.trim() || `git ${argumentsList.join(" ")} failed`);
  }
  return result.stdout;
}

export function collectChangedLines(repositoryRoot, baseReference) {
  runGit(repositoryRoot, ["rev-parse", "--verify", `${baseReference}^{commit}`]);
  const diffText = runGit(repositoryRoot, [
    "-c",
    "core.quotePath=false",
    "diff",
    "--word-diff=porcelain",
    "--word-diff-regex=[[:alnum:]_]+|[^[:space:][:alnum:]_]",
    "--no-ext-diff",
    "--find-renames",
    baseReference,
    "--",
  ]);
  const changedLinesByFile = parseWordDiff(diffText);
  const untrackedFiles = runGit(repositoryRoot, ["ls-files", "--others", "--exclude-standard"])
    .split(/\r?\n/)
    .filter(Boolean);
  return addUntrackedFiles(changedLinesByFile, repositoryRoot, untrackedFiles);
}

function coverageFilePath(repositoryRoot, sourceFile) {
  if (isAbsolute(sourceFile)) return normalizePath(relative(repositoryRoot, sourceFile));
  return normalizePath(sourceFile);
}

function ensureFileCoverage(coverageByFile, filePath) {
  if (!coverageByFile.has(filePath)) coverageByFile.set(filePath, new Map());
  return coverageByFile.get(filePath);
}

export function parseJacocoXml(xmlText, modulePath) {
  const coverageByFile = new Map();
  for (const packageMatch of xmlText.matchAll(
    /<package\s+name="([^"]*)"[^>]*>([\s\S]*?)<\/package>/g,
  )) {
    const packageName = decodeXml(packageMatch[1]);
    const packageBody = packageMatch[2];
    for (const sourceMatch of packageBody.matchAll(
      /<sourcefile\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/sourcefile>/g,
    )) {
      const sourceName = decodeXml(sourceMatch[1]);
      const filePath = normalizePath(
        [modulePath, "src/main/java", packageName, sourceName].filter(Boolean).join("/"),
      );
      const lineCoverage = ensureFileCoverage(coverageByFile, filePath);
      for (const lineMatch of sourceMatch[2].matchAll(/<line\s+([^>]*?)\/>/g)) {
        const attributes = parseAttributes(lineMatch[1]);
        const lineNumber = Number(attributes.get("nr"));
        lineCoverage.set(lineNumber, {
          lineCovered: Number(attributes.get("ci") ?? 0) > 0,
          branchesCovered: Number(attributes.get("cb") ?? 0),
          branchesTotal: Number(attributes.get("cb") ?? 0) + Number(attributes.get("mb") ?? 0),
        });
      }
    }
  }
  return coverageByFile;
}

export function parseLcov(lcovText, repositoryRoot) {
  const coverageByFile = new Map();
  let currentFileCoverage = null;

  for (const line of lcovText.split(/\r?\n/)) {
    if (line.startsWith("SF:")) {
      const filePath = coverageFilePath(repositoryRoot, line.slice(3));
      currentFileCoverage = ensureFileCoverage(coverageByFile, filePath);
      continue;
    }
    if (!currentFileCoverage) continue;

    if (line.startsWith("DA:")) {
      const [lineNumberText, executionCountText] = line.slice(3).split(",");
      const lineNumber = Number(lineNumberText);
      const existingCoverage = currentFileCoverage.get(lineNumber) ?? {
        lineCovered: null,
        branchesCovered: 0,
        branchesTotal: 0,
      };
      existingCoverage.lineCovered = Number(executionCountText) > 0;
      currentFileCoverage.set(lineNumber, existingCoverage);
      continue;
    }

    if (line.startsWith("BRDA:")) {
      const [lineNumberText, , , executionCountText] = line.slice(5).split(",");
      const lineNumber = Number(lineNumberText);
      const existingCoverage = currentFileCoverage.get(lineNumber) ?? {
        lineCovered: null,
        branchesCovered: 0,
        branchesTotal: 0,
      };
      existingCoverage.branchesTotal += 1;
      if (executionCountText !== "-" && Number(executionCountText) > 0) {
        existingCoverage.branchesCovered += 1;
      }
      currentFileCoverage.set(lineNumber, existingCoverage);
    }
  }
  return coverageByFile;
}

export function mergeCoverage(targetCoverage, additionalCoverage) {
  for (const [filePath, additionalLines] of additionalCoverage) {
    const targetLines = ensureFileCoverage(targetCoverage, filePath);
    for (const [lineNumber, additionalMetric] of additionalLines) {
      const targetMetric = targetLines.get(lineNumber);
      if (!targetMetric) {
        targetLines.set(lineNumber, { ...additionalMetric });
        continue;
      }
      targetLines.set(lineNumber, {
        lineCovered:
          targetMetric.lineCovered === true || additionalMetric.lineCovered === true
            ? true
            : targetMetric.lineCovered === false || additionalMetric.lineCovered === false
              ? false
              : null,
        branchesCovered: Math.max(targetMetric.branchesCovered, additionalMetric.branchesCovered),
        branchesTotal: Math.max(targetMetric.branchesTotal, additionalMetric.branchesTotal),
      });
    }
  }
  return targetCoverage;
}

function percentage(covered, total) {
  return total === 0 ? null : (covered / total) * 100;
}

function metricDisplay(covered, total) {
  const calculatedPercentage = percentage(covered, total);
  return calculatedPercentage === null
    ? "N/A"
    : `${covered}/${total} (${calculatedPercentage.toFixed(2)}%)`;
}

export function evaluateChangedCoverage(
  changedLinesByFile,
  coverageByFile,
  threshold = REQUIRED_COVERAGE_PERCENTAGE,
) {
  const totals = {
    linesCovered: 0,
    linesTotal: 0,
    branchesCovered: 0,
    branchesTotal: 0,
  };
  const files = [];

  for (const [filePath, changedLines] of [...changedLinesByFile].sort(([left], [right]) =>
    left.localeCompare(right),
  )) {
    const fileCoverage = coverageByFile.get(filePath);
    const fileResult = {
      filePath,
      linesCovered: 0,
      linesTotal: 0,
      branchesCovered: 0,
      branchesTotal: 0,
      uncoveredLines: [],
      partiallyCoveredBranchLines: [],
    };

    if (fileCoverage) {
      for (const lineNumber of [...changedLines].sort((left, right) => left - right)) {
        const metric = fileCoverage.get(lineNumber);
        if (!metric) continue;
        if (metric.lineCovered !== null) {
          fileResult.linesTotal += 1;
          if (metric.lineCovered) fileResult.linesCovered += 1;
          else fileResult.uncoveredLines.push(lineNumber);
        }

        fileResult.branchesTotal += metric.branchesTotal;
        fileResult.branchesCovered += metric.branchesCovered;
        if (metric.branchesTotal > metric.branchesCovered) {
          fileResult.partiallyCoveredBranchLines.push(lineNumber);
        }
      }
    }

    totals.linesCovered += fileResult.linesCovered;
    totals.linesTotal += fileResult.linesTotal;
    totals.branchesCovered += fileResult.branchesCovered;
    totals.branchesTotal += fileResult.branchesTotal;
    files.push(fileResult);
  }

  const linePercentage = percentage(totals.linesCovered, totals.linesTotal);
  const branchPercentage = percentage(totals.branchesCovered, totals.branchesTotal);
  return {
    passed:
      (linePercentage === null || linePercentage >= threshold) &&
      (branchPercentage === null || branchPercentage >= threshold),
    threshold,
    totals,
    files,
  };
}

function findFiles(directoryPath, predicate) {
  if (!existsSync(directoryPath)) return [];
  const matchingFiles = [];
  for (const entry of readdirSync(directoryPath, { withFileTypes: true })) {
    const entryPath = resolve(directoryPath, entry.name);
    if (entry.isDirectory()) matchingFiles.push(...findFiles(entryPath, predicate));
    else if (predicate(entryPath)) matchingFiles.push(entryPath);
  }
  return matchingFiles;
}

function loadCoverage(repositoryRoot, changedLinesByFile) {
  const coverageByFile = new Map();
  const hasBackendChanges = [...changedLinesByFile.keys()].some(isBackendProductionSource);
  const hasFrontendChanges = [...changedLinesByFile.keys()].some(isFrontendProductionSource);

  if (hasBackendChanges) {
    const jacocoReports = [
      ...findFiles(resolve(repositoryRoot, "services"), (filePath) =>
        filePath.endsWith("/target/site/jacoco/jacoco.xml"),
      ),
      ...findFiles(resolve(repositoryRoot, "libraries"), (filePath) =>
        filePath.endsWith("/target/site/jacoco/jacoco.xml"),
      ),
    ];
    if (jacocoReports.length === 0) {
      throw new Error(
        "No JaCoCo XML reports were found. Run mvn verify before the changed-coverage gate.",
      );
    }
    const coveredModulePaths = new Set(
      jacocoReports.map((reportPath) => {
        const moduleRoot = reportPath.slice(0, -"/target/site/jacoco/jacoco.xml".length);
        return normalizePath(relative(repositoryRoot, moduleRoot));
      }),
    );
    const missingModulePaths = findMissingBackendCoverageModules(
      changedLinesByFile,
      coveredModulePaths,
    );
    if (missingModulePaths.length > 0) {
      throw new Error(
        `JaCoCo XML is missing for changed modules: ${missingModulePaths.join(", ")}. Add or run tests so every changed module produces coverage during mvn verify.`,
      );
    }
    for (const reportPath of jacocoReports) {
      const moduleRoot = reportPath.slice(0, -"/target/site/jacoco/jacoco.xml".length);
      const modulePath = normalizePath(relative(repositoryRoot, moduleRoot));
      mergeCoverage(coverageByFile, parseJacocoXml(readFileSync(reportPath, "utf8"), modulePath));
    }
  }

  if (hasFrontendChanges) {
    const lcovPath = resolve(repositoryRoot, "coverage/frontend/lcov.info");
    if (!existsSync(lcovPath)) {
      throw new Error(
        "Frontend LCOV output was not found. Run npm run test:unit:coverage before the changed-coverage gate.",
      );
    }
    mergeCoverage(coverageByFile, parseLcov(readFileSync(lcovPath, "utf8"), repositoryRoot));
  }

  return coverageByFile;
}

function printResult(result) {
  for (const file of result.files) {
    if (file.linesTotal === 0 && file.branchesTotal === 0) continue;
    console.log(
      `${file.filePath}: lines ${metricDisplay(file.linesCovered, file.linesTotal)}, branches ${metricDisplay(file.branchesCovered, file.branchesTotal)}`,
    );
    if (file.uncoveredLines.length > 0) {
      console.log(`  uncovered lines: ${file.uncoveredLines.join(", ")}`);
    }
    if (file.partiallyCoveredBranchLines.length > 0) {
      console.log(
        `  uncovered branches on lines: ${[...new Set(file.partiallyCoveredBranchLines)].join(", ")}`,
      );
    }
  }
  console.log(
    `Changed executable lines: ${metricDisplay(result.totals.linesCovered, result.totals.linesTotal)}`,
  );
  console.log(
    `Changed branches: ${metricDisplay(result.totals.branchesCovered, result.totals.branchesTotal)}`,
  );
  console.log(
    `Required minimum: ${result.threshold.toFixed(2)}% for lines and branches when applicable`,
  );
}

export function runChangedCoverageGate(repositoryRoot = REPOSITORY_ROOT) {
  const baseReference = process.env.QUALITY_BASE_REF || "HEAD";
  const changedLinesByFile = collectChangedLines(repositoryRoot, baseReference);
  if (changedLinesByFile.size === 0) {
    console.log(
      `No changed backend or frontend production source was found relative to ${baseReference}.`,
    );
    return 0;
  }

  const coverageByFile = loadCoverage(repositoryRoot, changedLinesByFile);
  const result = evaluateChangedCoverage(changedLinesByFile, coverageByFile);
  printResult(result);
  return result.passed ? 0 : 1;
}

const invokedDirectly =
  process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (invokedDirectly) {
  try {
    process.exitCode = runChangedCoverageGate();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
