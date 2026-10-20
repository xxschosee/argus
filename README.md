<div align="center">

# Argus

**Watchdog and auto-healer for Paper servers.**

*Finds the cause of lag, fixes it, and tells you about it in Telegram.*

[![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

</div>

> **Русский:** плагин для Paper-серверов, который находит причину лагов, сам её устраняет
> и пишет об этом в Telegram. Бесплатный, без телеметрии, без платных версий.
> Подробности ниже на английском.

---

## The problem

Your server starts lagging at 3 AM. You find out about it at 9 AM, from angry players.
Or you never find out, and the server quietly dies for weeks.

Profiling tools tell you *what* is slow. They don't fix it, and they don't wake you up.

## What Argus does

Argus samples the server every few seconds, and when something goes wrong it:

1. **Names the cause** — TPS collapse, tick time overrun, memory pressure, entity flood,
   chunk flood. With the numbers that prove it.
2. **Fixes it** — unloads the guilty chunk, trims the heap, disables autosave temporarily.
   Only reversible actions by default; destructive ones are opt-in.
3. **Tells you** — a Telegram message with the diagnosis and exactly what was done.

Then, separately, it finds *which chunk* is guilty by walking the loaded chunks in
small budgeted steps — so the scan never becomes the lag it was looking for.

## Install

1. Download the JAR from [Modrinth](https://modrinth.com/plugin/argus) or
   [releases](https://github.com/xxschosee/argus/releases).
2. Drop it into `plugins/`.
3. Start the server. It works out of the box — without Telegram it just logs.

### Telegram setup (optional)

1. Create a bot with [@BotFather](https://t.me/BotFather), copy the token.
2. Write to your bot, then open `https://api.telegram.org/bot<TOKEN>/getUpdates`
   and read your `chat.id` from the response.
3. Fill in `config.yml`:

```yaml
telegram:
  bot_token: "123456:ABC-DEF..."
  chat_id: 987654321
```

4. `/argus reload`

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/argus status` | `argus.use` | Current TPS, tick time, heap, players, entities, chunks, active problems |
| `/argus history` | `argus.use` | Last 10 incidents with timestamps |
| `/argus scan` | `argus.use` | Force a full scan for the guilty chunk right now |
| `/argus reload` | `argus.admin` | Re-read `config.yml` and apply new thresholds |

## Configuration

Everything lives in `config.yml`. The defaults are tuned for a 4–8 GB server.

```yaml
sample:
  interval_ticks: 100        # 100 ticks = 5 seconds between samples

thresholds:
  warn_mspt: 45.0            # tick time above this = lag
  critical_mspt: 70.0
  warn_tps: 17.0             # TPS below this = lag
  critical_tps: 12.0
  warn_heap_ratio: 0.80      # heap usage
  critical_heap_ratio: 0.92
  max_entities: 3000
  max_chunks: 4000
  samples_before_alarm: 3   # consecutive bad samples before alerting

heal_actions:
  tps_degraded: [notify_only, trim_memory]
  high_mspt: [notify_only, trim_memory]
  heap_pressure: [trim_memory, notify_only]
  entity_overload: [notify_only]
  chunk_overload: [notify_only]
```

### Available heal actions

| Action | Reversible | What it does |
|---|---|---|
| `notify_only` | — | Just tell the admin |
| `unload_chunk` | yes | Unload the guilty chunk (it reloads on demand) |
| `trim_memory` | yes | Force a garbage collection |
| `disable_autosave` | yes | Temporarily pause autosave, restore it after N ticks |
| `reduce_entity_limit` | — | *Not supported by the Paper API* |
| `remove_entities` | **no** | Delete entities in the chunk — opt-in only |
| `restart_server` | **no** | Restart via the Pterodactyl API — opt-in only |

Destructive actions are **not** in the default policy. Enable them deliberately.

## What Argus will not do

- It will not send data anywhere except the Telegram chat you configured.
- It will not touch player data, inventory, chat logs, or anything about your players.
- It will not advertise anything anywhere.
- It will not cost you money, ever. No paid tier, no upsells, no telemetry.

## Design notes

- The core (`com.xxschosee.argus.core`) has **zero** Bukkit imports, so the decision
  logic stays readable on its own and does not get tangled with server calls.
- The Telegram client uses the JDK's own HTTP client. **No runtime dependencies.**
- One alarm per incident, not one per sample. A single spike never triggers it.
- The chunk scan has a hard per-tick budget, so Argus cannot become the lag.

## Building from source

```bash
./gradlew build        # JAR lands in build/libs/
./scripts/validate-plugin-layout.sh   # descriptor and layout checks
```

Requires JDK 21. The Gradle wrapper is included.

Changes are checked on a real server: `scripts/smoke-test.sh` boots Paper, generates
load and walks through the whole chain — detection, culprit search, incident log,
recovery.

Further reading: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit
together and why, [docs/ROADMAP.md](docs/ROADMAP.md) for what is next.

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Especially useful: **real-world cases of lag that Argus failed to identify.**

## License

[MIT](LICENSE) — free for any use, including commercial.
