package com.epicplayera10.itemsaddercontentssync;

import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class IASyncManager {
    private static final Gson GSON = new GsonBuilder().create();

    private static final Multimap<String, String> PLUGINS_TO_DELETE_FILES = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);

    // Some kind of lock
    private static boolean isSyncing = false;

    static {
        // Init PLUGINS_TO_DELETE_FILES
        PLUGINS_TO_DELETE_FILES.put("ItemsAdder", "ItemsAdder/contents");
        PLUGINS_TO_DELETE_FILES.put("ModelEngine", "ModelEngine/blueprints");
        PLUGINS_TO_DELETE_FILES.put("CosmeticsCore", "CosmeticsCore/cosmetics");
    }

    public static CompletableFuture<Void> syncPack(boolean force) {
        if (isSyncing) {
            throw new IllegalStateException("Tried to call syncPack() while syncing!");
        }

        if (!ItemsAdderContentsSync.instance().getThirdPartyPluginStates().isAllReloaded()) {
            throw new IllegalStateException("Tried to call syncPack() while one of required plugins is reloading!");
        }

        isSyncing = true;
        return CompletableFuture.runAsync(() -> {
            PluginConfiguration pluginConfiguration = ItemsAdderContentsSync.instance().getPluginConfiguration();

            File repoDir = ItemsAdderContentsSync.instance().getRepoDir();

            try (Git git = initRepo(repoDir)) {

                // Pull changes
                git.pull().call();

                // Check if changes where made in this repo
                String latestCommitHash = git.log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next()
                        .getName();

                if (!force && pluginConfiguration.lastCommitHash.equals(latestCommitHash)) {
                    return;
                }

                // Update pack
                updatePack(repoDir, latestCommitHash);

            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((unused, ex) -> {
            isSyncing = false;
            if (ex != null) {
                ItemsAdderContentsSync.instance().getLogger().log(Level.SEVERE, "An error occurred while syncing pack", ex);
            }
        });
    }

    /**
     * Updates pack
     *
     * @param repoDir Repository Directory
     * @param latestCommitHash Latest commit hash in this repository
     */
    private static void updatePack(File repoDir, String latestCommitHash) throws IOException {
        ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Found newer version of the pack (" + latestCommitHash + ")! Updating...");

        PluginConfiguration pluginConfiguration = ItemsAdderContentsSync.instance().getPluginConfiguration();

        // Start updating
        JsonObject root = JsonParser.parseReader(new FileReader(new File(repoDir, "config.json"))).getAsJsonObject();

        File packDir = new File(repoDir, "pack");

        // Delete files
        ItemsAdderContentsSync.instance().getLogger().info("Deleting files...");
        for (var entry : PLUGINS_TO_DELETE_FILES.asMap().entrySet()) {
            String pluginName = entry.getKey();
            Collection<String> pathsToDelete = entry.getValue();

            // Check if plugin config exists
            File pluginFolder = new File(packDir, pluginName);
            if (pluginFolder.isDirectory()) {
                for (String pathToDelete : pathsToDelete) {
                    File file = new File(ItemsAdderContentsSync.instance().getDataFolder().getParentFile(), pathToDelete);

                    if (!file.exists()) continue;

                    ItemsAdderContentsSync.instance().getLogger().info("Deleting file: " + pathToDelete);

                    FileUtils.deleteRecursion(file);
                }
            }
        }

        // Handle config
        handleConfig(root);

        // Copy new files
        ItemsAdderContentsSync.instance().getLogger().info("Copying new files");
        FileUtils.copyFileStructure(packDir, ItemsAdderContentsSync.instance().getDataFolder().getParentFile());

        // Pre reloads
        // Reload ModelEngine
        if (shouldPluginBeReloaded(packDir, "ModelEngine")) {
            reloadModelEngine();
        }

        // Reload ItemsAdder
        if (new File(packDir, "ItemsAdder").exists()) {
            ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture.thenCompose(unused -> {
                reloadItemsAdder();

                // Post reloads
                return ItemsAdderContentsSync.instance().getThirdPartyPluginStates().itemsAdderReloadingFuture.thenAccept(unused1 -> {
                    // Reload CosmeticsCore
                    if (shouldPluginBeReloaded(packDir, "CosmeticsCore")) {
                        reloadCosmeticsCore();
                    }
                });
            });
        }

        // Store latest commit hash
        pluginConfiguration.lastCommitHash = latestCommitHash;
        pluginConfiguration.save();

        ItemsAdderContentsSync.instance().getLogger().info("Done");
    }

    private static boolean shouldPluginBeReloaded(File packDir, String pluginName) {
        return new File(packDir, pluginName).exists() && Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    /**
     * Reloads CosmeticsCore
     */
    private static void reloadCosmeticsCore() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading CosmeticsCore");
        runCommandEnsureSync(Bukkit.getConsoleSender(), "cosmeticsconfig cosmetics reload");
    }

    /**
     * Reloads ModelEngine
     */
    private static void reloadModelEngine() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading ModelEngine");

        ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture = new CompletableFuture<>();
        runCommandEnsureSync(Bukkit.getConsoleSender(), "meg reload");
    }

    /**
     * Reloads ItemsAdder
     */
    private static void reloadItemsAdder() {
        ItemsAdderContentsSync.instance().getLogger().info("Reloading ItemsAdder");

        ItemsAdderContentsSync.instance().getThirdPartyPluginStates().itemsAdderReloadingFuture = new CompletableFuture<>();
        if (ItemsAdderContentsSync.instance().canItemsAdderCreateResourcepack()) {
            runCommandEnsureSync(Bukkit.getConsoleSender(), "iazip");
        } else {
            runCommandEnsureSync(Bukkit.getConsoleSender(), "iareload");
        }
    }

    private static void runCommandEnsureSync(CommandSender sender, String command) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(sender, command);
        } else {
            Bukkit.getScheduler().runTask(ItemsAdderContentsSync.instance(), () -> {
                Bukkit.dispatchCommand(sender, command);
            });
        }
    }

    /**
     * Clones repo if does not exists or just open existing repo.
     *
     * @param repoDir Repo Directory
     * @return {@link Git} reference
     */
    private static Git initRepo(File repoDir) throws IOException, GitAPIException {
        PluginConfiguration pluginConfiguration = ItemsAdderContentsSync.instance().getPluginConfiguration();

        Git git;

        if (!repoDir.exists() || !repoDir.isDirectory() || !new File(repoDir, ".git").exists()) {
            // Clone repo
            FileUtils.deleteRecursion(repoDir);


            git = Git.cloneRepository()
                    .setURI(pluginConfiguration.packRepoUrl)
                    .setBranch(pluginConfiguration.branch)
                    .setDirectory(repoDir)
                    .call();

        } else {
            // Open repo
            git = Git.open(repoDir);
        }

        try {
            // Checkout branch
            git.checkout()
                    .setCreateBranch(true)
                    .setName(pluginConfiguration.branch)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint("origin/" + pluginConfiguration.branch)
                    .call();
        } catch (RefAlreadyExistsException ignored) {
        }

        return git;
    }

    /**
     * Reads config and applies it
     *
     * @param root Config JSON
     */
    private static void handleConfig(JsonObject root) {
        // Set additional config in IA config
        if (root.has("iaAdditionalConfig")) {
            JsonObject additionalConfig = root.getAsJsonObject("iaAdditionalConfig");
            FileConfiguration config = ItemsAdderContentsSync.instance().getItemsAdderInstance().getConfig();

            setIAConfig(config, null, additionalConfig);
            ItemsAdderContentsSync.instance().getItemsAdderInstance().saveConfig();
        }
    }

    /**
     * A utility method to set key-value pairs to ItemsAdder config from JSON config
     *
     * @param config ItemsAdder config
     * @param key Key to set
     * @param value Value to set
     */
    private static void setIAConfig(FileConfiguration config, String key, JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject jsonObject = value.getAsJsonObject();
            for (var entry : jsonObject.entrySet()) {
                String newKey = key;
                if (newKey == null) {
                    newKey = entry.getKey();
                } else {
                    newKey += "." + entry.getKey();
                }

                setIAConfig(config, newKey, entry.getValue());
            }
        } else {
            Object data = GSON.fromJson(value, Object.class);

            config.set(key, data);
        }
    }

    public static boolean isSyncing() {
        return isSyncing;
    }
}
