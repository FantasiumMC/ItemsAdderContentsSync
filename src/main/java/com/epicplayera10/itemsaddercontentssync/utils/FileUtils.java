package com.epicplayera10.itemsaddercontentssync.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class FileUtils {
    public static void copyFileStructure(File source, File target) throws IOException {
        copyFileStructure(source, target, List.of());
    }

    public static void copyFileStructure(File source, File target, List<String> ignore) throws IOException {
        if (!ignore.contains(source.getName())) {
            if (source.isDirectory()) {
                if (!target.exists())
                    if (!target.mkdirs())
                        throw new IOException("Couldn't create world directory!");
                String[] files = source.list();
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(target, file);
                    copyFileStructure(srcFile, destFile, ignore);
                }
            } else {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteRecursion(File file) throws IOException {
        if (!file.exists()) return;

        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursion(child);
            }
        }
        Files.delete(file.toPath());
    }
}
