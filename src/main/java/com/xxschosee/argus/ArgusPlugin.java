package com.xxschosee.argus;

import com.xxschosee.argus.core.ChunkChurn;
import com.xxschosee.argus.core.PluginProfiler;
import com.xxschosee.argus.core.Cause;
import com.xxschosee.argus.core.Diagnosis;
import com.xxschosee.argus.core.DiagnosisEngine;
import com.xxschosee.argus.core.GcStats;
import com.xxschosee.argus.core.HealAction;
import com.xxschosee.argus.core.HealEngine;
import com.xxschosee.argus.core.HotChunk;
import com.xxschosee.argus.core.HotSpotScanner;
import com.xxschosee.argus.core.IncidentLog;
import com.xxschosee.argus.core.MetricsSnapshot;
import com.xxschosee.argus.core.Thresholds;
import com.xxschosee.argus.core.TickTimer;
import com.xxschosee.argus.notify.Notifier;
import com.xxschosee.argus.notify.MessageRenderer;
import com.xxschosee.argus.notify.TelegramClient;
import com.xxschosee.argus.notify.TelegramNotifier;
import com.xxschosee.argus.platform.BukkitChunkSource;
import com.xxschosee.argus.platform.BukkitHealExecutor;
import com.xxschosee.argus.platform.BukkitMetricsCollector;
import com.xxschosee.argus.platform.ChunkDetails;
import com.xxschosee.argus.platform.ChunkListener;
import com.xxschosee.argus.platform.HealExecutor;
import com.xxschosee.argus.platform.JfrProfiler;
import com.xxschosee.argus.platform.MetricsCollector;
import com.xxschosee.argus.platform.PluginOwnership;
import com.xxschosee.argus.platform.SchedulerFacade;
import com.xxschosee.argus.platform.TickListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArgusPlugin extends JavaPlugin {

    private SchedulerFacade scheduler;
    private MetricsCollector collector;
    private DiagnosisEngine engine;
    private HealEngine healer;
    private IncidentLog incidentLog;
    private HotSpotScanner scanner;
    private HealExecutor executor;
    private Notifier notifier;
    private Thresholds thresholds;
    private TickTimer tickTimer;
    private GcStats gcStats;
    private ChunkChurn chunkChurn;
    private PluginProfiler profiler;
    private JfrProfiler jfrProfiler;

    private volatile MetricsSnapshot lastSnapshot;

    private volatile Diagnosis pendingDiagnosis;

    private long startedAtMillis;
    private long startupGraceMillis;
    private boolean graceLogged;
    private long lastAlertAt;
    private long lastProfileReportAt;
    private long lastCulpritAt;
    private long notifyCooldownMillis;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("Плагин выключен в конфиге — ничего не делаем.");
            return;
        }

        try {
            thresholds = readThresholds();
        } catch (IllegalArgumentException e) {
            getLogger().severe("Некорректные пороги в конфиге: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        scheduler = new SchedulerFacade.Bukkit(this);
        tickTimer = new TickTimer(getConfig().getInt("sample.tick_window", 240));
        TickListener tickListener = new TickListener(tickTimer);
        getServer().getPluginManager().registerEvents(tickListener, this);
        collector = new BukkitMetricsCollector(getServer(), tickTimer, thresholds.tickBudgetMs());
        engine = new DiagnosisEngine(thresholds);
        healer = readHealPolicy();
        incidentLog = new IncidentLog(getConfig().getInt("incident_log_size", 50));
        scanner = new HotSpotScanner(
                new BukkitChunkSource(getServer()),
                getConfig().getInt("hotspot.top_chunks", 5));
        executor = new BukkitHealExecutor(
                this,
                getLogger(),
                getConfig().getLong("healer.autosave_restore_ticks", 1200L));
        notifier = buildNotifier();

        gcStats = new GcStats(getConfig().getLong("sample.interval_ticks", 100L) / 20.0);
        chunkChurn = new ChunkChurn();
        getServer().getPluginManager().registerEvents(new ChunkListener(chunkChurn), this);

        if (getConfig().getBoolean("jfr.enabled", true)) {
            profiler = new PluginProfiler(getConfig().getLong("jfr.window_seconds", 120L));
            PluginOwnership ownership = new PluginOwnership(
                    Arrays.asList(getServer().getPluginManager().getPlugins()));
            jfrProfiler = new JfrProfiler(ownership, profiler,
                    getConfig().getLong("jfr.sample_period_ms", 50L),
                    tickListener::mainThread,
                    getConfig().getBoolean("jfr.native_samples", false));
            try {
                if (getConfig().getBoolean("jfr.always_on", false)) {
                    jfrProfiler.start();
                    long window = Math.max(5L, getConfig().getLong("jfr.window_seconds", 120L));
                    scheduler.runAsyncTimer(jfrProfiler::rotate, window * 20L, window * 20L);
                    getLogger().info("Профиль по плагинам включён постоянно, окно " + window + " с.");
                } else {
                    getLogger().info("Профиль по плагинам снимается только во время инцидента.");
                }
            } catch (RuntimeException e) {
                getLogger().warning("JFR не запустился: " + e.getMessage());
                jfrProfiler = null;
            }
        }

        startedAtMillis = System.currentTimeMillis();
        startupGraceMillis = getConfig().getLong("startup_grace_seconds", 60L) * 1000L;
        notifyCooldownMillis = getConfig().getLong("notify.cooldown_seconds", 120L) * 1000L;

        registerCommand();

        long period = Math.max(20L, getConfig().getLong("sample.interval_ticks", 100L));
        scheduler.runGlobalTimer(this::tick, period, period);

        // Первый замер сразу, чтобы /argus status отвечал уже через секунду после старта.
        try {
            lastSnapshot = collector.capture();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось снять первые метрики", e);
        }

        getLogger().info("Запущен. Пороги: " + describeThresholds()
                + " | уведомления: " + notifier.name()
                + " | планировщик: " + scheduler.name());
    }

    @Override
    public void onDisable() {
        if (jfrProfiler != null) {
            jfrProfiler.stop();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getLogger().info("Остановлен.");
    }

    private void tick() {
        MetricsSnapshot snapshot;
        try {
            snapshot = collector.capture();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось снять метрики", e);
            return;
        }
        lastSnapshot = snapshot;
        sampleGc();

        // При старте идёт генерация мира, и метрики всегда плохие. Тревоги в этот
        // момент только сбивают с толку, поэтому первые минуты просто молчим.
        if (System.currentTimeMillis() - startedAtMillis < startupGraceMillis) {
            return;
        }
        if (!graceLogged) {
            graceLogged = true;
            getLogger().info("Разогрев закончен, слежу за сервером.");
        }

        for (Diagnosis diagnosis : engine.analyze(snapshot)) {
            handleDiagnosis(diagnosis);
        }

        advanceHotspotSearch();
    }

    private void handleDiagnosis(Diagnosis diagnosis) {
        incidentLog.record(diagnosis, System.currentTimeMillis());

        if (diagnosis.resolved()) {
            getLogger().info("Проблема ушла: " + diagnosis.cause().key());
            return;
        }

        getLogger().warning("" + diagnosis.severity() + " " + diagnosis.cause().key()
                + " — " + diagnosis.summary());

        List<HealAction> performed = applyPlan(diagnosis, null);

        // Один инцидент поднимает сразу несколько причин: просадка TPS, время тика,
        // перегрузка сущностями. Если слать по сообщению на каждую, админ получит
        // десяток уведомлений подряд. Поэтому между сообщениями есть пауза.
        long now = System.currentTimeMillis();
        if (now - lastAlertAt < notifyCooldownMillis) {
            getLogger().info("Уведомление о " + diagnosis.cause().key() + " пропущено: недавно было сообщение.");
        } else {
            lastAlertAt = now;
            notifyAsync(diagnosis, performed);
        }

        // Виновника ищем при любой проблеме: без поиска половина тревог
        // приходит с ответом "где смотреть - непонятно".
        if (pendingDiagnosis == null) {
            pendingDiagnosis = diagnosis;
            scanner.reset();
        }
        startProfileCollection(diagnosis);
    }

    /**
     * Начать снимать профиль по плагинам.
     * На здоровом сервере профилирование не идёт вообще, накладных ноль.
     * Как только появилась проблема - включаем JFR на окно и потом отправляем отчёт.
     */
    private void startProfileCollection(Diagnosis diagnosis) {
        if (jfrProfiler == null || jfrProfiler.isRunning()) {
            return;
        }
        if (getConfig().getBoolean("jfr.always_on", false)) {
            return;
        }
        try {
            jfrProfiler.start();
        } catch (RuntimeException e) {
            getLogger().warning("Не удалось включить профиль: " + e.getMessage());
            return;
        }
        long window = Math.max(5L, getConfig().getLong("jfr.incident_window_seconds", 30L));
        scheduler.runAsyncTimer(() -> {
            jfrProfiler.collectAndStop();
            reportProfile();
        }, window * 20L, 1L);
    }

    /**
     * Отчёт по профилю.
     *
     * Если время тратит сам сервер, это не виноватый плагин, а работа Paper над
     * сущностями и чанками. Такое сообщение админу ничего не даёт, поэтому в
     * Telegram оно не идёт, а остаётся в логе.
     */
    private void reportProfile() {
        Map<String, Double> distribution = profiler == null
                ? Map.of() : profiler.distribution();
        if (distribution.isEmpty()) {
            return;
        }
        String worst = distribution.entrySet().iterator().next().getKey();
        boolean onlyServer = distribution.entrySet().stream()
                .allMatch(entry -> PluginProfiler.SERVER.equals(entry.getKey()));

        if (onlyServer) {
            getLogger().info("Профиль: время тратит сам сервер, плагины ни при чём ("
                    + topOwner() + "). Смотри состав чанка выше или spark.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastProfileReportAt < notifyCooldownMillis) {
            getLogger().info("Отчёт по профилю пропущен: недавно уже отправляли.");
            return;
        }
        lastProfileReportAt = now;
        getLogger().info("Отчёт по профилю отправлен: " + topOwner());
        notifier.info("Кто занимал время во время инцидента: " + topOwner());
    }

    private List<HealAction> applyPlan(Diagnosis diagnosis, String target) {
        List<HealAction> performed = new ArrayList<>();
        if (diagnosis.resolved()) {
            return performed;
        }
        for (HealAction action : healer.planWithTarget(diagnosis, target)) {
            try {
                if (executor.apply(action)) {
                    performed.add(action);
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Действие " + action.kind().key() + " не удалось", e);
            }
        }
        return performed;
    }

    private void sampleGc() {
        long count = 0L;
        long millis = 0L;
        for (java.lang.management.GarbageCollectorMXBean bean
                : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0) {
                count += c;
            }
            if (t > 0) {
                millis += t;
            }
        }
        gcStats.sample(count, millis, System.currentTimeMillis());
    }

    private void advanceHotspotSearch() {
        Diagnosis diagnosis = pendingDiagnosis;
        if (diagnosis == null) {
            return;
        }
        int budget = Math.max(1, getConfig().getInt("hotspot.chunks_per_tick", 40));
        HotSpotScanner.ScanResult result = scanner.scan(budget);
        if (!result.complete()) {
            return;
        }

        pendingDiagnosis = null;
        HotChunk worst = result.worst();
        if (worst == null) {
            getLogger().info("Явного виновника не нашлось (просканировано чанков: " + result.scanned() + ")");
            notifier.info(diagnosisText(diagnosis) + " " + hints());
            return;
        }

        String details = ChunkDetails.describe(worst.world(), worst.chunkX(), worst.chunkZ());
        getLogger().info("Виновник: " + worst.shortName() + " (" + worst.describe() + ") | " + details);

        List<HealAction> performed = applyPlan(diagnosis, worst.target());

        if (System.currentTimeMillis() - lastCulpritAt < notifyCooldownMillis) {
            getLogger().info("Сообщение о виновнике пропущено: недавно было.");
            return;
        }
        lastCulpritAt = System.currentTimeMillis();
        notifier.info(diagnosisText(diagnosis)
                + " Виновник: " + worst.shortName() + " (" + worst.describe() + ")"
                + " | " + details
                + hints());
    }

    /**
     * Что ещё может объяснять лаг, если виновник не найден по чанкам.
     * Здесь только то, что можно измерить точно.
     */
    public String hints() {
        List<String> parts = new ArrayList<>();
        if (gcStats.overheadPercent() >= getConfig().getDouble("gc_warn_percent", 8.0)) {
            parts.add("сборка мусора забирает " + Math.round(gcStats.overheadPercent()) + "% времени");
        }
        if (chunkChurn.perMinute() >= getConfig().getInt("chunk_churn_warn", 120)) {
            parts.add("чанки едут: " + chunkChurn.perMinute() + " в минуту, мир не успевает генерироваться");
        }
        String owner = topOwner();
        if (owner != null) {
            parts.add("больше всего времени занимает " + owner);
        }
        if (parts.isEmpty()) {
            parts.add("если лаг остался - смотри spark: /timings paste");
        }
        return "| " + String.join("; ", parts);
    }

    /** Кто занимает время сервера, по данным JFR. */
    public String topOwner() {
        if (profiler == null) {
            return null;
        }
        Map<String, Double> distribution = profiler.distribution();
        if (distribution.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            if (i++ >= 3) {
                break;
            }
            parts.add(entry.getKey() + " " + Math.round(entry.getValue()) + "%");
        }
        return String.join(", ", parts);
    }

    private void notifyAsync(Diagnosis diagnosis, List<HealAction> performed) {
        getLogger().info("Уведомление отправлено: " + diagnosis.cause().key());
        scheduler.runAsync(() -> notifier.notify(diagnosis, performed));
    }

    private String diagnosisText(Diagnosis diagnosis) {
        return diagnosis.cause().key() + ": " + diagnosis.summary();
    }

    private Thresholds readThresholds() {
        var cfg = getConfig();
        return new Thresholds(
                cfg.getDouble("thresholds.warn_mspt", 45.0),
                cfg.getDouble("thresholds.critical_mspt", 70.0),
                cfg.getDouble("thresholds.warn_tps", 17.0),
                cfg.getDouble("thresholds.critical_tps", 12.0),
                cfg.getDouble("thresholds.warn_heap_ratio", 0.80),
                cfg.getDouble("thresholds.critical_heap_ratio", 0.92),
                cfg.getInt("thresholds.max_entities", 3000),
                cfg.getInt("thresholds.max_chunks", 4000),
                Math.max(1, cfg.getInt("thresholds.samples_before_alarm", 3)),
                cfg.getDouble("thresholds.tick_budget_ms", 100.0),
                Math.max(1, cfg.getInt("thresholds.spikes_before_alarm", 3)),
                cfg.getBoolean("thresholds.heap_needs_lag", true));
    }

    private HealEngine readHealPolicy() {
        Map<Cause, List<String>> policy = new EnumMap<>(Cause.class);
        for (Cause cause : Cause.values()) {
            List<String> actions = getConfig().getStringList("heal_actions." + cause.key());
            policy.put(cause, actions);
        }
        if (policy.values().stream().allMatch(List::isEmpty)) {
            getLogger().info("heal_actions в конфиге пустые — используется безопасный набор по умолчанию.");
            return HealEngine.defaultPolicy();
        }
        return HealEngine.fromConfig(policy);
    }

    private Notifier buildNotifier() {
        String token = getConfig().getString("telegram.bot_token", "").trim();
        long chatId = getConfig().getLong("telegram.chat_id", 0L);
        if (token.isEmpty() || chatId == 0L) {
            getLogger().info("Telegram не настроен (bot_token/chat_id) — уведомления идут только в лог.");
            return Notifier.logging(getLogger());
        }
        Duration timeout = Duration.ofSeconds(getConfig().getLong("telegram.timeout_seconds", 10L));
        TelegramClient client = new TelegramClient(token, getLogger(), timeout);
        MessageRenderer.Language language =
                MessageRenderer.Language.fromKey(getConfig().getString("notify.language", "ru"));
        String serverName = getConfig().getString("notify.server_name",
                getServer().getName() + " " + getServer().getVersion());
        MessageRenderer renderer = new MessageRenderer(language, serverName);
        return new TelegramNotifier(client, chatId, renderer);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("argus");
        if (command == null) {
            getLogger().severe("Команда /argus не описана в plugin.yml — отключаюсь.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ArgusCommand handler = new ArgusCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private String describeThresholds() {
        return "mspt " + thresholds.warnMspt() + "/" + thresholds.criticalMspt()
                + ", tps " + thresholds.warnTps() + "/" + thresholds.criticalTps()
                + ", heap " + thresholds.warnHeapRatio() + "/" + thresholds.criticalHeapRatio()
                + ", тик " + (long) thresholds.tickBudgetMs() + "мс x" + thresholds.spikesBeforeAlarm();
    }

    public MetricsSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    public IncidentLog incidentLog() {
        return incidentLog;
    }

    public DiagnosisEngine engine() {
        return engine;
    }

    public HotSpotScanner scanner() {
        return scanner;
    }

    public Notifier notifier() {
        return notifier;
    }

    public GcStats gcStats() {
        return gcStats;
    }

    public PluginProfiler profiler() {
        return profiler;
    }

    public ChunkChurn chunkChurn() {
        return chunkChurn;
    }

    public TickTimer tickTimer() {
        return tickTimer;
    }

    public SchedulerFacade scheduler() {
        return scheduler;
    }

    public void reloadSettings() {
        reloadConfig();
        thresholds = readThresholds();
        engine.reset();
        healer = readHealPolicy();
        incidentLog.clear();
        scanner.reset();
        pendingDiagnosis = null;

        // Токен и чат намеренно не перечитываются: смена бота требует перезапуска.
        getLogger().info("Пороги и план лечения применены. Для смены Telegram-токена нужен перезапуск сервера.");
    }
}
