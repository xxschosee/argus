# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-10-20

First working version. A thin vertical slice: the server degrades, Argus notices,
fixes what it can, and tells the admin in Telegram.

### Added

- **Metrics** — MSPT, TPS (1/5/15 min), heap usage, player count, loaded entities
  and chunks, sampled every 5 seconds by default.
- **Diagnosis** — five causes recognised: TPS collapse, tick time overrun, memory
  pressure, entity flood, chunk flood. Each with the numbers that prove it.
  One alarm per incident rather than one per sample, with a separate message
  when the problem clears.
- **Auto-healing** — reversible actions by default: chunk unloading, heap trimming,
  temporary autosave pause. Destructive actions exist but are opt-in.
- **Culprit finder** — an incremental, per-tick-budgeted walk over loaded chunks to
  find which one is guilty. The scan cannot become the lag it is looking for.
- **Telegram notifications** — diagnosis, evidence and applied actions, in Russian
  or English. Plain Bot API over the JDK HTTP client, no runtime dependencies.
- **`/argus`** — `status`, `history`, `scan`, `reload`.
- **Configurable thresholds** — all in `config.yml`, reloaded without a restart.
- **In-memory incident log** — the last 50 events, visible via `/argus history`.

### Known limitations

- `reduce_entity_limit` is not supported by the Paper API and logs as unsupported.
- `restart_server` requires the Pterodactyl integration, which is not in this release.
- Folia is not claimed as supported. The scheduler abstraction is built for it, but
  the flag is not set until it is verified on a real Folia server.
- Telegram buttons and the web dashboard are not in this release.
