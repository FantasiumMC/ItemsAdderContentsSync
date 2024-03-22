package com.epicplayera10.itemsaddercontentssync.pluginsreload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record ReloadPluginProperties(
        String pluginName,
        String reloadCommand,
        List<String> dontReloadWhenThesePluginsWereReloaded,
        CompletableFuture<Void> customReloadFuture
) {

    public ReloadPluginProperties(String pluginName, String reloadCommand) {
        this(pluginName, reloadCommand, new ArrayList<>(), null);
    }

    public ReloadPluginProperties withCustomReloadFuture(CompletableFuture<Void> customReloadFuture) {
        return new ReloadPluginProperties(pluginName(), reloadCommand(), dontReloadWhenThesePluginsWereReloaded(), customReloadFuture);
    }

    public ReloadPluginProperties withDontReloadWhenThesePluginsWereReloaded(String... dontReloadWhenThesePluginsWereReloaded) {
        return new ReloadPluginProperties(pluginName(), reloadCommand(), List.of(dontReloadWhenThesePluginsWereReloaded), customReloadFuture());
    }
}
