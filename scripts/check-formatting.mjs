// Author: Tinashe K

import { execFileSync, spawnSync } from "node:child_process";

const baseReference = process.env.QUALITY_BASE_REF || "origin/main";
const supportedExtensions = /\.(?:cjs|js|json|mjs|ts|vue)$/;
const trackedChanges = execFileSync(
  "git",
  ["diff", "--name-only", "--diff-filter=ACMR", baseReference, "--"],
  { encoding: "utf8" },
)
  .split("\n")
  .map((path) => path.trim())
  .filter((path) => path && supportedExtensions.test(path));
const untrackedFiles = execFileSync("git", ["ls-files", "--others", "--exclude-standard"], {
  encoding: "utf8",
})
  .split("\n")
  .map((path) => path.trim())
  .filter((path) => path && supportedExtensions.test(path));
const changedFiles = [...new Set([...trackedChanges, ...untrackedFiles])];

if (changedFiles.length === 0) {
  process.exit(0);
}

const prettierArguments = [
  process.argv.includes("--write") ? "--write" : "--check",
  ...changedFiles,
];
const result = spawnSync("npx", ["prettier", ...prettierArguments], {
  stdio: "inherit",
  shell: process.platform === "win32",
});

process.exit(result.status ?? 1);
