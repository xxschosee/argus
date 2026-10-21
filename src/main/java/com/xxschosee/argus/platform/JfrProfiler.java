package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.PluginProfiler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/**
 * Считает, кто занимает время главного потока, через JFR.
 *
 * Почему JFR, а не сэмплирование стека вручную: getStackTrace с другого потока
 * требует, чтобы поток встал в safepoint, и длинный непрерывный участок кода
 * туда заходит редко. Из-за этого плагин, занимающий 93% времени, показывался
 * как 53%. JFR сэмплирует по сигналам, а не по safepoint, поэтому смещения нет.
 *
 * JFR уже встроен в Java 21, ничего скачивать и вшивать не надо. Именно так же
 * работает spark, только у него свой нативный агент.
 */
public final class JfrProfiler {

    /** События, которые JFR пишет по умолчанию, а нам они не нужны. */
    private static final String[] NOISY_EVENTS = {
            "jdk.ObjectAllocationSample", "jdk.ObjectAllocationInNewTLAB",
            "jdk.JavaMonitorEnter", "jdk.JavaMonitorWait",
            "jdk.ThreadPark", "jdk.ThreadSleep", "jdk.ThreadStateTransition",
            "jdk.GCPhasePause", "jdk.YoungGarbageCollection", "jdk.OldGarbageCollection",
            "jdk.GarbageCollection", "jdk.TenuringDistribution", "jdk.MetaspaceSummary",
            "jdk.FileWrite", "jdk.FileRead", "jdk.SocketRead", "jdk.SocketWrite",
            "jdk.SystemProcess", "jdk.ClassLoading", "jdk.CPULoad", "jdk.ThreadCPULoad"
    };

    private final PluginOwnership ownership;
    private final PluginProfiler profiler;
    private final long samplePeriodMillis;
    private final java.util.function.Supplier<Thread> mainThread;
    private final boolean nativeSamples;

    private Recording recording;
    private Path file;

    public JfrProfiler(PluginOwnership ownership, PluginProfiler profiler,
                       long samplePeriodMillis, java.util.function.Supplier<Thread> mainThread,
                       boolean nativeSamples) {
        this.ownership = ownership;
        this.profiler = profiler;
        this.samplePeriodMillis = Math.max(1L, samplePeriodMillis);
        this.mainThread = mainThread;
        this.nativeSamples = nativeSamples;
    }

    /** Начать запись. */
    public void start() {
        if (recording != null) {
            return;
        }
        try {
            cleanStale();
            file = Files.createTempFile("argus-jfr", ".jfr");
            recording = new Recording();
            // В дефолтной конфигурации JFR включено около сорока типов событий:
            // аллокации, мониторы, парки потоков, чтение файлов и сокетов.
            // Нам нужны только образцы стека, всё остальное выключаем.
            for (String noisy : NOISY_EVENTS) {
                try {
                    recording.disable(noisy);
                } catch (RuntimeException ignored) {
                    // события может не быть в этой сборке
                }
            }
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(samplePeriodMillis));
            if (nativeSamples) {
                try {
                    recording.enable("jdk.NativeMethodSample")
                            .withPeriod(Duration.ofMillis(samplePeriodMillis));
                } catch (RuntimeException ignored) {
                    // этого события может не быть в некоторых сборках jdk
                }
            }
            recording.setDestination(file);
            recording.start();
        } catch (IOException | RuntimeException e) {
            stop();
            throw new IllegalStateException("не удалось запустить JFR", e);
        }
    }

    /**
     * Убрать файлы, оставшиеся от прошлого запуска.
     * Если сервер убили, а не остановили, onDisable не вызывается и файл остаётся.
     */
    private void cleanStale() {
        try (java.util.stream.Stream<Path> files = Files.list(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")))) {
            files.filter(p -> p.getFileName().toString().startsWith("argus-jfr"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // временный файл, ничего страшного
                        }
                    });
        } catch (IOException | RuntimeException ignored) {
            // временный каталог недоступен, это не критично
        }
    }

    /**
     * Остановить запись, разобрать её и начать новую.
     * Так размер файла не растёт бесконечно.
     */
    /**
     * Остановить запись, разобрать её и не начинать новую.
     * Используется, когда профиль снимается только на время инцидента.
     */
    public void collectAndStop() {
        if (recording == null) {
            return;
        }
        long started = System.nanoTime();
        try {
            recording.stop();
            recording.close();
            recording = null;
            parse();
        } catch (RuntimeException e) {
            stop();
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        java.util.logging.Logger.getLogger("Argus").info(
                "Профиль собран: " + profiler.sampleCount() + " образцов, разбор занял " + millis + " мс");
    }

    public void rotate() {
        if (recording == null) {
            start();
            return;
        }
        long started = System.nanoTime();
        try {
            recording.stop();
            recording.close();
            recording = null;
            parse();
        } catch (RuntimeException e) {
            stop();
        } finally {
            start();
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        java.util.logging.Logger.getLogger("Argus").info(
                "Профиль обновлён: " + profiler.sampleCount() + " образцов, разбор занял " + millis + " мс");
    }

    private void parse() {
        if (file == null) {
            return;
        }
        long raw = 0L;
        long accepted = 0L;
        try (RecordingFile source = new RecordingFile(file)) {
            while (source.hasMoreEvents()) {
                RecordedEvent event = source.readEvent();
                String type = event.getEventType().getName();
                if (!"jdk.ExecutionSample".equals(type) && !"jdk.NativeMethodSample".equals(type)) {
                    continue;
                }
                raw++;
                if (!isMainThread(event)) {
                    continue;
                }
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null) {
                    continue;
                }
                accepted++;
                profiler.record(ownerOf(stack), System.currentTimeMillis());
            }
        } catch (IOException | UncheckedIOException e) {
            // повреждённый файл не должен ронять плагин
        } finally {
            if (raw > 0 && accepted == 0) {
                java.util.logging.Logger.getLogger("Argus").warning(
                        "Профиль: образцов в записи " + raw + ", но главный поток не распознан.");
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // временный файл, ничего страшного
            }
            file = null;
        }
    }

    private boolean isMainThread(RecordedEvent event) {
        Thread thread = mainThread.get();
        if (thread == null) {
            return false;
        }
        jdk.jfr.consumer.RecordedThread recorded = event.getThread("sampledThread");
        if (recorded == null) {
            recorded = event.getThread();
        }
        return recorded != null && recorded.getJavaThreadId() == thread.getId();
    }

    /**
     * Владелец кадра: идём сверху вниз и берём первый кадр, который
     * принадлежит плагину. Если такого нет, значит время тратит сам сервер.
     */
    private String ownerOf(RecordedStackTrace stack) {
        for (RecordedFrame frame : stack.getFrames()) {
            RecordedMethod method = frame.getMethod();
            if (method == null || method.getType() == null) {
                continue;
            }
            String owner = ownership.ownerOf(method.getType().getName());
            if (!PluginProfiler.SERVER.equals(owner)) {
                return owner;
            }
        }
        return PluginProfiler.SERVER;
    }

    public boolean isRunning() {
        return recording != null;
    }

    public void stop() {
        if (recording != null) {
            try {
                recording.stop();
                recording.close();
            } catch (RuntimeException ignored) {
                // уже закрыта
            }
            recording = null;
        }
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // временный файл
            }
            file = null;
        }
    }
}
