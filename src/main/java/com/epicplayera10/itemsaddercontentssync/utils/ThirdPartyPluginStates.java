package com.epicplayera10.itemsaddercontentssync.utils;

import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

public class ThirdPartyPluginStates {
    public CompletableFuture<Void> itemsAdderReloadingFuture = new CompletableFuture<>();

    public CompletableFuture<Void> modelEngineReloadingFuture = new CompletableFuture<>();

    public ThirdPartyPluginStates() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            modelEngineReloadingFuture.complete(null);
        }
    }

    public boolean isAllReloaded() {
        return itemsAdderReloadingFuture.isDone() && modelEngineReloadingFuture.isDone();
    }
}
