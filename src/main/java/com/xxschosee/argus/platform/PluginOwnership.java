package com.xxschosee.argus.platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * Определяет, какому плагину принадлежит класс.
 *
 * Классы плагинов грузятся их загрузчиком классов, поэтому принадлежность
 * проверяется сравнением загрузчиков. Ответы кэшируются: один и тот же класс
 * в стеке встречается постоянно.
 */
public final class PluginOwnership {

    private final List<Plugin> plugins;
    private final Map<String, String> cache = new HashMap<>();

    public PluginOwnership(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public String ownerOf(String className) {
        String cached = cache.get(className);
        if (cached != null) {
            return cached;
        }
        String owner = resolve(className);
        cache.put(className, owner);
        return owner;
    }

    private String resolve(String className) {
        for (Plugin plugin : plugins) {
            try {
                Class.forName(className, false, plugin.getClass().getClassLoader());
                return plugin.getName();
            } catch (ClassNotFoundException | LinkageError ignored) {
                // класс не из этого плагина, пробуем следующий
            }
        }
        return "Paper";
    }

    public void invalidate() {
        cache.clear();
    }
}
