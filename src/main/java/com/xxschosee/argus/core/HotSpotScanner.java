package com.xxschosee.argus.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Инкрементальный поиск виновника деградации.
 *
 * Почему инкрементально: подсчёт сущностей в чанке — дорогая операция, и обойти
 * все чанки разом значит устроить лаг самому себе. Поэтому скан идёт частями:
 * за тик обрабатывается не больше {@code budget} чанков, результат собирается
 * постепенно, а админ получает список виновников через несколько секунд.
 *
 * Ядро не знает про Bukkit — источник чанков задаётся снаружи, поэтому вся логика
 * бюджета и сортировки проверяется юнит-тестами.
 */
public final class HotSpotScanner {

    /** Один чанк для опроса. Дорогие методы вызываются лениво и не чаще раза за проход. */
    public interface ChunkProbe {
        String world();

        int chunkX();

        int chunkZ();

        int entityCount();

        int blockEntityCount();
    }

    /** Источник списка загруженных чанков. */
    public interface ChunkSource {
        /** Пересобрать список загруженных чанков — в начале нового цикла скана. */
        void refresh();

        /** Сколько сейчас чанков доступно для опроса. */
        int size();

        /** Описать чанк по индексу. */
        ChunkProbe probe(int index);
    }

    private final ChunkSource source;
    private final int topN;

    private int cursor;
    private int scanned;
    private boolean complete;
    private final List<HotChunk> found = new ArrayList<>();

    public HotSpotScanner(ChunkSource source, int topN) {
        if (source == null) {
            throw new IllegalArgumentException("source обязателен");
        }
        if (topN < 1) {
            throw new IllegalArgumentException("topN должен быть >= 1");
        }
        this.source = source;
        this.topN = topN;
    }

    /**
     * Обработать не больше {@code budgetPerTick} чанков.
     *
     * @return текущее состояние скана
     */
    public ScanResult scan(int budgetPerTick) {
        if (complete) {
            return result();
        }
        if (budgetPerTick < 1) {
            throw new IllegalArgumentException("budgetPerTick должен быть >= 1");
        }
        if (cursor == 0 && scanned == 0) {
            source.refresh();
        }

        int size = source.size();
        int processed = 0;
        while (cursor < size && processed < budgetPerTick) {
            ChunkProbe probe = source.probe(cursor);
            if (probe != null) {
                consider(new HotChunk(probe.world(), probe.chunkX(), probe.chunkZ(),
                        probe.entityCount(), probe.blockEntityCount()));
            }
            cursor++;
            scanned++;
            processed++;
        }

        if (cursor >= size) {
            complete = true;
        }
        return result();
    }

    /** Начать новый цикл скана с нуля. */
    public void reset() {
        cursor = 0;
        scanned = 0;
        complete = false;
        found.clear();
    }

    public boolean isComplete() {
        return complete;
    }

    public int scanned() {
        return scanned;
    }

    private void consider(HotChunk chunk) {
        if (chunk.entityCount() <= 0 && chunk.blockEntityCount() <= 0) {
            return;
        }
        found.add(chunk);
        found.sort(Comparator.comparingInt(HotChunk::entityCount).reversed()
                .thenComparing(Comparator.comparingInt(HotChunk::blockEntityCount).reversed()));
        while (found.size() > topN) {
            found.remove(found.size() - 1);
        }
    }

    private ScanResult result() {
        List<HotChunk> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingInt(HotChunk::entityCount).reversed()
                .thenComparing(Comparator.comparingInt(HotChunk::blockEntityCount).reversed()));
        return new ScanResult(List.copyOf(sorted), complete, scanned);
    }

    /** Результат скана на текущий момент. */
    public record ScanResult(List<HotChunk> hottest, boolean complete, int scanned) {

        public ScanResult {
            hottest = List.copyOf(hottest);
        }

        /** Самый вероятный виновник, если он есть. */
        public HotChunk worst() {
            return hottest.isEmpty() ? null : hottest.get(0);
        }
    }
}
