package com.epicplayera10.itemsaddercontentssync.syncmanager;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.epicplayera10.itemsaddercontentssync.pluginsreload.ReloadPluginProperties;
import com.epicplayera10.itemsaddercontentssync.pluginsreload.ReloadPlugins;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import com.epicplayera10.itemsaddercontentssync.utils.WindowsSymlinkUtils;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SyncPackOperation {
    private static final Gson GSON = new GsonBuilder().create();

    private static final Multimap<String, PluginDataPath> MODIFIABLE_PLUGINS_PATHS = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);

    static {
        // Init MODIFIABLE_PLUGINS_PATHS

        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/contents", true));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/custom_fires_ids_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/font_images_unicode_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/items_ids_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/real_blocks_ids_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/real_blocks_note_ids_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/real_transparent_blocks_ids_cache.yml", false));
        MODIFIABLE_PLUGINS_PATHS.put("ItemsAdder", new PluginDataPath("ItemsAdder/storage/real_wire_ids_cache.yml", false));

        MODIFIABLE_PLUGINS_PATHS.put("ModelEngine", new PluginDataPath("ModelEngine/blueprints", true));
        // New feature in ModelEngine R4.0.0
        MODIFIABLE_PLUGINS_PATHS.put("ModelEngine", new PluginDataPath("ModelEngine/.data/cache.json", false));

        MODIFIABLE_PLUGINS_PATHS.put("CosmeticsCore", new PluginDataPath("CosmeticsCore/cosmetics", true));

        MODIFIABLE_PLUGINS_PATHS.put("MythicMobs", new PluginDataPath("MythicMobs/Packs", true));

        MODIFIABLE_PLUGINS_PATHS.put("MCPets", new PluginDataPath("MCPets/Pets", true));
    }

    public record PluginDataPath(String path, boolean isDirectory) {
    }



    private final Pack pack;
    private final boolean force;
    private final boolean isServerStartup;
    @Nullable
    private final CommandSender sender;

    public SyncPackOperation(Pack pack, boolean force, boolean isServerStartup, @Nullable CommandSender sender) {
        this.pack = pack;
        this.force = force;
        this.isServerStartup = isServerStartup;
        this.sender = sender;
    }

    public CompletableFuture<Boolean> syncPack() {
        return CompletableFuture.supplyAsync(() -> {
            boolean isFreshClone = this.pack.shouldCloneRepo();

            try (Git git = this.pack.getOrInitGit()) {
                if (!isFreshClone) {
                    // Get current commit hash on local repo
                    String lastCommitHash = git.log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next()
                        .getName();

                    // Pull changes
                    git.pull()
                        .setCredentialsProvider(this.pack.credentials)
                        .call();

                    // Get the latest commit hash from remote repo
                    String latestCommitHash = git.log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next()
                        .getName();

                    if (!force && latestCommitHash.equals(lastCommitHash)) {
                        // Using already the latest version

                        if (ItemsAdderContentsSync.instance().getPluginConfiguration().putContentMode == PutContentMode.SYMLINKS) {
                            // Make sure that symlinks exists
                            createSymlinks();
                        }
                        return false;
                    }

                    ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Found newer version of the pack (" + latestCommitHash + ")! Updating...");
                } else {
                    ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Installing fresh pack on the server...");
                }

                if (sender != null) {
                    sender.sendMessage(Component.text("Pomyślnie zsynchronizowano! Przeładowywanie pluginów...").color(NamedTextColor.GREEN));
                }

                // Post sync
                postSync().join();

                if (sender != null) {
                    sender.sendMessage(Component.text("Przeładowano pluginy!").color(NamedTextColor.GREEN));
                }

                return true;
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Updates pack
     */
    @Blocking
    private CompletableFuture<Void> postSync() throws IOException {
        // Start updating
        JsonObject root = JsonParser.parseReader(new FileReader(new File(this.pack.repoDir, "config.json"))).getAsJsonObject();

        // Handle config
        handleConfig(root);

        if (ItemsAdderContentsSync.instance().getPluginConfiguration().putContentMode == PutContentMode.SYMLINKS) {
            createSymlinks();
        } else if (ItemsAdderContentsSync.instance().getPluginConfiguration().putContentMode == PutContentMode.REPLACE_FILES) {
            replaceFilesInDestinations();
        }

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        // We don't need to reload plugins before they started
        if (!this.isServerStartup) {
            // Reload plugins
            future = ReloadPlugins.reloadPlugins(this);
        }

        ItemsAdderContentsSync.instance().getLogger().info("Done");

        return future;
    }

    /**
     * Creates symlinks in proper places
     */
    private void createSymlinks() throws IOException {
        File pluginsDir = ItemsAdderContentsSync.instance().getDataFolder().getParentFile();

        for (var entry : MODIFIABLE_PLUGINS_PATHS.asMap().entrySet()) {
            String pluginName = entry.getKey();
            Collection<PluginDataPath> pluginDataPathList = entry.getValue();

            if (isPluginInUse(pluginName)) {
                for (PluginDataPath pluginDataPath : pluginDataPathList) {
                    File targetPath = new File(this.pack.packDir, pluginDataPath.path());
                    if (!targetPath.exists()) continue;

                    File linkPath = new File(pluginsDir, pluginDataPath.path());

                    if (linkPath.exists()) {
                        if (FileUtils.isSymlink(linkPath)) {
                            continue;
                        } else {
                            // Delete non-symlinks
                            FileUtils.deleteRecursion(linkPath);
                        }
                    }

                    linkPath.getParentFile().mkdirs();

                    // Create symlink
                    if (pluginDataPath.isDirectory() && System.getProperty("os.name").startsWith("Windows")) {
                        // Windows has its own way of directory symlinks
                        WindowsSymlinkUtils.createJunctionSymlink(linkPath, targetPath);
                    } else {
                        Files.createSymbolicLink(linkPath.getAbsoluteFile().toPath(), targetPath.getAbsoluteFile().toPath());
                    }
                }
            }
        }
    }

    /**
     * Replaces files (copying) in the according destinations
     */
    private void replaceFilesInDestinations() throws IOException {
        // Delete files
        ItemsAdderContentsSync.instance().getLogger().info("Deleting files...");
        for (var entry : MODIFIABLE_PLUGINS_PATHS.asMap().entrySet()) {
            String pluginName = entry.getKey();
            Collection<PluginDataPath> pluginDataPathList = entry.getValue();

            // Check if plugin config exists
            if (isPluginInUse(pluginName)) {
                for (PluginDataPath pluginDataPath : pluginDataPathList) {
                    File file = new File(ItemsAdderContentsSync.instance().getDataFolder().getParentFile(), pluginDataPath.path());

                    if (!file.exists()) continue;

                    ItemsAdderContentsSync.instance().getLogger().info("Deleting file: " + pluginDataPath.path());

                    FileUtils.deleteRecursion(file);
                }
            }
        }

        // Copy new files
        ItemsAdderContentsSync.instance().getLogger().info("Copying new files");
        FileUtils.copyFileStructure(this.pack.packDir, ItemsAdderContentsSync.instance().getDataFolder().getParentFile());
    }

    public boolean isPluginInUse(String pluginName) {
        return new File(this.pack.packDir, pluginName).exists() // Is the plugin in the pack?
            && Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    /**
     * Reads config and applies it
     *
     * @param root Config JSON
     */
    private void handleConfig(JsonObject root) {
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
    private void setIAConfig(FileConfiguration config, String key, JsonElement value) {
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
}
