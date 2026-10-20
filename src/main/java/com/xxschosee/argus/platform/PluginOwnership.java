package com.xxschosee.argus.platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * Определяет, какому плагину принадлежит класс.
 *
 * Классы плагинов грузятся их собственным загрузчиком, поэтому принадлежность
 * проверяется сравнением загрузчиков. Сравнивать нужно именно загрузчики:
 * Class.forName через загрузчик плагина находит и серверные классы, потому что
 * они подгружаются родителем, и тогда весь сервер приписался бы первому
 * плагину в списке.
 */
public final class PluginOwnership {

    private static final String[] SERVER_PREFIXES = {
            "net.minecraft.", "org.bukkit.", "com.destroystokyo.paper.", "io.papermc.paper.",
            "co.aikar.timings.", "java.", "javax.", "jdk.", "sun.", "com.google.",
            "com.mojang.", "org.slf4j.", "org.apache.logging.", "it.unimi.",
            "org.jetbrains.", "org.checkerframework.", "org.joml."
    };

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
        if (isServerClass(className)) {
            return "Paper";
        }
        for (Plugin plugin : plugins) {
            ClassLoader loader = plugin.getClass().getClassLoader();
            try {
                Class<?> candidate = Class.forName(className, false, loader);
                if (candidate.getClassLoader() == loader) {
                    return plugin.getName();
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // класс не из этого плагина, пробуем следующий
            }
        }
        return "Paper";
    }

    private static boolean isServerClass(String className) {
        for (String prefix : SERVER_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public void invalidate() {
        cache.clear();
    }
}
