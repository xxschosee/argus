package com.xxschosee.argus.platform;

import org.bukkit.plugin.Plugin;

// Форма методов рассчитана на Folia: глобальные, асинхронные и региональные задачи
// разведены по разным методам, чтобы подходили обоим планировщикам.
public interface SchedulerFacade {

    void runGlobal(Runnable task);

    void runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks);

    void runAsync(Runnable task);

    void runAsyncTimer(Runnable task, long initialDelayTicks, long periodTicks);

    void cancelAll();

    String name();

    final class Bukkit implements SchedulerFacade {

        private final Plugin plugin;

        public Bukkit(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void runGlobal(Runnable task) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }

        @Override
        public void runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }

        @Override
        public void runAsync(Runnable task) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }

        @Override
        public void runAsyncTimer(Runnable task, long initialDelayTicks, long periodTicks) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
        }

        @Override
        public void cancelAll() {
            plugin.getServer().getScheduler().cancelTasks(plugin);
        }

        @Override
        public String name() {
            return "bukkit";
        }
    }
}
