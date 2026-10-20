package com.xxschosee.argus.core;

import java.util.Arrays;

/**
 * Считает длительность каждого тика и держит скользящее окно.
 *
 * Зачем своё, если есть getAverageTickTime(): усреднение по окну съедает
 * короткие всплески. Один тик на 300 мс в среднем за минуту не виден,
 * а игроки в этот момент уже жалуются.
 */
public final class TickTimer {

    private final long[] micros;
    private int cursor;
    private int count;

    public TickTimer(int windowTicks) {
        if (windowTicks < 20) {
            throw new IllegalArgumentException("окно должно быть не меньше 20 тиков");
        }
        this.micros = new long[windowTicks];
    }

    public void record(long nanos) {
        micros[cursor] = nanos / 1000L;
        cursor = (cursor + 1) % micros.length;
        if (count < micros.length) {
            count++;
        }
    }

    public int count() {
        return count;
    }

    public void reset() {
        Arrays.fill(micros, 0L);
        cursor = 0;
        count = 0;
    }

    public double worstMs() {
        long max = 0L;
        for (int i = 0; i < count; i++) {
            if (micros[i] > max) {
                max = micros[i];
            }
        }
        return max / 1000.0;
    }

    public double averageMs() {
        if (count == 0) {
            return 0.0;
        }
        long sum = 0L;
        for (int i = 0; i < count; i++) {
            sum += micros[i];
        }
        return (sum / (double) count) / 1000.0;
    }

    public double percentileMs(double percentile) {
        if (count == 0) {
            return 0.0;
        }
        long[] sorted = Arrays.copyOf(micros, count);
        Arrays.sort(sorted);
        int index = (int) Math.round((percentile / 100.0) * (sorted.length - 1));
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index] / 1000.0;
    }

    public int ticksOver(double ms) {
        long limit = (long) (ms * 1000.0);
        int over = 0;
        for (int i = 0; i < count; i++) {
            if (micros[i] > limit) {
                over++;
            }
        }
        return over;
    }
}
