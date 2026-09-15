# Glossary Management & Lifecycle

How glossaries are stored, resolved per site, applied by each translation
provider, and (for DeepL) stored remotely — including whether two sites can
conflict.

> For the CSV file format and validation rules, see
> [`glossary-csv-contract.md`](./glossary-csv-contract.md).

---

## 1. Where glossaries live (per site)

Glossaries are plain CSV files stored **inside each Jahia site**, not as
individual JCR nodes per term.

- Location: `<site-path> + translation.glossary.relative.path`
- Default relative path: `/files/glossaries`
  (configurable via `translation.glossary.relative.path` in
  `org.jahia.community.translation.assisted.cfg`).
- Example: `/sites/acme/files/glossaries/marketing.csv`

Every `*.csv` file in that folder is a candidate. A site may contain several
glossary files; they are all loaded and **merged** (see below).

Because the folder is resolved from the **site** of the node being translated,
each site has its own, independent glossary folder.

### Global glossary (system site)

A **global glossary** shared by every site is read from the Jahia system site,
at the **same relative path**:

- Location: `/sites/systemsite` + `translation.glossary.relative.path`
  (e.g. `/sites/systemsite/files/glossaries/*.csv`).

The global glossary is loaded **first**, then the current node's resolved-site
glossaries are loaded on top. On a conflicting source term, the **site value
wins** (it is loaded last); source terms that exist only globally are inherited.
This lets you define organization-wide terms once and override them per site.

---

## 2. Lifecycle of a glossary

```
author CSV in site        resolve for a               apply during
/files/glossaries    ->   (source, target)     ->     translation
                          language pair
     (JCR)                (parse + merge + cache)      (DeepL or OpenAI)
```

### 2.1 Authoring
An editor uploads/edits one or more CSV files in the site's glossary folder.
No restart or redeploy is required.

### 2.2 Resolution (per site, per language pair)
When a translation or suggestion is requested for `(sourceLanguage,
targetLanguage)`, the module:

1. Lists every `*.csv` under the **global** (system site) folder, then every
   `*.csv` under the current **site** folder — global first, site second.
2. Parses and validates each file. Invalid files are **skipped** (logged);
   files with only some bad rows keep their valid rows (logged).
3. Builds a single `source term -> target term` map for the requested pair by
   **merging all files** in load order. On a duplicate source term, the
   last-loaded value wins — so a site term overrides a global term, and within
   a folder the last file in path order wins.
4. Caches the resolved map (the cache fingerprint covers both global and site
   files, so changing either rebuilds the map).

**Resolution cache key** (`CsvGlossaryService`):

```
workspace | sitePath | sourceLanguage | targetLanguage
```

This key includes the **site path**, so resolution is fully isolated per site.
The cache entry carries a *fingerprint* of the contributing files (path +
`jcr:lastModified`); when any glossary file changes, the fingerprint changes
and the map is rebuilt on the next request.

### 2.3 Application by provider
The resolved map is handed to whichever provider is active:

- **DeepL** — the map is turned into a DeepL glossary and attached to the
  translation request by `glossaryId` (see §3).
- **OpenAI** — there is **no remote glossary object**. Relevant terms are
  injected into the prompt, and any field whose entire value exactly matches a
  glossary source term is replaced deterministically. This is resolved per
  request from the per-site map, so OpenAI has **no cross-site storage** to
  conflict over.

The rest of this document focuses on DeepL, where a remote object is created.

---

## 3. How glossaries are stored in DeepL

For DeepL, the resolved map is materialised as a **multilingual glossary**
managed through the DeepL API (`DeepLGlossaryManager`).

### 3.1 Naming
The glossary name is scoped by **site and language pair**:

```
jahia-ai-assisted-translations-<SITE>-<SRC>-<DEST>
        (GLOSSARY_NAME_PREFIX)      e.g. jahia-ai-assisted-translations-acme-FR-EN
```

`<SITE>` is the site key of the node being translated (non-alphanumeric
characters are replaced with `-`; a missing site key falls back to `default`).
Including the site means two sites that share a language pair get **separate**
DeepL glossaries and no longer overwrite each other (see §4). The language codes
are the DeepL codes (regional variants such as `EN-US` are not valid for
glossaries, so the module falls back to the base language, e.g. `EN`).

### 3.2 In-memory cache and content hashing
`DeepLGlossaryManager` keeps an in-memory cache keyed by:

