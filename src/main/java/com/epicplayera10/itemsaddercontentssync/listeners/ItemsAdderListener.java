package com.epicplayera10.itemsaddercontentssync.listeners;

import com.epicplayera10.itemsaddercontentssync.IASyncManager;
import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ItemsAdderListener implements Listener {

    @EventHandler
    public void onIAReloadFinish(ItemsAdderLoadDataEvent event) {
        if (event.getCause() == ItemsAdderLoadDataEvent.Cause.FIRST_LOAD) {
            ItemsAdderContentsSync.instance().setItemsAdderReloading(false);
        } else if (!ItemsAdderContentsSync.instance().canItemsAdderCreateResourcepack() && event.getCause() == ItemsAdderLoadDataEvent.Cause.RELOAD) {
            ItemsAdderContentsSync.instance().setItemsAdderReloading(false);
        }

        if (event.getCause() == ItemsAdderLoadDataEvent.Cause.FIRST_LOAD && ItemsAdderContentsSync.instance().getPluginConfiguration().syncOnStartup) {
            IASyncManager.syncPack(false);
        }
    }

    @EventHandler
    public void onIAResourcepackFinish(ItemsAdderPackCompressedEvent event) {
        ItemsAdderContentsSync.instance().setItemsAdderReloading(false);
    }
}
