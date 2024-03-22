package com.epicplayera10.itemsaddercontentssync.pluginsreload;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.epicplayera10.itemsaddercontentssync.syncmanager.SyncPackOperation;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReloadPlugins {
    // The order of the plugins to reload.
    public static final List<ReloadPluginProperties> PLUGINS_RELOAD_ORDER = List.of(
        // Pre-reload
        new ReloadPluginProperties("ModelEngine", "meg reload")
            .withCustomReloadFuture(ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture),

        // Reload ItemsAdder
        new ReloadPluginProperties("ItemsAdder", "iareload")
            .withCustomReloadFuture(ItemsAdderContentsSync.instance().getThirdPartyPluginStates().itemsAdderReloadingFuture),

        // Post-reload
        new ReloadPluginProperties("CosmeticsCore", "cosmeticsconfig cosmetics reload")
            .withDontReloadWhenThesePluginsWereReloaded("ItemsAdder"),
        new ReloadPluginProperties("MythicMobs", "mm reload")
            .withDontReloadWhenThesePluginsWereReloaded("ItemsAdder"),
        new ReloadPluginProperties("MCPets", "mcpets reload")
    );

    /**
     * Reloads plugins
     */
    public static CompletableFuture<Void> reloadPlugins(SyncPackOperation syncPackOperation) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        // Beautiful futures chain :D
        for (ReloadPluginProperties reloadPluginProperties : ReloadPlugins.PLUGINS_RELOAD_ORDER) {
            future = reloadPlugin(syncPackOperation, reloadPluginProperties, future);
        }

        return future;
    }

    private static CompletableFuture<Void> reloadPlugin(SyncPackOperation syncPackOperation, ReloadPluginProperties reloadPluginProperties, CompletableFuture<Void> future) {
        if (!syncPackOperation.isPluginInUse(reloadPluginProperties.pluginName())) return future;

        // Don't reload plugin when some other plugin already reloaded this plugin
        for (String excludingPlugin : reloadPluginProperties.dontReloadWhenThesePluginsWereReloaded()) {
            if (syncPackOperation.isPluginInUse(excludingPlugin)) return future;
        }

        return future.thenCompose((unused) -> {
            runCommandEnsureSync(Bukkit.getConsoleSender(), reloadPluginProperties.reloadCommand());

            if (reloadPluginProperties.customReloadFuture() != null) {
                return reloadPluginProperties.customReloadFuture();
            } else {
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    private static void runCommandEnsureSync(CommandSender sender, String command) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(sender, command);
        } else {
            Bukkit.getScheduler().runTask(ItemsAdderContentsSync.instance(), () -> {
                Bukkit.dispatchCommand(sender, command);
            });
        }
    }
}
