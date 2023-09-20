package com.epicplayera10.itemsaddercontentssync.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.epicplayera10.itemsaddercontentssync.IASyncManager;
import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;

@CommandAlias("iacs|itemsaddercontentssync")
@CommandPermission("iacs.admin")
public class MainCommand extends BaseCommand {

    @HelpCommand
    public void doHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("sync")
    @Description("Synchronizuje paczkę")
    @Syntax("[force]")
    public void sync(CommandSender sender, @Default("false") boolean force) {
        if (IASyncManager.isSyncing()) {
            sender.sendMessage("Już trwa proces synchronizacji!");
            return;
        }

        IASyncManager.syncPack(force).whenComplete((wasNewerVersion, ex) -> {
            if (ex == null) {
                if (wasNewerVersion) {
                    sender.sendMessage("Pomyślnie zsynchronizowano!");
                } else {
                    sender.sendMessage("Już posiadasz najnowszą wersje paczki");
                }
            } else {
                sender.sendMessage("Wystąpił błąd podczas synchronizowania. Po więcej informacji zobacz konsole.");
                ItemsAdderContentsSync.instance().getLogger().log(Level.SEVERE, "An error occured while syncing ItemsAdder files!", ex);
            }
        });
    }

    @Subcommand("reload")
    @Description("Przeładowywuje plugin")
    public void reload(CommandSender sender) {
        ItemsAdderContentsSync.instance().reload();
        sender.sendMessage("Przeładowano!");
    }
}
