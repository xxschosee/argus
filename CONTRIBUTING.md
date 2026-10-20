# Contributing to Argus

Thanks for helping. Argus is MIT licensed and everyone is welcome to pull requests.

## Before you start

- **Open an issue first** for anything larger than a bug fix. It saves both of us time
  if a design change is going to be needed.
- **Real lag cases are the most valuable contribution.** If Argus stayed silent while
  your server was dying, or named the wrong cause, that is a bug worth a report.

## Rules of the codebase

1. **The core must stay Bukkit-free.** Nothing under `src/main/java/dev/argus/core/`
   may import `org.bukkit.*`. All decision logic has to be testable without a server.
2. **Tests come with the logic.** If you change something under `core/`, add or update
   the corresponding test in the same commit.
3. **No runtime dependencies.** The JAR ships only our own code. Telegram is called
   through the JDK HTTP client.
4. **Destructive actions stay off by default.** If you add a new heal action, decide
   carefully whether it is reversible, and if it is not, keep it out of the default
   policy in `HealEngine.defaultPolicy()`.
5. **No telemetry, ever.** The only outbound call is the Telegram Bot API, and only
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

```bash
./gradlew test
```

The `core` package should stay at 100% branch coverage. `platform` and `notify` are
verified manually against a real server — if you touch them, say so in the PR and
include what you tested.

## Verifying a change on a real server

1. `./gradlew build`
2. Drop the JAR into `plugins/` and start Paper 1.21.x.
3. Build a lag machine (TNT or a redstone clock) and watch `/argus status`.
4. Check the Telegram message and the log.

## Reporting bugs

Include: Minecraft and Paper version, server RAM, what you saw, what Argus reported
(`/argus history` output), and the relevant part of the server log.
