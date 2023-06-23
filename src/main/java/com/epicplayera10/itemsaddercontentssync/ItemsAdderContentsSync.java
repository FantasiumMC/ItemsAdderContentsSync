package com.epicplayera10.itemsaddercontentssync;

import com.epicplayera10.itemsaddercontentssync.commands.MainCommand;
import com.epicplayera10.itemsaddercontentssync.configuration.ConfigurationFactory;
import com.epicplayera10.itemsaddercontentssync.configuration.PluginConfiguration;
import com.epicplayera10.itemsaddercontentssync.utils.FileUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

public final class ItemsAdderContentsSync extends JavaPlugin {

    private final File pluginConfigurationFile = new File(this.getDataFolder(), "config.yml");

    private final File tempPackDir = new File(this.getDataFolder(), "temprepo");

    private static ItemsAdderContentsSync instance;

    private PluginConfiguration pluginConfiguration;

    @Override
    public void onEnable() {
        instance = this;

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(this.pluginConfigurationFile);

        getCommand("itemsaddercontentssync").setExecutor(new MainCommand());
        getCommand("iacs").setExecutor(new MainCommand());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

    }

    public void syncPack() {
        try {
            FileUtils.deleteRecursion(tempPackDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            this.getLogger().info("Cloning...");
            CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(pluginConfiguration.packRepoUrl)
                .setDirectory(tempPackDir);

            // Authorization
            if (pluginConfiguration.accessToken != null) {
                UsernamePasswordCredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("oauth2", pluginConfiguration.accessToken);
                cloneCommand.setCredentialsProvider(credentialsProvider);
            }

            Git git = cloneCommand.call();

            this.getLogger().info("Reading config...");
            JsonObject root = JsonParser.parseReader(new FileReader(new File(tempPackDir, "config.json"))).getAsJsonObject();

            // Delete files
            JsonArray filesToDelete = root.getAsJsonArray("deleteFilesBeforeInstall");
            for (JsonElement element : filesToDelete) {
                String path = element.getAsString();
                this.getLogger().info("Deleting "+path);

                File fileToDelete = new File(this.getDataFolder().getParentFile(), path);
                FileUtils.deleteRecursion(fileToDelete);
            }

            // Copy new files
            this.getLogger().info("Copying new files");
            FileUtils.copyFileStructure(new File(tempPackDir, "pack"), this.getDataFolder().getParentFile());

            // Reload ItemsAdderno
            this.getLogger().info("Reloading ItemsAdder");
            if (ItemsAdder.getPackUrl(false) == null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iareload");
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
            }

            // TODO
            //pluginConfiguration.lastCommitHash = git.

            this.getLogger().info("Done");
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemsAdderContentsSync instance() {
        return instance;
    }

    public PluginConfiguration getPluginConfiguration() {
        return pluginConfiguration;
    }

    public File getTempPackDir() {
        return tempPackDir;
    }
}
