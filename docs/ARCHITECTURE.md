# Architecture

Three layers, one direction of dependencies. The point of the split is that the part
which decides *what* to do never has to deal with the server.

```
core/          metrics -> diagnosis -> healer
               (no Bukkit imports at all)

platform/      adapters to the live server
               (metrics collection, chunk scanning, action execution, scheduling)

notify/        delivery to the admin
               (Telegram Bot API over the JDK HTTP client)
```

`core` never imports `org.bukkit`. `platform` never imports `notify`. `notify` never
imports `platform`. The validator script fails the build if that stops being true.

## core

| Class | Role |
|---|---|
| `MetricsSnapshot` | Immutable snapshot of the server's vitals at one instant |
| `Thresholds` | The numbers that decide "this is broken", loaded from `config.yml` |
| `Cause` | The five things that can go wrong |
| `Severity` | INFO / WARNING / CRITICAL |
| `Diagnosis` | Cause + severity + the numbers that prove it |
| `DiagnosisEngine` | Turns snapshots into diagnoses, edge-triggered |
| `HealKind` / `HealAction` | The vocabulary of fixes |
| `HealEngine` | Turns diagnoses into a plan of actions |
| `HotSpotScanner` | Budgeted search for the guilty chunk |
| `IncidentLog` | Ring buffer of the last N incidents |

### Edge-triggered alarms

The obvious implementation — "if TPS is low, send a message" — produces dozens of
messages a minute and gets muted within a day. `DiagnosisEngine` instead keeps a
counter of consecutive bad samples and a flag for "incident already open". One alarm
per incident, then a separate message when it clears. A single spike never triggers
anything, because `samples_before_alarm` requires the condition to hold.

### Planning separated from execution

`HealEngine.plan()` returns a list of `HealAction` and touches nothing. `HealExecutor`
does the actual work on the server. That split is what makes the whole healing
decision tree testable without a Minecraft server, and it keeps every place that
touches the server down to a single class per operation.

### The guilty chunk

Counting entities in a chunk is expensive. Scanning every loaded chunk in one tick
would cause the lag you are trying to find. So the scan is incremental: each tick
processes at most `hotspot.chunks_per_tick` chunks (40 by default) and the result
arrives a few seconds after the diagnosis. The scan only runs when something is
actually wrong.

## platform

`SchedulerFacade` wraps the schedulers behind an interface shaped for Folia — global
work, async work, and region-bound work are separate methods. Today the only
implementation is the classic Bukkit scheduler; adding Folia means adding one class
and changing one line in `ArgusPlugin`. `folia-supported` is deliberately **not** set
in `plugin.yml` until that has been verified against a real Folia server.

`BukkitMetricsCollector` reads counters, no iteration over the world list beyond
entity and chunk counts.

`BukkitHealExecutor` is the only class that mutates server state. Chunk unloading goes
through `World#unloadChunkRequest`, which respects players standing in the chunk,
instead of `Chunk#unload`, which does not.

## notify

`MessageRenderer` builds the text; `TelegramClient` sends it. Rendering is separated
from sending so the message format can be unit-tested without a network.

`TelegramClient` checks for `"ok":true` in the response body instead of parsing JSON —
for a single endpoint that is enough, and it keeps the JAR dependency-free.

Two languages are supported (`notify.language: ru | en`). The Russian one is the
default because that is where the plugin comes from; English is there because
Modrinth is international.

## Checking a change

There is no test suite. Everything is verified on a real Paper server, because that is
the only place where the interesting behaviour lives — TPS, tick time and entity counts
are properties of a running server, not of a function call.

`scripts/smoke-test.sh` does this in one command: it boots Paper 1.21.11, applies
aggressive thresholds, creates load through `/forceload` and a pile of entities, and
walks the whole chain — detection, culprit search, incident log, recovery.

```bash
bash scripts/smoke-test.sh
```

For anything smaller, a manual round is usually enough: drop the JAR in `plugins/`,
start the server, watch `/argus status` while a farm runs in the background.

## Things that turned out differently than planned

- `Bukkit.setAutoSave(boolean)` does not exist in Paper API 1.21.11. Autosave is
  toggled per world with `World#setAutoSave(boolean)` instead.
- There is no way to change the per-chunk entity limit through the public API, so
  `reduce_entity_limit` is listed but reports itself as unsupported.
- Paper API 1.21.11 does expose the Folia schedulers
  (`getRegionScheduler()`, `getGlobalRegionScheduler()`, `getAsyncScheduler()`), so
  Folia support needs no reflection.
