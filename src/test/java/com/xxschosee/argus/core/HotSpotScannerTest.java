package com.xxschosee.argus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HotSpotScannerTest {

    /** Фейковый источник: N чанков с заданным числом сущностей. */
    private static final class FakeSource implements HotSpotScanner.ChunkSource {

        private final int count;
        private final int[] entities;
        private int refreshes;

        FakeSource(int count, int... entityCounts) {
            this.count = count;
            this.entities = new int[count];
            for (int i = 0; i < count && i < entityCounts.length; i++) {
                this.entities[i] = entityCounts[i];
            }
        }

        @Override
        public void refresh() {
            refreshes++;
        }

        @Override
        public int size() {
            return count;
        }

        @Override
        public HotSpotScanner.ChunkProbe probe(int index) {
            final int e = entities[index];
            return new HotSpotScanner.ChunkProbe() {
                @Override
                public String world() {
                    return "world";
                }

                @Override
                public int chunkX() {
                    return index;
                }

                @Override
                public int chunkZ() {
                    return -index;
                }

                @Override
                public int entityCount() {
                    return e;
                }

                @Override
                public int blockEntityCount() {
                    return 0;
                }
            };
        }
    }

    @Test
    @DisplayName("За один вызов обрабатывается не больше заданного бюджета")
    void respectsBudget() {
        FakeSource source = new FakeSource(100);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);

        HotSpotScanner.ScanResult result = scanner.scan(10);

        assertEquals(10, result.scanned());
        assertEquals(10, scanner.scanned());
        assertFalse(result.complete());
    }

    @Test
    @DisplayName("Скан завершается, когда все чанки обработаны")
    void completesWhenAllChunksScanned() {
        FakeSource source = new FakeSource(25);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);

        HotSpotScanner.ScanResult result = null;
        for (int i = 0; i < 10; i++) {
            result = scanner.scan(10);
            if (result.complete()) {
                break;
            }
        }
        assertNotNull(result);
        assertTrue(result.complete());
        assertEquals(25, result.scanned());
    }

    @Test
    @DisplayName("Список чанков пересобирается один раз за цикл")
    void refreshesOncePerCycle() {
        FakeSource source = new FakeSource(50);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);

        scanner.scan(10);
        scanner.scan(10);
        scanner.scan(10);

        assertEquals(1, source.refreshes, "список чанков должен пересобираться один раз за цикл");
    }

    @Test
    @DisplayName("Новый цикл снова пересобирает список")
    void resetStartsNewCycle() {
        FakeSource source = new FakeSource(30);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);
        scanner.scan(30);
        assertTrue(scanner.isComplete());

        scanner.reset();
        assertFalse(scanner.isComplete());
        scanner.scan(30);
        assertEquals(2, source.refreshes);
    }

    @Test
    @DisplayName("Хранится только top-N самых горячих чанков, и они отсортированы")
    void keepsTopN() {
        FakeSource source = new FakeSource(10, 5, 900, 20, 700, 10, 100, 1, 2, 3, 4);
        HotSpotScanner scanner = new HotSpotScanner(source, 3);

        HotSpotScanner.ScanResult result = scanner.scan(10);

        assertEquals(3, result.hottest().size());
        assertEquals(900, result.hottest().get(0).entityCount());
        assertEquals(700, result.hottest().get(1).entityCount());
        assertEquals(100, result.hottest().get(2).entityCount());
    }

    @Test
    @DisplayName("Самый вероятный виновник доступен отдельным методом")
    void worstIsTheHottest() {
        FakeSource source = new FakeSource(3, 1, 500, 50);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);
        HotSpotScanner.ScanResult result = scanner.scan(3);
        assertNotNull(result.worst());
        assertEquals(500, result.worst().entityCount());
    }

    @Test
    @DisplayName("Пустые чанки в отчёт не попадают")
    void skipsEmptyChunks() {
        FakeSource source = new FakeSource(5, 0, 0, 0, 0, 0);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);
        HotSpotScanner.ScanResult result = scanner.scan(5);
        assertTrue(result.hottest().isEmpty());
        assertEquals(null, result.worst());
    }

    @Test
    @DisplayName("Нулевой бюджет — ошибка, а не бесконечный цикл")
    void rejectsZeroBudget() {
        HotSpotScanner scanner = new HotSpotScanner(new FakeSource(1), 1);
        try {
            scanner.scan(0);
            throw new AssertionError("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("budgetPerTick"));
        }
    }

    @Test
    @DisplayName("Цель чанка собирается в формате chunk:world:x,z")
    void targetFormat() {
        HotChunk chunk = new HotChunk("world", 12, -8, 100, 3);
        assertEquals("chunk:world:12,-8", chunk.target());
        assertEquals("world:12,-8", chunk.shortName());
    }

    @Test
    @DisplayName("Список горячих чанков неизменяемый")
    void resultIsImmutable() {
        FakeSource source = new FakeSource(3, 1, 2, 3);
        HotSpotScanner scanner = new HotSpotScanner(source, 5);
        HotSpotScanner.ScanResult result = scanner.scan(3);
        List<HotChunk> hottest = result.hottest();
        try {
            hottest.add(new HotChunk("x", 0, 0, 0, 0));
            throw new AssertionError("ожидалась UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertTrue(new ArrayList<>(hottest).size() >= 1);
        }
    }
}
