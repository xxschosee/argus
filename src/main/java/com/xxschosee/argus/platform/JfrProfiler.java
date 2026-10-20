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

    private final PluginOwnership ownership;
    private final PluginProfiler profiler;
    private final long samplePeriodMillis;
    private final java.util.function.Supplier<Thread> mainThread;

    private Recording recording;
    private Path file;

    public JfrProfiler(PluginOwnership ownership, PluginProfiler profiler,
                       long samplePeriodMillis, java.util.function.Supplier<Thread> mainThread) {
        this.ownership = ownership;
        this.profiler = profiler;
        this.samplePeriodMillis = Math.max(1L, samplePeriodMillis);
        this.mainThread = mainThread;
    }

    /** Начать запись. */
    public void start() {
        if (recording != null) {
            return;
        }
        try {
            file = Files.createTempFile("argus-jfr", ".jfr");
            recording = new Recording();
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(samplePeriodMillis));
            try {
                recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(samplePeriodMillis));
            } catch (RuntimeException ignored) {
                // этого события может не быть в некоторых сборках jdk
            }
            recording.setDestination(file);
            recording.start();
        } catch (IOException | RuntimeException e) {
            stop();
            throw new IllegalStateException("не удалось запустить JFR", e);
        }
    }

    /**
     * Остановить запись, разобрать её и начать новую.
     * Так размер файла не растёт бесконечно.
     */
    public void rotate() {
        if (recording == null) {
            start();
            return;
        }
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
    }

    private void parse() {
        if (file == null) {
            return;
        }
        try (RecordingFile source = new RecordingFile(file)) {
            while (source.hasMoreEvents()) {
                RecordedEvent event = source.readEvent();
                String type = event.getEventType().getName();
                if (!"jdk.ExecutionSample".equals(type) && !"jdk.NativeMethodSample".equals(type)) {
                    continue;
                }
                if (!isMainThread(event)) {
                    continue;
                }
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null) {
                    continue;
                }
                profiler.record(ownerOf(stack), System.currentTimeMillis());
            }
        } catch (IOException | UncheckedIOException e) {
            // повреждённый файл не должен ронять плагин
        } finally {
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
