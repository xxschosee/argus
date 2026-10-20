package com.xxschosee.argus;

import com.xxschosee.argus.core.Cause;
import com.xxschosee.argus.core.IncidentLog;
import com.xxschosee.argus.core.MetricsSnapshot;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ArgusCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private static final List<String> SUBCOMMANDS = List.of("status", "history", "plugins", "scan", "reload");

    private final ArgusPlugin plugin;

    public ArgusCommand(ArgusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("argus.use")) {
            sender.sendMessage("Недостаточно прав.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Использование: /argus <status|history|scan|reload>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "history" -> history(sender);
            case "plugins" -> plugins(sender);
            case "scan" -> scan(sender);
            case "reload" -> reload(sender);
            default -> sender.sendMessage("Неизвестный подкоманд: " + args[0]);
        }
        return true;
    }

    private void status(CommandSender sender) {
        MetricsSnapshot snapshot = plugin.lastSnapshot();
        if (snapshot == null) {
            sender.sendMessage("Метрики ещё не собраны — подожди пару секунд.");
            return;
        }
        sender.sendMessage("§6§l— состояние сервера");
        sender.sendMessage("§7TPS: §f" + fmt(snapshot.tps1m())
                + " §7(5м " + fmt(snapshot.tps5m()) + ", 15м " + fmt(snapshot.tps15m()) + ")");
        sender.sendMessage("§7Время тика: §f" + fmt(snapshot.mspt()) + " мс");
        sender.sendMessage("§7Худший тик: §f" + fmt(snapshot.worstTickMs()) + " мс"
                + " §7(p99 " + fmt(snapshot.p99TickMs()) + " мс, средний "
                + fmt(plugin.tickTimer().averageMs()) + " мс, всплесков " + snapshot.ticksOverBudget() + ")");
        sender.sendMessage("§7Куча: §f" + (snapshot.heapUsedBytes() / 1024 / 1024) + " / "
                + (snapshot.heapMaxBytes() / 1024 / 1024) + " МБ"
                + " §7(" + Math.round(snapshot.heapRatio() * 100) + "%)");
        sender.sendMessage("§7Игроки: §f" + snapshot.playersOnline() + "/" + snapshot.playersMax());
        sender.sendMessage("§7Сущности: §f" + snapshot.entitiesLoaded()
                + " §7| чанки: §f" + snapshot.chunksLoaded());
        sender.sendMessage("§7Сборка мусора: §f" + Math.round(plugin.gcStats().overheadPercent()) + "%"
                + " §7| смена чанков: §f" + plugin.chunkChurn().perMinute() + " в мин");

        List<String> active = new ArrayList<>();
        for (Cause cause : Cause.values()) {
            if (plugin.engine().isFiring(cause)) {
                active.add(cause.key());
            }
        }
        sender.sendMessage(active.isEmpty()
                ? "§aАктивных проблем нет."
                : "§cАктивные проблемы: §f" + String.join(", ", active));
    }

    private void history(CommandSender sender) {
        List<IncidentLog.Entry> entries = plugin.incidentLog().recent(10);
        if (entries.isEmpty()) {
            sender.sendMessage("§7Инцидентов пока не было.");
            return;
        }
        sender.sendMessage("§6§l— последние инциденты");
        for (IncidentLog.Entry entry : entries) {
            String mark = entry.resolved() ? "§a✔" : "§c✖";
            sender.sendMessage(mark + " §7" + TIME.format(Instant.ofEpochMilli(entry.timestampMillis()))
                    + " §f" + entry.cause().key() + " §7— " + entry.summary());
        }
    }

    private void scan(CommandSender sender) {
        plugin.scanner().reset();
        sender.sendMessage("§7Полное сканирование виновника запущено, результат придёт в Telegram.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("argus.admin")) {
            sender.sendMessage("Недостаточно прав.");
            return;
        }
        try {
            plugin.reloadSettings();
            sender.sendMessage("§aКонфиг перечитан, пороги применены.");
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cКонфиг некорректен: " + e.getMessage());
        }
    }

    private void plugins(CommandSender sender) {
        Map<String, Double> distribution = plugin.profiler() == null
                ? Map.of() : plugin.profiler().distribution();
        if (distribution.isEmpty()) {
            sender.sendMessage("§7Пока нет образцов, подожди немного.");
            sender.sendMessage("§7Точный профиль плагинов считает spark: /timings paste");
            return;
        }
        sender.sendMessage("§6§l— кто занимает время (" + plugin.profiler().sampleCount() + " образцов)");
        int i = 0;
        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            if (i++ >= 10) {
                break;
            }
            String bar = "#".repeat(Math.min(20, (int) Math.round(entry.getValue() / 5.0)));
            sender.sendMessage("§f" + entry.getKey() + " §7" + Math.round(entry.getValue()) + "% "
                    + "§8" + bar);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
