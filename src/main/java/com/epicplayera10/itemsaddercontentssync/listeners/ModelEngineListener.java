package com.epicplayera10.itemsaddercontentssync.listeners;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ModelEngineListener implements Listener {
    @EventHandler
    public void onFinalPhase(ModelRegistrationEvent event) {
        if (event.getPhase().toString().equals("FINISHED") || // After R4.0.0
            event.getPhase().toString().equals("FINAL")) { // Before R4.0.0
            ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture.complete(null);
        }
    }
}
