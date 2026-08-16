package com.linux.permissionmanager.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ShellUtils {

    private static final long DEFAULT_TIMEOUT_SECONDS = 70L;

    public static String executeScript(Context context, String scriptContent) {
        return executeScript(context, scriptContent, DEFAULT_TIMEOUT_SECONDS);
    }

    public static String executeScript(Context context, String scriptContent, long timeoutSeconds) {
        File defaultFile = new File(context.getCacheDir(), "temp_script.sh");
        return executeScript(scriptContent, defaultFile.getAbsolutePath(), timeoutSeconds);
    }

    public static String executeScript(String scriptContent, String scriptPath) {
        return executeScript(scriptContent, scriptPath, DEFAULT_TIMEOUT_SECONDS);
    }

    public static String executeScript(String scriptContent, String scriptPath, long timeoutSeconds) {
        StringBuffer outputBuilder = new StringBuffer();
        Process process = null;
        File scriptFile = null;

        try {
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                throw new IllegalArgumentException("scriptPath is null or empty");
            }
            if (timeoutSeconds <= 0L) {
                throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
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

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (finished) {
                outputBuilder.append("\n[Exit Code: ").append(process.exitValue()).append("]");
            } else {
                outputBuilder.append("\n[Execution Timeout: ")
                        .append(timeoutSeconds)
                        .append(" seconds]");
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
            // Upstream 4.5.6 deliberately keeps the private cache script: the
            // Ghostlock path may re-enter the same file after the parent shell
            // exits. The next hotload execution overwrites this cache entry.
        }

        return outputBuilder.toString();
    }
}
