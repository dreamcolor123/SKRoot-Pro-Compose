package com.linux.permissionmanager.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ShellUtils {

    public static String executeScript(Context context, String scriptContent) {
        File defaultFile = new File(context.getCacheDir(), "temp_script.sh");
        return executeScript( scriptContent, defaultFile.getAbsolutePath());
    }

    public static String executeScript(String scriptContent, String scriptPath) {
        StringBuilder outputBuilder = new StringBuilder();
        Process process = null;
        File scriptFile = null;

        try {
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                throw new IllegalArgumentException("scriptPath is null or empty");
            }

            scriptFile = new File(scriptPath);

            File parent = scriptFile.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean mkdirsOk = parent.mkdirs();
                if (!mkdirsOk && !parent.exists()) {
                    throw new RuntimeException("Failed to create parent directory: " + parent.getAbsolutePath());
                }
            }

            try (FileOutputStream fos = new FileOutputStream(scriptFile)) {
                fos.write(scriptContent.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            boolean chmodOk = scriptFile.setExecutable(true, false);
            outputBuilder.append("[Script Path] ").append(scriptFile.getAbsolutePath()).append("\n");
            outputBuilder.append("[setExecutable] ").append(chmodOk).append("\n");

            ProcessBuilder processBuilder = new ProcessBuilder("sh", scriptFile.getAbsolutePath());
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            outputBuilder.append("\n[Exit Code: ").append(exitCode).append("]");
        } catch (Exception e) {
            e.printStackTrace();
            outputBuilder.append("\n[Execution Error: ").append(e.getMessage()).append("]");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (scriptFile != null && scriptFile.exists()) {
                boolean deleted = scriptFile.delete();
                outputBuilder.append("\n[Delete Script] ").append(deleted);
            }
        }

        return outputBuilder.toString();
    }
}