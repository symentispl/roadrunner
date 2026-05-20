# Site Design: Antora + GitHub Pages

**Date:** 2026-05-20  
**Status:** Approved

## Goal

Add a project documentation site for Roadrunner, published automatically during release builds. The site uses AsciiDoc as its authoring format and requires no infrastructure beyond GitHub Pages.

## Approach

**Antora** (AsciiDoc-native static site generator) builds the site from source under `site/`. GitHub Pages hosts it at `https://symentispl.github.io/roadrunner/`. The existing GitHub Actions release workflow publishes the site after each release.

Versioned docs are out of scope for now; the site always reflects the latest release.

## Directory Layout

```
site/
├── antora.yml                        # component descriptor
└── modules/
    └── ROOT/
        ├── nav.adoc                  # sidebar navigation
        └── pages/
            ├── index.adoc            # landing page
            ├── getting-started.adoc  # build, install, first run
            ├── configuration.adoc    # CLI flags and options
            └── protocols/
                ├── index.adoc        # protocols overview
                ├── vm.adoc
                ├── ab.adoc
                ├── zero.adoc
                └── jdbc.adoc
```

`target/site` is the Antora output directory, consistent with Maven conventions. It must be added to `.gitignore`.

## Antora Playbook

`antora-playbook.yml` at the repo root:

```yaml
site:
  title: Roadrunner
  url: https://symentispl.github.io/roadrunner
  start_page: roadrunner::index.adoc

content:
  sources:
    - url: .
      start_path: site

ui:
  bundle:
    url: https://gitlab.com/antora/antora-default-ui/-/jobs/artifacts/HEAD/raw/build/ui-bundle.zip?job=bundle-stable
    snapshot: true

output:
  dir: target/site
```

`antora.yml` (component descriptor inside `site/`):

```yaml
name: roadrunner
title: Roadrunner
version: ~
start_page: ROOT:index.adoc
nav:
  - modules/ROOT/nav.adoc
```

`version: ~` activates Antora's unversioned mode: no version prefix in URLs, no version switcher in the UI.

## GitHub Actions Integration

Two steps are appended to the existing `release.yml` workflow, after the JReleaser step. They run from `target/checkout` (where maven-release-plugin leaves the tagged source):

```yaml
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'

      - name: Build site with Antora
        working-directory: target/checkout
        run: npx antora antora-playbook.yml

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: target/checkout/target/site
```

The `gh-pages` branch is created automatically on first deploy. After that, GitHub Pages must be enabled in the repository settings pointing at the `gh-pages` branch root. The existing `contents: write` permission on the workflow covers this.

## Out of Scope

- Versioned docs (can be added later by setting `version` in `antora.yml` and adding multiple content sources to the playbook)
- Custom UI theme
- Search beyond Antora's built-in page search
- Publishing on non-release builds (e.g. on push to master)
