package com.epicplayera10.itemsaddercontentssync;

import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class IASyncManager {
    private static final Gson GSON = new GsonBuilder().create();

    private static boolean isSyncing = false;

    public static CompletableFuture<Void> syncPack(boolean force) {
        if (isSyncing) {
            throw new IllegalStateException("Tried to call syncPack() while syncing!");
        }

        if (ItemsAdderContentsSync.instance().isItemsAdderReloading()) {
            throw new IllegalStateException("Tried to call syncPack() while ItemsAdder is reloading!");
        }

        isSyncing = true;
        return CompletableFuture.runAsync(() -> {
            PluginConfiguration pluginConfiguration = ItemsAdderContentsSync.instance().getPluginConfiguration();

            try {
                File packDir = ItemsAdderContentsSync.instance().getPackDir();

                Git git;
                ItemsAdderContentsSync.instance().getLogger().info("Syncing...");
                if (!packDir.exists() || !packDir.isDirectory() || !new File(packDir, ".git").exists()) {
                    FileUtils.deleteRecursion(packDir);

                    git = Git.cloneRepository()
                        .setURI(pluginConfiguration.packRepoUrl)
                        .setBranch(pluginConfiguration.branch)
                        .setDirectory(ItemsAdderContentsSync.instance().getPackDir())
                        .call();
                } else {
                    git = Git.open(packDir);
                }

                // Checkout branch
                try {
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(pluginConfiguration.branch)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint("origin/" + pluginConfiguration.branch)
                        .call();
                } catch (RefAlreadyExistsException ignored) {
                }

                // Pull changes
                git.pull().call();

                String latestCommitHash = git.log()
                    .setMaxCount(1)
                    .call()
                    .iterator()
                    .next()
                    .getName();

                if (!force && pluginConfiguration.lastCommitHash.equals(latestCommitHash)) {
                    ItemsAdderContentsSync.instance().getLogger().info("IA Pack is up-to-date!");
                    return;
                }

                ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Found newer version of the pack ("+latestCommitHash+")! Updating...");

                // Start updating
                JsonObject root = JsonParser.parseReader(new FileReader(new File(ItemsAdderContentsSync.instance().getPackDir(), "config.json"))).getAsJsonObject();

                // Handle config
                handleConfig(root);

                // Copy new files
                ItemsAdderContentsSync.instance().getLogger().info("Copying new files");
                FileUtils.copyFileStructure(new File(ItemsAdderContentsSync.instance().getPackDir(), "pack"), ItemsAdderContentsSync.instance().getDataFolder().getParentFile());

                // Reload ItemsAdder
                ItemsAdderContentsSync.instance().getLogger().info("Reloading ItemsAdder");
                Bukkit.getScheduler().callSyncMethod(ItemsAdderContentsSync.instance(), () -> {
                    ItemsAdderContentsSync.instance().setItemsAdderReloading(true);
                    if (ItemsAdderContentsSync.instance().canItemsAdderCreateResourcepack()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iareload");
                    }

                    return null;
                }).get();

                // Store latest commit hash
                pluginConfiguration.lastCommitHash = latestCommitHash;
                pluginConfiguration.save();

                git.close();

                ItemsAdderContentsSync.instance().getLogger().info("Done");
            } catch (GitAPIException | IOException | InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((unused, ex) -> isSyncing = false);
    }

    private static void handleConfig(JsonObject root) throws IOException {
        // Delete files
        if (root.has("deleteFilesBeforeInstall")) {
            JsonArray filesToDelete = root.getAsJsonArray("deleteFilesBeforeInstall");
            for (JsonElement element : filesToDelete) {
                String path = element.getAsString();
                ItemsAdderContentsSync.instance().getLogger().info("Deleting " + path);

                File fileToDelete = new File(ItemsAdderContentsSync.instance().getDataFolder().getParentFile(), path);
                FileUtils.deleteRecursion(fileToDelete);
            }
        }

        // Set additional config in IA config
        if (root.has("iaAdditionalConfig")) {
            JsonObject additionalConfig = root.getAsJsonObject("iaAdditionalConfig");
            FileConfiguration config = ItemsAdderContentsSync.instance().getItemsAdderInstance().getConfig();

            setIAConfig(config, null, additionalConfig);
            ItemsAdderContentsSync.instance().getItemsAdderInstance().saveConfig();
        }
    }

    private static void setIAConfig(FileConfiguration config, String key, JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject jsonObject = value.getAsJsonObject();
            for (var entry : jsonObject.entrySet()) {
                String newKey = key;
                if (newKey == null) {
                    newKey = entry.getKey();
                } else {
                    newKey += "."+entry.getKey();
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
