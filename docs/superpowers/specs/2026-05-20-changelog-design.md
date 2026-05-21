# Changelog Design: JReleaser GitHub Release Notes

**Date:** 2026-05-20  
**Status:** Approved

## Goal

Generate structured, categorized changelogs in GitHub Release notes automatically on every release. No new tooling, no site page — one config change to the existing JReleaser setup.

## Approach

Add a `changelog` block to `release.github` in `roadrunner-app/jreleaser.yml`. JReleaser's built-in Conventional Commits preset categorizes commits and formats the GitHub Release body. Commits that don't match any CC prefix (Dependabot `Bump ...`, maven-release-plugin `[release] prepare ...`) are silently dropped via `hide.uncategorized: true`.

## Change

**File:** `roadrunner-app/jreleaser.yml`

Add under `release.github`:

```yaml
    changelog:
      formatted: ALWAYS
      preset: conventional-commits
      hide:
        uncategorized: true
        contributors:
          - dependabot[bot]
          - release-bot
```

## Behavior

- `preset: conventional-commits` — maps CC prefixes to categories:
  - `feat:` → Features
  - `fix:` → Bug Fixes
  - `perf:` → Performance
  - `refactor:` → Refactoring
  - `docs:` → Documentation
  - `build:`, `ci:`, `chore:` → Build
- `hide.uncategorized: true` — drops all commits without a matching CC prefix; catches Dependabot and `[release]` commits without explicit patterns
- `hide.contributors` — suppresses bot names from the Contributors footer

## Out of Scope

- `CHANGELOG.adoc` or `CHANGELOG.md` file in the repo
- Changelog page on the Antora site
- Enforcing Conventional Commits on all contributions (existing non-CC commits are simply filtered out)