```
<SITE>|<SRC>|<DEST>          e.g. acme|FR|EN
```

Alongside the `glossaryId` it stores a **SHA-256 hash of the resolved terms**.
On each request:

- If the incoming terms hash **matches** the cached hash → reuse the existing
  `glossaryId` (no API call).
- If it **differs** → find the glossary by name and **replace its dictionary
  contents** with the new terms (or create it if missing), then update the
  cache.

### 3.3 Cleanup of stale glossaries
Old glossaries are garbage-collected to avoid unbounded growth:

- Candidates: glossaries whose name starts with the module prefix (or are
  currently cache-protected).
- A glossary is deleted once it is older than **30 days**
  (`GLOSSARY_MAX_AGE_MILLIS`), skipping any still referenced by the cache.
- Cleanup runs at most every **12 hours** (`GLOSSARY_CLEANUP_INTERVAL_MILLIS`),
  and once eagerly when the DeepL service activates.

---

## 4. Do two sites conflict? (the filename question)

**Short answer:** no. The *filename* is irrelevant to conflicts, and sites are
isolated at the DeepL layer too: glossaries are named and cached per **site**
and language pair (`…-<site>-<SRC>-<DEST>`), so two sites never share or
overwrite a glossary — whatever their filenames or language pairs.

### 4.1 Same filename, different sites — NO conflict at the file level
`marketing.csv` on `/sites/acme` and `marketing.csv` on `/sites/globex` are
two independent JCR nodes in two independent folders. They are located and
resolved separately, and the resolution cache key includes the site path. Same
name, zero interaction.

### 4.2 Same language pair, different sites — NO conflict (scoped per site)
The DeepL glossary is scoped by **site and language pair**, so two sites get
distinct glossary objects even when the filenames and the pair are the same:

| Site   | File name       | Pair  | DeepL glossary name                          |
| ------ | --------------- | ----- | -------------------------------------------- |
| acme   | `marketing.csv` | fr→en | `jahia-ai-assisted-translations-acme-FR-EN`  |
| globex | `marketing.csv` | fr→en | `jahia-ai-assisted-translations-globex-FR-EN`|

Each site creates/updates only its own glossary, and the in-memory cache key
(`<SITE>|<SRC>|<DEST>`) is likewise per site, so there is no cross-site
overwrite and no content thrash — including in a Jahia cluster, where every node
targets the same DeepL account but the same per-site name.

> Historical note: before this scoping, the glossary name was
> `jahia-ai-assisted-translations-<SRC>-<DEST>` (no site), so any two sites
> sharing a language pair with different terms repeatedly overwrote one shared
> glossary. Filename never played a role either way.

### 4.3 Summary

| Scenario                                             | Conflict? |
| ---------------------------------------------------- | --------- |
| Same filename, different sites, different pairs      | No        |
| Different filenames, same site (multiple files)      | No — merged |
| Same **language pair** across sites, different terms | No — separate per-site glossaries |
| Same language pair across sites, identical terms     | No — separate per-site glossaries |

---

## 5. Practical guidance

- **Filename does not matter for isolation between sites** — and it no longer
  needs to. Sites are isolated by site key at the DeepL layer regardless of the
  glossary filename.
- Multiple sites may translate the **same language pair** with **different**
  term sets safely; each gets its own DeepL glossary
  (`…-<site>-<SRC>-<DEST>`).
- Within a single site, split large glossaries across several CSV files freely
  — they are merged; just avoid duplicate source terms across files (last one
  loaded wins).
- Changing a CSV takes effect on the next translation (fingerprint-based cache
  invalidation); no restart needed.

---

## 6. Key references in code

| Concern                        | Class / detail                                             |
| ------------------------------ | ---------------------------------------------------------- |
| Per-site file location         | `JcrGlossaryFileLocator.listGlossaryFiles` (`<site>` + relative path) |
| Global file location           | `JcrGlossaryFileLocator.listGlobalGlossaryFiles` (`/sites/systemsite` + relative path) |
| Parse & validate               | `GlossaryCsvParser`                                         |
| Merge + per-site resolution cache | `CsvGlossaryService` (key `workspace\|sitePath\|src\|dest`) |
| Site key carried to providers  | `ResolvedGlossary.getSiteKey()`                            |
| DeepL storage / naming / hash / cleanup | `DeepLGlossaryManager` (name `prefix-<site>-SRC-DEST`) |
| OpenAI in-prompt application   | `OpenAITranslatorService`                                   |
