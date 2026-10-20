package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.PluginProfiler;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Сэмплирует стек главного потока и раскладывает время по владельцам кода.
 *
 * Приём тот же, что в обычных профайлерах: смотрим стек через случайные
 * промежутки времени. Плагин, который занимает больше времени, попадает
 * в стек чаще, и так становится видно, кто виноват.
 *
 * Отдельный поток, а не задача Bukkit: планировщик Bukkit считает период
 * в тиках, то есть минимальный интервал у него 50 мс, а для профайлера
 * нужен более частый.
 */
public final class MainThreadSampler {

    private final Supplier<Thread> mainThread;
    private final PluginOwnership ownership;
    private final PluginProfiler profiler;
    private final ScheduledExecutorService executor;
    private volatile boolean running;

    public MainThreadSampler(Supplier<Thread> mainThread, PluginOwnership ownership, PluginProfiler profiler) {
        this.mainThread = mainThread;
        this.ownership = ownership;
        this.profiler = profiler;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Argus Sampler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Запустить сэмплирование с указанным интервалом в миллисекундах. */
    public void start(long intervalMillis) {
        long period = Math.max(5L, intervalMillis);
        running = true;
        executor.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    sample();
                } catch (Throwable ignored) {
                    // сэмплер не должен ронять сервер
                }
            }
        }, 0L, period, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    /** Снять один образец. */
    public void sample() {
        Thread thread = mainThread.get();
        if (thread == null || !thread.isAlive()) {
            return;
        }
        StackTraceElement[] frames = thread.getStackTrace();
        if (frames.length == 0) {
            return;
        }
        profiler.record(ownerOf(frames), System.currentTimeMillis());
    }

    /**
     * Владелец кадра: идём сверху вниз и берём первый кадр, который
     * принадлежит плагину. Если такого нет, значит время тратит сам сервер.
     */
    private String ownerOf(StackTraceElement[] frames) {
        for (StackTraceElement frame : frames) {
            String owner = ownership.ownerOf(frame.getClassName());
            if (!PluginProfiler.SERVER.equals(owner)) {
                return owner;
            }
        }
        return PluginProfiler.SERVER;
    }
}
