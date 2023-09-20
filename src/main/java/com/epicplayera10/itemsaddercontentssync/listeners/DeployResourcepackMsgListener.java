package com.epicplayera10.itemsaddercontentssync.listeners;

import com.epicplayera10.itemsaddercontentssync.IASyncManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class DeployResourcepackMsgListener implements PluginMessageListener {
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        IASyncManager.syncPack(false).thenAccept(wasNewerVersion -> {

        });
    }
}
