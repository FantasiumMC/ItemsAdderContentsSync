package com.epicplayera10.itemsaddercontentssync;

import co.aikar.commands.PaperCommandManager;
import com.epicplayera10.itemsaddercontentssync.commands.MainCommand;
import com.epicplayera10.itemsaddercontentssync.configuration.ConfigurationFactory;
import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.listeners.ItemsAdderListener;
import com.epicplayera10.itemsaddercontentssync.listeners.ModelEngineListener;
import com.epicplayera10.itemsaddercontentssync.syncmanager.IASyncManager;
import com.epicplayera10.itemsaddercontentssync.utils.ThirdPartyPluginStates;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.jgit.transport.CredentialsProvider;

import java.io.File;

public final class ItemsAdderContentsSync extends JavaPlugin {

    private final File pluginConfigurationFile = new File(this.getDataFolder(), "config.yml");

    private final File repoDir = new File(this.getDataFolder(), "packrepo");

    private static ItemsAdderContentsSync instance;
    private Plugin itemsAdderInstance;

    // State manager
    private final ThirdPartyPluginStates thirdPartyPluginStates = new ThirdPartyPluginStates();

    private PluginConfiguration pluginConfiguration;

    private BukkitTask syncTask = null;

    @Override
    public void onEnable() {
        instance = this;
        itemsAdderInstance = Bukkit.getPluginManager().getPlugin("ItemsAdder");

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(this.pluginConfigurationFile);
        CredentialsProvider.setDefault(this.pluginConfiguration.credentials);

        // Listeners for proxy communication
        //Bukkit.getMessenger().registerIncomingPluginChannel(this, "iacs:deploy_resourcepack", new DeployResourcepackMsgListener());
        //Bukkit.getMessenger().registerOutgoingPluginChannel(this, "iacs:resourcepack_ready");

        Bukkit.getPluginManager().registerEvents(new ItemsAdderListener(), this);

        if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            Bukkit.getPluginManager().registerEvents(new ModelEngineListener(), this);
        }

        // Register command
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.enableUnstableAPI("help");
        manager.enableUnstableAPI("brigadier");

        manager.registerCommand(new MainCommand());

        // Sync on startup
        syncOnStartup();

        // Start task
        startSyncPackTask();
    }

    private void syncOnStartup() {
        this.getLogger().info("Syncing pack on startup...");

        boolean wasNewerVersion = IASyncManager.syncPack(false, true).join();

        if (wasNewerVersion) {
            this.getLogger().info("Synchronized successfully!");
        } else {
            this.getLogger().info("You are using the latest pack version!");
        }
    }

    private void startSyncPackTask() {
        if (this.pluginConfiguration.syncRepeatMinutes != -1) {
            long ticks = (long) this.pluginConfiguration.syncRepeatMinutes * 60 * 20;

            this.syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> IASyncManager.syncPack(false), ticks, ticks);
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

        startSyncPackTask();
    }

    public static ItemsAdderContentsSync instance() {
        return instance;
    }

    public PluginConfiguration getPluginConfiguration() {
        return pluginConfiguration;
    }

    public File getRepoDir() {
        return repoDir;
    }

    public ThirdPartyPluginStates getThirdPartyPluginStates() {
        return thirdPartyPluginStates;
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
