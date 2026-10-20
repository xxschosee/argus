package com.xxschosee.argus.core;

/**
 * Сколько чанков сервер грузит и выгружает.
 *
 * Постоянная смена чанков означает, что игроки ходят по миру, а мир упирается
 * в генерацию. Если чанки едут десятками в секунду, лаг почти всегда в этом,
 * и никакой профайлер плагинов этого не покажет.
 */
public final class ChunkChurn {

    private int loaded;
    private int unloaded;
    private int lastLoaded;
    private int lastUnloaded;
    private long lastAtMillis;

    public void onLoad() {
        loaded++;
    }

    public void onUnload() {
        unloaded++;
    }

    /** Посчитать, сколько чанков сменилось за последнюю минуту. */
    public void sample(long now) {
        if (lastAtMillis == 0L) {
            lastLoaded = loaded;
            lastUnloaded = unloaded;
            lastAtMillis = now;
            return;
        }
        long elapsed = now - lastAtMillis;
        if (elapsed < 5000L) {
            return;
        }
        lastLoaded = loaded;
        lastUnloaded = unloaded;
        lastAtMillis = now;
    }

    /** Сколько чанков загрузилось за последний интервал, в пересчёте на минуту. */
    public int perMinute() {
        if (lastAtMillis == 0L) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastAtMillis;
        if (elapsed <= 0L) {
            return 0;
        }
        return (int) ((loaded - lastLoaded) * 60000L / elapsed);
    }

    public int totalLoaded() {
        return loaded;
    }

    public int totalUnloaded() {
        return unloaded;
    }
}
