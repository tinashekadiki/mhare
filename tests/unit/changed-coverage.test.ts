// Author: Tinashe K

import { afterEach, describe, expect, it } from 'vitest'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import {
  addUntrackedFiles,
  collectChangedLines,
  evaluateChangedCoverage,
  findMissingBackendCoverageModules,
  parseJacocoXml,
  parseLcov,
  parseUnifiedDiff
} from '../../scripts/changed-coverage.mjs'

const temporaryDirectories: string[] = []

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true })
  }
})

describe('changed coverage input collection', () => {
  it('collects added lines and ignores deleted-only hunks', () => {
    const changedLines = parseUnifiedDiff(`diff --git a/services/example/src/main/java/example/Rule.java b/services/example/src/main/java/example/Rule.java
--- a/services/example/src/main/java/example/Rule.java
+++ b/services/example/src/main/java/example/Rule.java
@@ -8,0 +9,2 @@
+first changed line
+second changed line
diff --git a/apps/admin-portal/pages/removed.vue b/apps/admin-portal/pages/removed.vue
--- a/apps/admin-portal/pages/removed.vue
+++ /dev/null
@@ -3,2 +0,0 @@
-removed line
-another removed line
`)

    expect([...changedLines.keys()]).toEqual([
      'services/example/src/main/java/example/Rule.java'
    ])
    expect([...changedLines.values()][0]).toEqual(new Set([9, 10]))
  })

  it('treats every line in an untracked production file as changed', () => {
    const repositoryRoot = mkdtempSync(join(tmpdir(), 'emhare-coverage-'))
    temporaryDirectories.push(repositoryRoot)
    const sourcePath = 'apps/example/composables/useExample.ts'
    mkdirSync(join(repositoryRoot, 'apps/example/composables'), { recursive: true })
    writeFileSync(join(repositoryRoot, sourcePath), 'export const one = 1\nexport const two = 2\n')

    const changedLines = addUntrackedFiles(new Map(), repositoryRoot, [sourcePath])

    expect(changedLines.get(sourcePath)).toEqual(new Set([1, 2]))
  })

  it('identifies changed Java modules that did not produce JaCoCo XML', () => {
    const changedLines = new Map([
      ['services/admissions-service/src/main/java/example/AdmissionsRule.java', new Set([1])],
      ['services/finance-service/src/main/java/example/FinanceRule.java', new Set([1])],
      ['packages/portal-shell/composables/useExample.ts', new Set([1])]
    ])

    expect(findMissingBackendCoverageModules(
      changedLines,
      new Set(['services/admissions-service'])
    )).toEqual(['services/finance-service'])
  })

  it('collects a diff larger than the default child-process buffer', () => {
    const repositoryRoot = mkdtempSync(join(tmpdir(), 'emhare-large-diff-'))
    temporaryDirectories.push(repositoryRoot)
    const sourcePath = 'packages/example/composables/useLargeDiff.ts'
    mkdirSync(join(repositoryRoot, 'packages/example/composables'), { recursive: true })
    execFileSync('git', ['init', '--quiet'], { cwd: repositoryRoot })
    execFileSync('git', ['config', 'user.name', 'Coverage Test'], { cwd: repositoryRoot })
    execFileSync('git', ['config', 'user.email', 'coverage@example.test'], { cwd: repositoryRoot })
    writeFileSync(join(repositoryRoot, sourcePath), `export const value = '${'a'.repeat(600_000)}'\n`)
    execFileSync('git', ['add', sourcePath], { cwd: repositoryRoot })
    execFileSync('git', ['commit', '--quiet', '-m', 'baseline'], { cwd: repositoryRoot })
    writeFileSync(join(repositoryRoot, sourcePath), `export const value = '${'b'.repeat(600_000)}'\n`)

    const changedLines = collectChangedLines(repositoryRoot, 'HEAD')

    expect(changedLines.get(sourcePath)).toEqual(new Set([1]))
  })
})

