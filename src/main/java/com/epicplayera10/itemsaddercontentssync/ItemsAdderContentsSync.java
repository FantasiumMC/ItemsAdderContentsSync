package com.epicplayera10.itemsaddercontentssync;

import co.aikar.commands.PaperCommandManager;
import com.epicplayera10.itemsaddercontentssync.commands.MainCommand;
import com.epicplayera10.itemsaddercontentssync.configuration.ConfigurationFactory;
import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.listeners.ItemsAdderListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;

public final class ItemsAdderContentsSync extends JavaPlugin {

    private final File pluginConfigurationFile = new File(this.getDataFolder(), "config.yml");

    private final File packDir = new File(this.getDataFolder(), "packrepo");

    private static ItemsAdderContentsSync instance;
    private Plugin itemsAdderInstance;

    private PluginConfiguration pluginConfiguration;

    private boolean itemsAdderReloading = true;

    private BukkitTask syncTask = null;

    @Override
    public void onEnable() {
        instance = this;
        itemsAdderInstance = Bukkit.getPluginManager().getPlugin("ItemsAdder");

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(this.pluginConfigurationFile);

        CredentialsProvider.setDefault(this.pluginConfiguration.credentials);

        Bukkit.getPluginManager().registerEvents(new ItemsAdderListener(), this);

        // Register command
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.enableUnstableAPI("help");
        manager.enableUnstableAPI("brigadier");

        manager.registerCommand(new MainCommand());

        // Start task
        startSyncTask();
    }

    private void startSyncTask() {
        if (this.pluginConfiguration.syncRepeatMinutes != -1) {
            long ticks = (long) this.pluginConfiguration.syncRepeatMinutes * 60 * 20;

            this.syncTask = Bukkit.getScheduler().runTaskTimer(this, () -> IASyncManager.syncPack(false), ticks, ticks);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

    }

    public void reload() {
        this.pluginConfiguration.load();

        if (syncTask != null) {
            syncTask.cancel();
        }

        startSyncTask();
    }

    public static ItemsAdderContentsSync instance() {
        return instance;
    }

    public PluginConfiguration getPluginConfiguration() {
        return pluginConfiguration;
    }

    public File getPackDir() {
        return packDir;
    }

    public boolean isItemsAdderReloading() {
        return itemsAdderReloading;
    }

    @ApiStatus.Internal
    public void setItemsAdderReloading(boolean itemsAdderReloading) {
        this.itemsAdderReloading = itemsAdderReloading;
    }

    public Plugin getItemsAdderInstance() {
        return itemsAdderInstance;
    }

    public boolean canItemsAdderCreateResourcepack() {
        FileConfiguration iaConfig = ItemsAdderContentsSync.instance().getItemsAdderInstance().getConfig();
        ConfigurationSection hostingSection = iaConfig.getConfigurationSection("resource-pack.hosting");

        return hostingSection.getBoolean("auto-external-host.enabled") || hostingSection.getBoolean("self-host.enabled");
    }
}
