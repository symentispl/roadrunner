# JReleaser Changelog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure JReleaser to generate structured, categorized GitHub Release notes from Conventional Commits on every release.

**Architecture:** Add a `changelog` block to `release.github` in `roadrunner-app/jreleaser.yml`. JReleaser's built-in `conventional-commits` preset categorizes commits by type; `hide.uncategorized: true` silently drops Dependabot and `[release]` commits that don't match any CC prefix. No new tools, no workflow changes.

**Tech Stack:** JReleaser (jreleaser-maven-plugin, already declared in `roadrunner-app/pom.xml`)

---

## File Map

| Action | Path | Purpose |
|--------|------|---------|
| Modify | `roadrunner-app/jreleaser.yml` | Add `changelog` block under `release.github` |

---

## Task 1: Add changelog configuration to jreleaser.yml

**Files:**
- Modify: `roadrunner-app/jreleaser.yml`

- [ ] **Step 1: Read the current file**

Open `roadrunner-app/jreleaser.yml` and locate the `release.github` block. It currently ends after `skipTag: true`. The full current content is:

```yaml
project:
  name: roadrunner
  description: A Java load generator built on virtual threads
  authors:
    - Symentis.pl
  license: Apache-2.0
  inceptionYear: '2024'
  java:
    groupId: io.roadrunner
    version: '25'

release:
  github:
    owner: symentispl
    name: roadrunner
    branch: master
    overwrite: false
    draft: false
    skipTag: true

assemble:
  ...
```

- [ ] **Step 2: Add the `changelog` block immediately after `skipTag: true`**

The `release.github` section should become:

```yaml
release:
  github:
    owner: symentispl
    name: roadrunner
    branch: master
    overwrite: false
    draft: false
    skipTag: true
    changelog:
      formatted: ALWAYS
      preset: conventional-commits
      hide:
        uncategorized: true
        contributors:
          - dependabot[bot]
          - release-bot
```

`formatted: ALWAYS` — always render the changelog as structured Markdown in the GitHub Release body, even if there are no categorized commits.

`preset: conventional-commits` — maps CC prefixes to sections:
- `feat:` → 🚀 Features
- `fix:` → 🐛 Bug Fixes
- `perf:` → ⚡ Performance
- `refactor:` → ♻️ Refactoring
- `docs:` → 📝 Documentation
- `build:`, `ci:`, `chore:` → 🔧 Build

`hide.uncategorized: true` — commits without a CC prefix are dropped silently. This filters out Dependabot `Bump ...` commits and maven-release-plugin `[release] prepare ...` commits automatically.

`hide.contributors` — suppresses `dependabot[bot]` and `release-bot` from the contributors footer. `release-bot` matches the git identity set in the release workflow (`git config user.name "release-bot"`).

- [ ] **Step 3: Preview the changelog locally**

Run the JReleaser `changelog` goal to generate a preview without creating a release:

```bash
./mvnw -f roadrunner-app/pom.xml -B -DskipTests \
  org.jreleaser:jreleaser-maven-plugin:changelog
```

JReleaser writes the output to:
```
roadrunner-app/target/jreleaser/release/CHANGELOG.md
```

Open that file and verify:
- Commits are grouped by CC type (Features, Bug Fixes, etc.)
- No `Bump ...` Dependabot entries appear
- No `[release] prepare ...` entries appear
- Entries from real features/fixes are present (e.g. `basic JDBC protocol support`, `intial design of parameters support`)

If `CHANGELOG.md` is empty or has no sections, it means no commits in the current range match CC prefixes — that is expected on a branch with only `feat(site):` commits. Run against `master` to see the full history:

```bash
git checkout master
./mvnw -f roadrunner-app/pom.xml -B -DskipTests \
  org.jreleaser:jreleaser-maven-plugin:changelog
cat roadrunner-app/target/jreleaser/release/CHANGELOG.md
```

Expected: sections for Features and Bug Fixes with commit entries, no Dependabot noise.

- [ ] **Step 4: Commit**

```bash
git add roadrunner-app/jreleaser.yml
git commit -m "feat: configure JReleaser changelog with conventional-commits preset"
```
