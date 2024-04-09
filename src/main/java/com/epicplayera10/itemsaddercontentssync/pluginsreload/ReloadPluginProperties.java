package com.epicplayera10.itemsaddercontentssync.pluginsreload;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public record ReloadPluginProperties(
        String pluginName,
        String reloadCommand,
        List<String> dontReloadWhenThesePluginsWereReloaded,
        @Nullable Supplier<CompletableFuture<Void>> customReloadFutureSupplier
) {

    public ReloadPluginProperties(String pluginName, String reloadCommand) {
        this(pluginName, reloadCommand, new ArrayList<>(), null);
    }

    public ReloadPluginProperties withCustomReloadFuture(Supplier<CompletableFuture<Void>> customReloadFutureSupplier) {
        return new ReloadPluginProperties(pluginName(), reloadCommand(), dontReloadWhenThesePluginsWereReloaded(), customReloadFutureSupplier);
    }

    public ReloadPluginProperties withDontReloadWhenThesePluginsWereReloaded(String... dontReloadWhenThesePluginsWereReloaded) {
        return new ReloadPluginProperties(pluginName(), reloadCommand(), List.of(dontReloadWhenThesePluginsWereReloaded), customReloadFutureSupplier());
    }
}
