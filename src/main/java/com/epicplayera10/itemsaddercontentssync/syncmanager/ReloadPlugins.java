package com.epicplayera10.itemsaddercontentssync.syncmanager;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class ReloadPlugins {
    /**
     * Reloads CosmeticsCore
     */
    public static void reloadCosmeticsCore() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading CosmeticsCore");
        runCommandEnsureSync(Bukkit.getConsoleSender(), "cosmeticsconfig cosmetics reload");
    }

    /**
     * Reloads ModelEngine
     */
    public static CompletableFuture<Void> reloadModelEngine() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading ModelEngine");

        runCommandEnsureSync(Bukkit.getConsoleSender(), "meg reload");

        return ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture;
    }

    /**
     * Reloads ItemsAdder
     */
    public static CompletableFuture<Void> reloadItemsAdder() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading ItemsAdder");

        runCommandEnsureSync(Bukkit.getConsoleSender(), "iareload");
        /*if (ItemsAdderContentsSync.instance().canItemsAdderCreateResourcepack()) {
            runCommandEnsureSync(Bukkit.getConsoleSender(), "iazip");
        } else {
            runCommandEnsureSync(Bukkit.getConsoleSender(), "iareload");
        }*/
        return ItemsAdderContentsSync.instance().getThirdPartyPluginStates().itemsAdderReloadingFuture;
    }

    /**
     * Reloads MythicMobs
     */
    public static void reloadMythicMobs() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading MythicMobs");

        runCommandEnsureSync(Bukkit.getConsoleSender(), "mm reload");
    }

    /**
     * Reloads MCPets
     */
    public static void reloadMCPets() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading MCPets");

        runCommandEnsureSync(Bukkit.getConsoleSender(), "mcpets reload");
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
