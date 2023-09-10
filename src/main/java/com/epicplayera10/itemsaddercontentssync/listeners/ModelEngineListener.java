package com.epicplayera10.itemsaddercontentssync.listeners;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ModelEngineListener implements Listener {
    @EventHandler
    public void onFinalPhase(ModelRegistrationEvent event) {
        if (event.getPhase() == ModelRegistrationEvent.Phase.FINAL) {
            ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture.complete(null);
        }
    }
}
