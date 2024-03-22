package com.epicplayera10.itemsaddercontentssync.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
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
        if (ItemsAdderContentsSync.instance().getPack().isSyncing()) {
            sender.sendMessage(Component.text("Już trwa proces synchronizacji!").color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Synchronizowanie...").color(NamedTextColor.GREEN));

        ItemsAdderContentsSync.instance().getPack().syncPack(force, sender).whenComplete((wasNewerVersion, ex) -> {
            if (ex == null) {
                if (wasNewerVersion) {
                    sender.sendMessage(Component.text("Pomyślnie zsynchronizowano!").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Już posiadasz najnowszą wersje paczki").color(NamedTextColor.RED));
                }
            } else {
                sender.sendMessage(Component.text("Wystąpił błąd podczas synchronizowania. Po więcej informacji zobacz konsole.").color(NamedTextColor.RED));
                ItemsAdderContentsSync.instance().getLogger().log(Level.SEVERE, "An error occured while syncing ItemsAdder files!", ex);
            }
        });
    }

    @Subcommand("reload")
    @Description("Przeładowywuje plugin")
    public void reload(CommandSender sender) {
        ItemsAdderContentsSync.instance().reload();
        sender.sendMessage(Component.text("Przeładowano!").color(NamedTextColor.GREEN));
    }

    @Subcommand("resethead")
    @Description("Resetuje repozytorium do HEADa (git reset --hard HEAD)")
    public void resetHead(CommandSender sender) {
        sender.sendMessage(Component.text("Resetuje repozytorium do HEADa...").color(NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskAsynchronously(ItemsAdderContentsSync.instance(), () -> {
            try (Git git = ItemsAdderContentsSync.instance().getPack().getOrInitGit()) {

                // Delete untracked files too
                git.clean()
                    .setForce(true)
                    .setCleanDirectories(true)
                    .call();

                git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef("HEAD")
                    .call();

                sender.sendMessage(Component.text("Pomyślnie zresetowano repozytorium do HEADa!").color(NamedTextColor.GREEN));
            } catch (GitAPIException | IOException e) {
                sender.sendMessage(Component.text("Wystąpił błąd! Po więcej informacji zobacz konsolę").color(NamedTextColor.RED));
                throw new RuntimeException(e);
            }
        });
    }
}
