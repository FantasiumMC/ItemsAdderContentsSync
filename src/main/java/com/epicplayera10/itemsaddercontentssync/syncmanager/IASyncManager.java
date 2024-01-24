package com.epicplayera10.itemsaddercontentssync.syncmanager;

import com.epicplayera10.itemsaddercontentssync.ItemsAdderContentsSync;
import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import com.epicplayera10.itemsaddercontentssync.utils.WindowsSymlinkUtils;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.StoredConfig;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class IASyncManager {
    private static final Gson GSON = new GsonBuilder().create();

    private static final Multimap<String, PluginDataPath> MODIFIABLE_PLUGINS_PATHS = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);

    // Some kind of lock
    private static boolean isSyncing = false;

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

    public static CompletableFuture<Boolean> syncPack(boolean force) {
        return syncPack(force, false);
    }

    /**
     * Sync pack
     *
     * @param force Should sync pack despite that pack didn't change
     * @param isServerStartup Is server is startup state
     *
     * @return A future which returns a boolean. "true" means that there was a new version of the pack
     */
    public static CompletableFuture<Boolean> syncPack(boolean force, boolean isServerStartup) {
        if (isSyncing) {
            throw new IllegalStateException("Tried to call syncPack() while syncing!");
        }

        if (!isServerStartup && !ItemsAdderContentsSync.instance().getThirdPartyPluginStates().isAllReloaded()) {
            throw new IllegalStateException("Tried to call syncPack() while one of required plugins is reloading!");
        }

        isSyncing = true;
        return CompletableFuture.supplyAsync(() -> {
            File repoDir = ItemsAdderContentsSync.instance().getRepoDir();

            boolean isFreshClone = shouldCloneRepo(repoDir);

            try (Git git = initRepo(repoDir)) {
                if (!isFreshClone) {
                    // Get current commit hash on local repo
                    String lastCommitHash = git.log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next()
                        .getName();

                    // Pull changes
                    git.pull().call();

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
                            createSymlinks(new File(repoDir, "pack"));
                        }
                        return false;
                    }

                    ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Found newer version of the pack (" + latestCommitHash + ")! Updating...");
                } else {
                    ItemsAdderContentsSync.instance().getLogger().log(Level.INFO, "Installing fresh pack on the server...");
                }

                // Update pack
                updatePack(repoDir, isServerStartup);

                return true;
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((wasNewerVersion, ex) -> {
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
     */
    private static void updatePack(File repoDir, boolean isServerStartup) throws IOException {
        // Start updating
        JsonObject root = JsonParser.parseReader(new FileReader(new File(repoDir, "config.json"))).getAsJsonObject();

        // Handle config
        handleConfig(root);

        File packDir = new File(repoDir, "pack");

        if (ItemsAdderContentsSync.instance().getPluginConfiguration().putContentMode == PutContentMode.SYMLINKS) {
            createSymlinks(packDir);
        } else if (ItemsAdderContentsSync.instance().getPluginConfiguration().putContentMode == PutContentMode.REPLACE_FILES) {
            replaceFilesInDestinations(packDir);
        }

        // We don't need to reload plugins before they started
        if (!isServerStartup) {
            // Reload plugins
            reloadPlugins(packDir);
        }

        ItemsAdderContentsSync.instance().getLogger().info("Done");
    }

    /**
     * Creates symlinks in proper places
     */
    private static void createSymlinks(File packDir) throws IOException {
        File pluginsDir = ItemsAdderContentsSync.instance().getDataFolder().getParentFile();

        for (var entry : MODIFIABLE_PLUGINS_PATHS.asMap().entrySet()) {
            String pluginName = entry.getKey();
            Collection<PluginDataPath> pluginDataPathList = entry.getValue();

            if (new File(packDir, pluginName).isDirectory()) {
                for (PluginDataPath pluginDataPath : pluginDataPathList) {
                    File targetPath = new File(packDir, pluginDataPath.path);
                    if (!targetPath.exists()) continue;

                    File linkPath = new File(pluginsDir, pluginDataPath.path);

                    if (linkPath.exists()) {
                        if (isSymlink(linkPath)) {
                            continue;
                        } else {
                            // Delete non-symlinks
                            FileUtils.deleteRecursion(linkPath);
                        }
                    }

                    linkPath.getParentFile().mkdirs();

                    // Create symlink
                    if (pluginDataPath.isDirectory && System.getProperty("os.name").startsWith("Windows")) {
                        // Windows has its own way of directory symlinks
                        WindowsSymlinkUtils.createJunctionSymlink(linkPath, targetPath);
                    } else {
                        Files.createSymbolicLink(linkPath.getAbsoluteFile().toPath(), targetPath.getAbsoluteFile().toPath());
                    }
                }
            }
        }
    }

    private static boolean isSymlink(File file) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        if (attrs.isSymbolicLink()) {
            return true;
        } else {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
            return isWindows && attrs.isDirectory() && attrs.isOther();
        }
    }

    /**
     * Replaces files (copying) in the according destinations
     */
    private static void replaceFilesInDestinations(File packDir) throws IOException {
        // Delete files
        ItemsAdderContentsSync.instance().getLogger().info("Deleting files...");
        for (var entry : MODIFIABLE_PLUGINS_PATHS.asMap().entrySet()) {
            String pluginName = entry.getKey();
            Collection<PluginDataPath> pluginDataPathList = entry.getValue();

            // Check if plugin config exists
            File pluginFolder = new File(packDir, pluginName);
            if (pluginFolder.isDirectory()) {
                for (PluginDataPath pluginDataPath : pluginDataPathList) {
                    File file = new File(ItemsAdderContentsSync.instance().getDataFolder().getParentFile(), pluginDataPath.path);

                    if (!file.exists()) continue;

                    ItemsAdderContentsSync.instance().getLogger().info("Deleting file: " + pluginDataPath.path);

                    FileUtils.deleteRecursion(file);
                }
            }
        }

        // Copy new files
        ItemsAdderContentsSync.instance().getLogger().info("Copying new files");
        FileUtils.copyFileStructure(packDir, ItemsAdderContentsSync.instance().getDataFolder().getParentFile());
    }

    /**
     * Reloads plugins
     *
     * @param packDir Pack directory
     */
    private static void reloadPlugins(File packDir) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        // Beautiful futures chain :D

        // Pre reloads
        future = preReloadPlugins(packDir, future);

        // Reload ItemsAdder
        if (shouldBePluginReloaded(packDir, "ItemsAdder")) {
            ItemsAdderContentsSync.instance().getThirdPartyPluginStates().itemsAdderReloadingFuture = new CompletableFuture<>();

            future = future.thenCompose((unused) -> ReloadPlugins.reloadItemsAdder());
        }

        // Post reloads
        future = postReloadPlugins(packDir, future);
    }

    private static CompletableFuture<Void> preReloadPlugins(File packDir, CompletableFuture<Void> future) {
        // Reload ModelEngine
        if (shouldBePluginReloaded(packDir, "ModelEngine")) {
            ItemsAdderContentsSync.instance().getThirdPartyPluginStates().modelEngineReloadingFuture = new CompletableFuture<>();

            future = future.thenCompose((unused) -> ReloadPlugins.reloadModelEngine());
        }

        return future;
    }

    private static CompletableFuture<Void> postReloadPlugins(File packDir, CompletableFuture<Void> future) {
        // Reload CosmeticsCore
        if (shouldBePluginReloaded(packDir, "CosmeticsCore")) {
            future = future.thenRun(ReloadPlugins::reloadCosmeticsCore);
        }
        // Reload MythicMobs
        if (shouldBePluginReloaded(packDir, "MythicMobs")) {
            future = future.thenRun(ReloadPlugins::reloadMythicMobs);
        }
        // Reload MCPets
        if (shouldBePluginReloaded(packDir, "MCPets")) {
            future = future.thenRun(ReloadPlugins::reloadMCPets);
        }

        return future;
    }

    private static boolean shouldBePluginReloaded(File packDir, String pluginName) {
        return new File(packDir, pluginName).exists() // Is the plugin in the pack?
                && Bukkit.getPluginManager().isPluginEnabled(pluginName);
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

        if (shouldCloneRepo(repoDir)) {
            // Clone repo
            ItemsAdderContentsSync.instance().getLogger().info("Cloning repo...");
            FileUtils.deleteRecursion(repoDir);

            git = Git.cloneRepository()
                    .setURI(pluginConfiguration.packRepoUrl)
                    .setBranch(pluginConfiguration.branch)
                    .setDirectory(repoDir)
                    .call();

            StoredConfig gitConfig = git.getRepository().getConfig();
            gitConfig.setString("user", null, "name", "MC Server");
            gitConfig.setString("user", null, "email", "minecraft@server.null");
            gitConfig.setBoolean("core", null, "fileMode", false);
            gitConfig.save();

            ItemsAdderContentsSync.instance().getLogger().info("Cloned repo!");
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

    private static boolean shouldCloneRepo(File repoDir) {
        return !repoDir.exists() || !repoDir.isDirectory() || !new File(repoDir, ".git").exists();
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

    private record PluginDataPath(String path, boolean isDirectory) {
    }
}
