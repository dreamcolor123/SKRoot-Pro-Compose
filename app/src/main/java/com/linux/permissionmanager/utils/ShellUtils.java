package com.linux.permissionmanager.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ShellUtils {

    public static String executeScript(Context context, String scriptContent) {
        File defaultFile = new File(context.getCacheDir(), "temp_script.sh");
        return executeScript( scriptContent, defaultFile.getAbsolutePath());
    }

    public static String executeScript(String scriptContent, String scriptPath) {
        StringBuffer outputBuilder = new StringBuffer();
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

            Process runningProcess = process;
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                    }
                } catch (Exception e) {
                    if (runningProcess.isAlive()) {
                        outputBuilder.append("\n[Output Error: ").append(e.getMessage()).append("]");
                    }
                }
            }, "SKRoot-Hotload-Output");
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(70, TimeUnit.SECONDS);
            if (finished) {
                outputBuilder.append("\n[Exit Code: ").append(process.exitValue()).append("]");
            } else {
                outputBuilder.append("\n[Execution Timeout: 70 seconds]");
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                try {
                    process.getInputStream().close();
                } catch (Exception ignored) {}
            }
            outputReader.join(2_000);
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
