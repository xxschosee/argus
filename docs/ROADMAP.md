# Roadmap

Ordered by what unblocks the most people, not by how interesting it is to build.

## Done

- Metric collection: MSPT, TPS 1/5/15 min, heap, players, entities, chunks
- Five causes with evidence, edge-triggered, one alarm per incident
- Reversible auto-healing by default, destructive actions opt-in
- Budgeted search for the guilty chunk
- Telegram alerts, RU and EN
- `/argus status|history|scan|reload`
- 52 unit tests, a live-server smoke test, GitHub Actions build

## Next

**Pterodactyl integration.** A hung server cannot restart itself from the inside the
process. With a panel API key the plugin can ask the panel to restart it. The
`restart_server` action already exists in the vocabulary and is waiting for this.

**Telegram buttons.** Right now the plugin only sends text. Buttons need
`getUpdates`, which means the plugin owns the bot token and polls for callbacks.
Worth doing once the panel integration is in, because both need the same plumbing.

**Smart thresholds.** The defaults assume a 4–8 GB server. Deriving the thresholds
from the actual heap limit would make the plugin usable on a 2 GB VPS without
tuning.

**Folia.** `SchedulerFacade` is already shaped for it. What is missing is a Folia
implementation of the interface, a real Folia server to test against, and
`folia-supported: true` in `plugin.yml`.

**Web dashboard.** History of incidents over time, uptime, comparison between
servers. Only worth it once there are enough servers using the plugin to make the
data interesting.

## Not planned

- **Anti-cheat.** The audience this plugin came from treats cheats as a feature of
  anarchy servers, not a problem to solve.
- **Player data.** No inventory, no chat logs, no per-player statistics. The plugin
  aggregates the server's own vitals and nothing else.
- **A paid tier.** The plugin is free and stays free.
