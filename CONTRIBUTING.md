# Contributing to Argus

Thanks for helping. Argus is MIT licensed and everyone is welcome to pull requests.

## Before you start

- **Open an issue first** for anything larger than a bug fix. It saves both of us time
  if a design change is going to be needed.
- **Real lag cases are the most valuable contribution.** If Argus stayed silent while
  your server was dying, or named the wrong cause, that is a bug worth a report.

## Rules of the codebase

1. **The core stays Bukkit-free.** Nothing under `src/main/java/com/xxschosee/argus/core/`
   may import `org.bukkit.*`. The part that decides what to do should not be tangled
   with server calls.
2. **No runtime dependencies.** The JAR ships only our own code. Telegram is called
   through the JDK HTTP client.
3. **Destructive actions stay off by default.** If you add a new heal action, decide
   carefully whether it is reversible, and if it is not, keep it out of the default
   policy in `HealEngine.defaultPolicy()`.
4. **No telemetry, ever.** The only outbound call is the Telegram Bot API, and only
   when a token is configured.

## Style

- Java 21. Records for immutable data, `final` on fields.
- One public class per file, file name matches the class.
- Comments explain *why*, not *what*. The code says what.
- No `System.out` — use the plugin logger.
- Keep methods short. If a method needs a comment to explain what it does, split it.

## Commit messages

Conventional Commits:

```
feat: detect chunk flood as a separate cause
fix: don't fire an alarm on a single TPS spike
docs: explain how to find your Telegram chat id
```

## Testing

There is no automated test suite. Verify on a real server:

```bash
./gradlew build
bash scripts/smoke-test.sh
```

The smoke test boots Paper, applies aggressive thresholds, creates load and checks
that the plugin detects it, finds the guilty chunk, records the incident and reports
the recovery.

For a smaller change, a manual round is usually enough: drop the JAR into `plugins/`,
start the server, watch `/argus status` while a farm runs in the background.

## Verifying a change on a real server

1. `./gradlew build`
2. Drop the JAR into `plugins/` and start Paper 1.21.x.
3. Build a lag machine (TNT or a redstone clock) and watch `/argus status`.
4. Check the Telegram message and the log.

## Reporting bugs

Include: Minecraft and Paper version, server RAM, what you saw, what Argus reported
(`/argus history` output), and the relevant part of the server log.
