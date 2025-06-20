package com.epicplayera10.itemsaddercontentssync.syncmanager;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import org.bukkit.command.CommandSender;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Pack {
    // Some kind of lock
    private boolean isSyncing = false;

    public final File repoDir;
    public final File packDir;
    public final String packRepoUrl;
    public final String branch;
    @Nullable
    public final UsernamePasswordCredentialsProvider credentials;

    public Pack(File repoDir, String packRepoUrl, String branch, @Nullable UsernamePasswordCredentialsProvider credentials) {
        this.repoDir = repoDir;
        this.packDir = new File(repoDir, "pack");

        this.packRepoUrl = packRepoUrl;
        this.branch = branch;
        this.credentials = credentials;
    }

    public CompletableFuture<Boolean> syncPack(boolean force, @Nullable CommandSender sender) {
        return syncPack(force, false, sender);
    }

    /**
     * Sync pack
     *
     * @param force Should sync pack despite that pack didn't change
     * @param isServerStartup Is server is startup state
     *
     * @return A future which returns a boolean. "true" means that there was a new version of the pack
     */
    public CompletableFuture<Boolean> syncPack(boolean force, boolean isServerStartup, @Nullable CommandSender sender) {
        if (this.isSyncing) {
            throw new IllegalStateException("Tried to call syncPack() while syncing!");
        }

        // Check if somebody is reloading manually required plugins
        if (!isServerStartup && !ItemsAdderContentsSync.instance().getThirdPartyPluginStates().isAllReloaded()) {
            throw new IllegalStateException("Tried to call syncPack() while one of required plugins is reloading!");
        }

        this.isSyncing = true;
        SyncPackOperation syncPackOperation = new SyncPackOperation(this, force, isServerStartup, sender);
        return syncPackOperation.syncPack()
            .whenComplete((wasNewerVersion, ex) -> {
                isSyncing = false;
                if (ex != null) {
                    ItemsAdderContentsSync.instance().getLogger().log(Level.SEVERE, "An error occurred while syncing pack", ex);
                }
            });
    }

    /**
     * Clones repo if does not exists or just open existing repo.
     *
     * @return {@link Git} reference
     */
    public Git getOrInitGit() throws IOException, GitAPIException {
        Git git;

        if (shouldCloneRepo()) {
            // Clone repo
            ItemsAdderContentsSync.instance().getLogger().info("Cloning repo...");
            FileUtils.deleteRecursion(this.repoDir);

            git = Git.cloneRepository()
                .setURI(this.packRepoUrl)
                .setBranch(this.branch)
                .setDirectory(this.repoDir)
                .setCredentialsProvider(this.credentials)
                .call();

            StoredConfig gitConfig = git.getRepository().getConfig();

            gitConfig.setString("user", null, "name", "MC Server");
            gitConfig.setString("user", null, "email", "minecraft@server.null");
            gitConfig.setBoolean("core", null, "fileMode", false);
            gitConfig.setString("credential", null, "helper", "store");

            gitConfig.save();

            ItemsAdderContentsSync.instance().getLogger().info("Cloned repo!");
        } else {
            // Open repo
            git = Git.open(this.repoDir);
        }

        try {
            // Checkout branch
            git.checkout()
                .setCreateBranch(true)
                .setName(this.branch)
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .setStartPoint("origin/" + this.branch)
                .call();
        } catch (RefAlreadyExistsException ignored) {
        }

        return git;
    }

    public boolean shouldCloneRepo() {
        return !this.repoDir.exists() || !this.repoDir.isDirectory() || !new File(this.repoDir, ".git").exists();
    }

    public boolean isSyncing() {
        return isSyncing;
    }
}
