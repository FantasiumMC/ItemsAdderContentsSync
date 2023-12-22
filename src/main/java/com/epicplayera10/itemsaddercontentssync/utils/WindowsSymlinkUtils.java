package com.epicplayera10.itemsaddercontentssync.utils;

import java.io.File;
import java.io.IOException;

public class WindowsSymlinkUtils {

    public static void createJunctionSymlink(File link, File target) {
        createSymlink(WinSymlinkFlag.JUNCTION, link, target);
    }

    public static void createSymlink(WinSymlinkFlag flag, File link, File target) {
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/C", "mklink", flag.rawFlag, link.getAbsolutePath(), target.getAbsolutePath());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException("Failed to create windows symlink. Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
