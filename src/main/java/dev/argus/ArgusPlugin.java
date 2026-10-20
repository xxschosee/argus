package dev.argus;

import dev.argus.core.Cause;
import dev.argus.core.Diagnosis;
import dev.argus.core.DiagnosisEngine;
import dev.argus.core.HealAction;
import dev.argus.core.HealEngine;
import dev.argus.core.HotChunk;
import dev.argus.core.HotSpotScanner;
import dev.argus.core.IncidentLog;
import dev.argus.core.MetricsSnapshot;
import dev.argus.core.Thresholds;
import dev.argus.notify.Notifier;
import dev.argus.notify.MessageRenderer;
import dev.argus.notify.TelegramClient;
import dev.argus.notify.TelegramNotifier;
import dev.argus.platform.BukkitChunkSource;
import dev.argus.platform.BukkitHealExecutor;
import dev.argus.platform.BukkitMetricsCollector;
import dev.argus.platform.HealExecutor;
import dev.argus.platform.MetricsCollector;
import dev.argus.platform.SchedulerFacade;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Argus — сторож и авто-лечитель для Paper-серверов.
 *
 * Логика плагина разделена на три слоя:
 * <ul>
 *   <li>{@code dev.argus.core} — чистое ядро: метрики, диагнозы, план лечения.
 *       Не знает про Bukkit, полностью покрыто юнит-тестами.</li>
 *   <li>{@code dev.argus.platform} — адаптеры к серверу: сбор метрик, сканирование
 *       чанков, применение действий, планировщик.</li>
 *   <li>{@code dev.argus.notify} — доставка сообщений админу (Telegram).</li>
 * </ul>
 */
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

    /** Метрики последнего съёма — для команды /argus status. */
    private volatile MetricsSnapshot lastSnapshot;

    /** Диагноз, для которого сейчас ищется виновник. */
    private volatile Diagnosis pendingDiagnosis;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("Argus выключен в конфиге — плагин ничего не делает.");
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
        collector = new BukkitMetricsCollector(getServer());
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

        registerCommand();

        long period = Math.max(20L, getConfig().getLong("sample.interval_ticks", 100L));
        scheduler.runGlobalTimer(this::tick, period, period);

        getLogger().info("Argus запущен. Пороги: " + describeThresholds()
                + " | уведомления: " + notifier.name()
                + " | планировщик: " + scheduler.name());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getLogger().info("Argus остановлен.");
    }

    // ------------------------------------------------------------------
    // основной цикл
    // ------------------------------------------------------------------

    private void tick() {
        MetricsSnapshot snapshot;
        try {
            snapshot = collector.capture();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "[Argus] Не удалось снять метрики", e);
            return;
        }
        lastSnapshot = snapshot;

        for (Diagnosis diagnosis : engine.analyze(snapshot)) {
            handleDiagnosis(diagnosis);
        }

        advanceHotspotSearch();
    }

    private void handleDiagnosis(Diagnosis diagnosis) {
        incidentLog.record(diagnosis, System.currentTimeMillis());

        if (diagnosis.resolved()) {
            getLogger().info("[Argus] Проблема ушла: " + diagnosis.cause().key());
            notifyAsync(diagnosis, List.of());
            return;
        }

        getLogger().warning("[Argus] " + diagnosis.severity() + " " + diagnosis.cause().key()
                + " — " + diagnosis.summary());

        List<HealAction> performed = applyPlan(diagnosis, null);
        notifyAsync(diagnosis, performed);

        if (needsHotspot(diagnosis.cause()) && pendingDiagnosis == null) {
            pendingDiagnosis = diagnosis;
            scanner.reset();
        }
    }

    /** Выполнить действия, которые не требуют поиска виновника. */
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
                getLogger().log(Level.WARNING, "[Argus] Действие " + action.kind().key() + " не удалось", e);
            }
        }
        return performed;
    }

    /**
     * Продвинуть поиск виновника. Скан идёт частями, поэтому виновник находится
     * через несколько секунд после постановки диагноза.
     */
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
            getLogger().info("[Argus] Явного виновника не нашлось (просканировано чанков: " + result.scanned() + ")");
            notifier.info(diagnosisText(diagnosis) + " Явного виновника не нашлось.");
            return;
        }

        getLogger().info("[Argus] Виновник: " + worst.shortName()
                + " (сущностей " + worst.entityCount() + ", блочных " + worst.blockEntityCount() + ")");

        List<HealAction> performed = applyPlan(diagnosis, worst.target());
        // Виновника сообщаем всегда, даже если автоматических действий не было -
        // иначе админ не поймёт, что именно искать.
        notifier.info(diagnosisText(diagnosis)
                + " Виновник: " + worst.shortName()
                + " (сущностей " + worst.entityCount() + ", блочных " + worst.blockEntityCount() + ")");
    }

    private void notifyAsync(Diagnosis diagnosis, List<HealAction> performed) {
        scheduler.runAsync(() -> notifier.notify(diagnosis, performed));
    }

    private String diagnosisText(Diagnosis diagnosis) {
        return diagnosis.cause().key() + ": " + diagnosis.summary();
    }

    private boolean needsHotspot(Cause cause) {
        return cause == Cause.ENTITY_OVERLOAD || cause == Cause.CHUNK_OVERLOAD || cause == Cause.HIGH_MSPT;
    }

    // ------------------------------------------------------------------
    // конфиг
    // ------------------------------------------------------------------

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
                Math.max(1, cfg.getInt("thresholds.samples_before_alarm", 3)));
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
                + ", heap " + thresholds.warnHeapRatio() + "/" + thresholds.criticalHeapRatio();
    }

    // ------------------------------------------------------------------
    // доступ для команды
    // ------------------------------------------------------------------

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

    public SchedulerFacade scheduler() {
        return scheduler;
    }

    /** Перечитать конфиг и применить новые пороги. */
    public void reloadSettings() {
        reloadConfig();
        thresholds = readThresholds();
        engine.reset();
        healer = readHealPolicy();
        incidentLog.clear();
        scanner.reset();
        pendingDiagnosis = null;
        // Токен и чат намеренно не перечитываются: смена бота требует перезапуска,
        // иначе старый бот продолжит слать сообщения в старый чат.
        getLogger().info("Пороги и план лечения применены. Для смены Telegram-токена нужен перезапуск сервера.");
    }
}
