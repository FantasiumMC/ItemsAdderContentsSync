package com.epicplayera10.itemsaddercontentssync.commands;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MainCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender.hasPermission("itemsaddercontentssync.use"))) {
            sender.sendMessage("Nie masz uprawnień!");
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(ItemsAdderContentsSync.instance(), () -> {
            ItemsAdderContentsSync.instance().syncPack();
            sender.sendMessage("Pomyślnie zsynchronizowano!");
        });

        return true;
    }
}
