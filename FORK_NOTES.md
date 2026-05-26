# DoplarrChaptarr — Fork Notes

This fork adds [Chaptarr](https://github.com/robertlordhood/chaptarr)
support (ebooks and audiobooks) to upstream
[Doplarr](https://github.com/kiranshila/Doplarr). It registers two
new Discord slash subcommands: `/request book` and `/request
audiobook`. Every upstream backend (Sonarr, Radarr, Overseerr) still
works unchanged.

This document is for whoever is maintaining the fork — usually
either merging a new upstream release or touching Chaptarr code.
For operator-facing integration notes, see
[`docs/CHAPTARR_INTEGRATION.md`](docs/CHAPTARR_INTEGRATION.md).

## Design stance

Every modification to an upstream file is a pure insertion. No
upstream lines are rewritten or reordered. The Chaptarr-specific
logic lives in two new files (`backends/chaptarr.clj` and
`backends/chaptarr/impl.clj`) plus additive entries in the shared
config/spec/discord/state-machine files. That keeps `git merge
upstream/main` mechanical: conflicts land on config-key lists and
resolve by keeping both blocks.

## File inventory

**Additive (new files, zero merge risk):**

| Path | Purpose |
|---|---|
| `src/doplarr/backends/chaptarr.clj` | Backend API: `search`, `additional-options`, `request-embed`, `request` |
| `src/doplarr/backends/chaptarr/impl.clj` | HTTP client, payload construction, row selection, lookup translation |
| `docs/CHAPTARR_INTEGRATION.md` | Operator-facing Chaptarr integration notes |
| `FORK_NOTES.md` | This file |

**Modified (insertion blocks only):**

- `src/doplarr/config.clj` — four insertions: Chaptarr entries in
  `valid-keys`, `redact-secrets`, `backend-media`, and
  `available-backends`.
- `src/doplarr/config/specs.clj` — four insertions: two required
  spec defs (`:chaptarr/url`, `:chaptarr/api`), one optional block,
  a `::has-backend` clause, and `::config` `:opt` additions plus a
  `matched-keys` line.
- `src/doplarr/discord.clj` — three insertions: `:book`/`:audiobook`
  entries in `request-thumbnail`; a `:metadata-profile` binding and
  field in `request-embed`; and a `cond->` wrap so `:image` is only
  assoc'd when `poster` is non-nil (Discord rejects null image
  URLs).
- `src/doplarr/interaction_state_machine.clj` — two insertions:
  `(assoc payload :sm-uuid uuid)` in `query-for-option-or-request`,
  which lets backends stash state back into the cache (Chaptarr
  uses this to cache the resolved book id after its pre-request
  POST); and a progress-message edit in `process-event! "request"`
  before the blocking take, which makes long Chaptarr requests feel
  responsive and removes the duplicate-click surface.
- `README.md` — Chaptarr-specific quickstart and the
  Chaptarr config-keys table. Discord / Sonarr / Radarr / Overseerr
  configuration links out to upstream's docs rather than duplicating
  them.

**Intentionally not modified:**

- `src/doplarr/core.clj` — Discord slash-command registration reads
  from `config/available-media`, which picks up `:book`/`:audiobook`
  automatically once `backend-media` has a `:chaptarr` entry.
- `src/doplarr/utils.clj` — all helpers (`from-camel`, `to-camel`,
  `process-rootfolders`, `request-and-process-body`, etc.) are
  reused by the Chaptarr backend without modification.
- `deps.edn`, `config.edn`, `build/build.clj`,
  `docker/Dockerfile` — unchanged from upstream.

## Merging from upstream

```bash
git fetch upstream
git merge upstream/main
```

Conflicts are almost always in `config.clj` or `specs.clj` when
upstream adds a new backend or config key near our insertion
points. Resolve by keeping both blocks. Upstream's new entries
and our Chaptarr entries sit alongside each other.

Higher-risk scenarios to watch for (none seen so far):

- **Upstream ships its own Readarr/audiobook backend.**
  `backend-media` would have a real competing claim on `:book`.
  Decide whether to drop the `:chaptarr` entry in favour of
  upstream, or keep both and have
  `available-backend-for-media` prefer Chaptarr when both URLs are
  set.
- **Upstream reshapes `config.clj` or `specs.clj`** (e.g. replaces
  the hand-maintained `valid-keys` set with a registry). Re-derive
  the insertion points in the new structure. The list of Chaptarr
  keys stays the same.

## Invariants a future maintainer should preserve

These took the longest to land. If any of them look like a
"cleanup opportunity" in a future refactor, read the integration
doc first.

- **Monitor flips go through `PUT /book/monitor`, not `PUT
  /book/{id}`.** The per-book PUT returns 2xx but doesn't actually
  persist monitor-flag changes. The bulk endpoint is the only one
  that does. Folding monitor toggles back into the per-book PUT
  will quietly break requests.
- **All four per-format profile ids on the author POST body.**
  Chaptarr ignores the singular `qualityProfileId` /
  `metadataProfileId` fields that other *arrs accept, with no
  error. Sending only three of the four leaves the author in an
  unusable state.
- **Author's `*MonitorFuture` flag must be true for the format
  before book-level monitor flips.** `ensure-author-enabled-for-format`
  handles this. Without the gate, the per-book monitor PUT is
  dropped server-side.
- **POST runs at `request-embed` render, not at Request-click.**
  Lookup results don't carry the edition-level identifiers
  (`isbn13`, `asin`) the public-CDN cover fallback needs.
  Post-POST resolved rows do. A refactor back to
  confirm-before-commit loses both the primary absolute CDN URL
  and the fallback identifiers, and the embed renders coverless.
- **Selection tiers: exact-title beats substring beats
  no-match.** Inside a tier, resolved rows beat placeholders and
  cleaner titles beat marketing-heavy ones (via
  `title-length-affinity`). Flattening to a single ranker with
  popularity as a major signal breaks requests against
  anthology-heavy catalogues.
- **`RefreshAuthor` only fires when the target is a placeholder.**
  It re-applies metadata profile language filters and can cull
  editions, so firing it preemptively on resolved rows is
  destructive. The `(not book-row-complete?)` gate in
  `request` is doing real work; don't remove it.
- **`request`'s body is wrapped in `try` + `(catch Throwable e ...
  e)`.** core.async's ioc-macro exception routing leaks when a
  continuation runs on a hato HTTP worker thread; returning the
  Throwable as a channel value is the only way the state
  machine's `else` branch can render the 403 body to Discord.
- **Author-name fallback in `find-existing-author` only fires on
  a single-match.** Multi-match names (two indexed "John
  Smith"s) return nil rather than risk resolving to the wrong
  author. The caller POSTs in that case, which is safe but slow.
- **Cover images do not go through Chaptarr.** The fork prefers
  the resolved row's absolute CDN URL and falls back to OpenLibrary
  (by ISBN) or Amazon (by ASIN). The fork deliberately avoids any
  design that would require exposing Chaptarr publicly. A previous
  iteration required a `CHAPTARR__PUBLIC_URL`, and operators
  whose Chaptarr lives on an internal network shouldn't have to
  expose it just for cover art. If a refactor reintroduces a
  Chaptarr-hosted cover path, the public-URL requirement comes
  back with it.
