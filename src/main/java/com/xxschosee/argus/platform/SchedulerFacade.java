package com.xxschosee.argus.platform;

import org.bukkit.plugin.Plugin;

/**
 * Абстракция над планировщиками Bukkit.
 *
 * Сделана сразу в форме, пригодной для Folia: глобальные задачи, асинхронные
 * задачи и задачи, привязанные к региону. Сегодня реализация одна — Bukkit,
 * но переход на Folia — это добавление ещё одного класса, а не переписывание
 * вызывающего кода.
 */
public interface SchedulerFacade {

    /** Выполнить задачу в глобальном контексте (на Paper — основной поток). */
    void runGlobal(Runnable task);

    /** Повторяющаяся задача в глобальном контексте. */
    void runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks);

    /** Выполнить задачу вне основного потока. Только I/O, никаких обращений к Bukkit. */
    void runAsync(Runnable task);

    /** Повторяющаяся асинхронная задача. */
    void runAsyncTimer(Runnable task, long initialDelayTicks, long periodTicks);

    /** Отменить все задачи плагина. Вызывается при выключении. */
    void cancelAll();

    /** Человекочитаемое имя реализации — попадает в лог при старте. */
    String name();

    /** Реализация поверх классического Bukkit-планировщика. */
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