describe('coverage report parsing', () => {
  it('reads executable lines and branches from JaCoCo XML', () => {
    const coverage = parseJacocoXml(`<?xml version="1.0"?>
<report name="example">
  <package name="example/domain">
    <sourcefile name="Rule.java">
      <line nr="10" mi="0" ci="4" mb="0" cb="2"/>
      <line nr="11" mi="3" ci="0" mb="1" cb="1"/>
    </sourcefile>
  </package>
</report>`, 'services/example')

    expect(coverage.get('services/example/src/main/java/example/domain/Rule.java')?.get(10)).toEqual({
      lineCovered: true,
      branchesCovered: 2,
      branchesTotal: 2
    })
    expect(coverage.get('services/example/src/main/java/example/domain/Rule.java')?.get(11)).toEqual({
      lineCovered: false,
      branchesCovered: 1,
      branchesTotal: 2
    })
  })

  it('reads line and branch execution counts from LCOV', () => {
    const repositoryRoot = '/workspace/emhare'
    const coverage = parseLcov(`TN:
SF:/workspace/emhare/packages/portal-shell/composables/useExample.ts
DA:3,1
DA:4,0
BRDA:4,0,0,1
BRDA:4,0,1,-
end_of_record
`, repositoryRoot)

    expect(coverage.get('packages/portal-shell/composables/useExample.ts')?.get(3)).toEqual({
      lineCovered: true,
      branchesCovered: 0,
      branchesTotal: 0
    })
    expect(coverage.get('packages/portal-shell/composables/useExample.ts')?.get(4)).toEqual({
      lineCovered: false,
      branchesCovered: 1,
      branchesTotal: 2
    })
  })

  it('does not fabricate an uncovered line from an LCOV branch-only location', () => {
    const repositoryRoot = '/workspace/emhare'
    const filePath = 'apps/admin-portal/pages/operations/example.vue'
    const coverage = parseLcov(`TN:
SF:/workspace/emhare/${filePath}
DA:10,1
BRDA:11,0,0,1
BRDA:11,0,1,1
end_of_record
`, repositoryRoot)

    const result = evaluateChangedCoverage(
      new Map([[filePath, new Set([10, 11])]]),
      coverage
    )

    expect(result.totals).toEqual({
      linesCovered: 1,
      linesTotal: 1,
      branchesCovered: 2,
      branchesTotal: 2
    })
    expect(result.passed).toBe(true)
  })
})

describe('changed coverage evaluation', () => {
  it('passes when changed lines and branches meet the threshold', () => {
    const filePath = 'services/example/src/main/java/example/Rule.java'
    const result = evaluateChangedCoverage(
      new Map([[filePath, new Set([10, 11])]]),
      new Map([[filePath, new Map([
        [10, { lineCovered: true, branchesCovered: 2, branchesTotal: 2 }],
        [11, { lineCovered: true, branchesCovered: 0, branchesTotal: 0 }]
      ])]])
    )

    expect(result.passed).toBe(true)
    expect(result.totals).toEqual({
      linesCovered: 2,
      linesTotal: 2,
      branchesCovered: 2,
      branchesTotal: 2
    })
  })

  it('fails when either changed lines or changed branches fall below the threshold', () => {
    const filePath = 'packages/portal-shell/composables/useExample.ts'
    const result = evaluateChangedCoverage(
      new Map([[filePath, new Set([3, 4])]]),
      new Map([[filePath, new Map([
        [3, { lineCovered: true, branchesCovered: 1, branchesTotal: 2 }],
        [4, { lineCovered: false, branchesCovered: 0, branchesTotal: 0 }]
      ])]])
    )

    expect(result.passed).toBe(false)
    expect(result.files[0].uncoveredLines).toEqual([4])
    expect(result.files[0].partiallyCoveredBranchLines).toEqual([3])
  })

  it('does not penalise changed executable code when no branches exist', () => {
    const filePath = 'packages/portal-shell/composables/useExample.ts'
    const result = evaluateChangedCoverage(
      new Map([[filePath, new Set([3])]]),
      new Map([[filePath, new Map([
        [3, { lineCovered: true, branchesCovered: 0, branchesTotal: 0 }]
      ])]])
    )

    expect(result.passed).toBe(true)
    expect(result.totals.branchesTotal).toBe(0)
  })
})
