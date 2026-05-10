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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.List;
import java.util.Set;
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

    @Subcommand("status")
    @Description("Sprawdza status paczki (git status)")
    public void status(CommandSender sender) {
        sender.sendMessage(Component.text("Sprawdzanie statusu repozytorium...").color(NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskAsynchronously(ItemsAdderContentsSync.instance(), () -> {
            try (Git git = ItemsAdderContentsSync.instance().getPack().getOrInitGit()) {
                Status status = git.status().call();

                Set<String> modified = status.getModified();
                Set<String> changed = status.getChanged();
                Set<String> added = status.getAdded();
                Set<String> removed = status.getRemoved();
                Set<String> missing = status.getMissing();
                Set<String> untracked = status.getUntracked();
                Set<String> conflicting = status.getConflicting();

                boolean hasChanges = !modified.isEmpty() || !changed.isEmpty() || !added.isEmpty()
                    || !removed.isEmpty() || !missing.isEmpty() || !untracked.isEmpty() || !conflicting.isEmpty();

                if (!hasChanges) {
                    sender.sendMessage(Component.text("Brak zmian w repozytorium.").color(NamedTextColor.GREEN));
                    return;
                }

                sender.sendMessage(Component.text("--- Status repozytorium ---").color(NamedTextColor.GOLD));

                for (String file : changed) {
                    sender.sendMessage(Component.text("[M] ").color(NamedTextColor.YELLOW).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : modified) {
                    sender.sendMessage(Component.text("[M] ").color(NamedTextColor.YELLOW).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : added) {
                    sender.sendMessage(Component.text("[A] ").color(NamedTextColor.GREEN).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : removed) {
                    sender.sendMessage(Component.text("[D] ").color(NamedTextColor.RED).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : missing) {
                    sender.sendMessage(Component.text("[D] ").color(NamedTextColor.RED).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : conflicting) {
                    sender.sendMessage(Component.text("[C] ").color(NamedTextColor.DARK_RED).append(Component.text(file).color(NamedTextColor.WHITE)));
                }
                for (String file : untracked) {
                    sender.sendMessage(Component.text("[?] ").color(NamedTextColor.GRAY).append(Component.text(file).color(NamedTextColor.GRAY)));
                }
            } catch (GitAPIException | IOException e) {
                sender.sendMessage(Component.text("Wystąpił błąd! Po więcej informacji zobacz konsolę").color(NamedTextColor.RED));
                throw new RuntimeException(e);
            }
        });
    }

    @Subcommand("showahead")
    @Description("Pokazuje commity, które są w lokalnym repozytorium, ale nie ma ich w zdalnym (git log origin/<branch>..HEAD)")
    public void showAhead(CommandSender sender) {
        sender.sendMessage(Component.text("Pobieranie commitów...").color(NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskAsynchronously(ItemsAdderContentsSync.instance(), () -> {
            try (Git git = ItemsAdderContentsSync.instance().getPack().getOrInitGit()) {
                Repository repo = git.getRepository();
                String branch = repo.getBranch();

                ObjectId localHead = repo.resolve("HEAD");
                ObjectId remoteHead = repo.resolve("origin/" + branch);

                if (remoteHead == null) {
                    sender.sendMessage(Component.text("Nie znaleziono zdalnej gałęzi origin/" + branch + ".").color(NamedTextColor.RED));
                    return;
                }

                Iterable<RevCommit> commits = git.log().addRange(remoteHead, localHead).call();

                sender.sendMessage(Component.text("--- Commity przed zdalnym (origin/" + branch + ") ---").color(NamedTextColor.GOLD));

                boolean hasCommits = false;
                for (RevCommit commit : commits) {
                    hasCommits = true;
                    String hash = commit.getName().substring(0, 7);
                    String author = commit.getAuthorIdent().getName();
                    sender.sendMessage(
                        Component.text(hash + " ").color(NamedTextColor.YELLOW)
                            .append(Component.text("(" + author + ") ").color(NamedTextColor.GRAY))
                            .append(Component.text(commit.getShortMessage()).color(NamedTextColor.WHITE))
                    );
                }

                if (!hasCommits) {
                    sender.sendMessage(Component.text("Brak commitów przed zdalnym repozytorium.").color(NamedTextColor.GREEN));
                }
            } catch (GitAPIException | IOException e) {
                sender.sendMessage(Component.text("Wystąpił błąd! Po więcej informacji zobacz konsolę").color(NamedTextColor.RED));
                throw new RuntimeException(e);
            }
        });
    }


    @Subcommand("commitandpush")
    @Description("Tworzy commita z aktualnymi zmianami i pushuje go do repozytorium (git commit -m <message> & git push)")
    @Syntax("<message>")
    public void commitAndPush(CommandSender sender, String message) {
        sender.sendMessage(Component.text("Tworzenie commita...").color(NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskAsynchronously(ItemsAdderContentsSync.instance(), () -> {
            try (Git git = ItemsAdderContentsSync.instance().getPack().getOrInitGit()) {
                // git add .
                git.add()
                    .addFilepattern(".")
                    .call();

                if (!git.status().call().isClean()) {
                    // git commit -m <message>
                    git.commit()
                        .setMessage(message)
                        .call();
                    sender.sendMessage(Component.text("Pomyślnie utworzono commita! Pushowanie...").color(NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Brak zmian do zatwierdzenia, pushowanie...").color(NamedTextColor.YELLOW));
                }

                // git push
                git.push()
                    .setCredentialsProvider(ItemsAdderContentsSync.instance().getPack().credentials)
                    .call();

                sender.sendMessage(Component.text("Pomyślnie wypushowano commita do repozytorium!").color(NamedTextColor.GREEN));
            } catch (GitAPIException | IOException e) {
                sender.sendMessage(Component.text("Wystąpił błąd! Po więcej informacji zobacz konsolę").color(NamedTextColor.RED));
                throw new RuntimeException(e);
            }
        });
    }
}
