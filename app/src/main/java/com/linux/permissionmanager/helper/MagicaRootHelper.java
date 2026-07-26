package com.linux.permissionmanager.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MagicaRootHelper {
    private static final long EXECUTION_TIMEOUT_MS = 180_000L;
    private static final long STREAM_DRAIN_TIMEOUT_MS = 1_500L;

    public interface ResultCallback {
        void onResult(String result);
    }
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static void executeMagicaRootScript(Context context, String script, ResultCallback callback) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<String> phase = new AtomicReference<>("binding isolated service");
        AtomicReference<RemoteProcess> activeProcess = new AtomicReference<>();
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        ServiceConnection connection = new ServiceConnection() {
            private IRemoteService remoteService;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                phase.set("service connected");
                remoteService = IRemoteService.Stub.asInterface(service);
                new Thread(() -> {
                    IRemoteProcess remoteProcess = null;
                    try {
                        remoteProcess = remoteService.getRemoteProcess();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    if (remoteProcess == null) {
                        postResultOnce(context, finished, callback, "ERROR: remote process is null");
                        safeUnbind(context, this);
                        return;
                    }
                    final RemoteProcess process = new RemoteProcess(remoteProcess);
                    activeProcess.set(process);

                    Thread outThread = new Thread(() -> copyStream(process.getInputStream(), stdoutBuffer), "Magica-stdout");
                    Thread errThread = new Thread(() -> copyStream(process.getErrorStream(), stderrBuffer), "Magica-stderr");
                    try {
                        phase.set("opening process streams");
                        outThread.start();
                        errThread.start();
                        OutputStream stdin = process.getOutputStream();
                        int scriptBytes = script == null ? 0 : script.getBytes(StandardCharsets.UTF_8).length;
                        phase.set("sending script (" + scriptBytes + " bytes)");
                        writeStringChunked(stdin, script, 4096);
                        stdin.flush();
                        stdin.close();

                        phase.set("waiting for script process");
                        int exitCode = process.waitFor();

                        // A hotload script may launch a background child which
                        // inherits stdout/stderr. The shell has already exited,
                        // so waiting forever for EOF here only leaves the UI on
                        // "loading" even though the payload has completed.
                        phase.set("draining process output");
                        outThread.join(STREAM_DRAIN_TIMEOUT_MS);
                        errThread.join(STREAM_DRAIN_TIMEOUT_MS);
                        closeProcessReadStreams(process);
                        outThread.join(300L);
                        errThread.join(300L);

                        String stdout = snapshot(stdoutBuffer);
                        String stderr = snapshot(stderrBuffer);

                        StringBuilder result = new StringBuilder();
                        result.append(stdout);

                        if (!stderr.isEmpty()) {
                            if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                                result.append('\n');
                            }
                            result.append("[stderr]\n").append(stderr);
                        }

                        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                            result.append('\n');
                        }
                        result.append("[exitCode] ").append(exitCode).append('\n');

                        postResultOnce(context, finished, callback, result.toString());
                    } catch (Throwable t) {
                        try {
                            process.destroy();
                        } catch (Throwable ignore) {}
                        try {
                            outThread.join(300);
                            errThread.join(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }

                        String stdout = null;
                        String stderr = null;
                        try {
                            stdout = snapshot(stdoutBuffer);
                            stderr = snapshot(stderrBuffer);
                        } catch (Throwable ignored) {}
                        StringBuilder msg = new StringBuilder();
                        msg.append("ERROR [").append(phase.get()).append("]: ")
                                .append(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())
                                .append('\n');

                        if (!TextUtils.isEmpty(stdout)) {
                            msg.append("[stdout]\n").append(stdout);
                            if (stdout.charAt(stdout.length() - 1) != '\n') msg.append('\n');
                        }

                        if (!TextUtils.isEmpty(stderr)) {
                            msg.append("[stderr]\n").append(stderr);
                            if (stderr.charAt(stderr.length() - 1) != '\n') msg.append('\n');
                        }

                        postResultOnce(context, finished, callback, msg.toString());
                    } finally {
                        activeProcess.compareAndSet(process, null);
                        try {
                            if (process != null) process.destroy();
                        } catch (Throwable ignore) {}
                        safeUnbind(context, this);
                    }
                }, "Magica-Exec").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                postResultOnce(context, finished, callback,
                        diagnosticResult("ERROR: service disconnected", phase.get(), stdoutBuffer, stderrBuffer));
            }

            @Override
            public void onBindingDied(ComponentName name) {
                postResultOnce(context, finished, callback,
                        diagnosticResult("ERROR: isolated service binding died", phase.get(), stdoutBuffer, stderrBuffer));
                safeUnbind(context, this);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                postResultOnce(context, finished, callback,
                        diagnosticResult(
                                "ERROR: Magica service returned a null binder; root transition failed",
                                phase.get(), stdoutBuffer, stderrBuffer));
                safeUnbind(context, this);
            }
        };

        // Keep a finite upper bound, but allow slower devices substantially
        // longer than the previous 75-second cutoff. Include the last phase and
        // partial process output so the next device log identifies the exact
        // failure instead of reporting a generic timeout.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!finished.get()) {
                RemoteProcess process = activeProcess.getAndSet(null);
                if (process != null) {
                    try {
                        process.destroy();
                    } catch (Throwable ignore) {}
                    closeProcessReadStreams(process);
                }
                postResultOnce(context, finished, callback, diagnosticResult(
                        "ERROR: Magica execution timed out after 180 seconds",
                        phase.get(), stdoutBuffer, stderrBuffer));
                safeUnbind(context, connection);
            }
        }, EXECUTION_TIMEOUT_MS);

        try {
            Intent intent = new Intent(context, MagicaService.class);
            Executor executor = context.getMainExecutor();
            boolean ok = context.bindIsolatedService(intent, Context.BIND_AUTO_CREATE, "magica", executor, connection);
            if (!ok) postResultOnce(context, finished, callback, "ERROR: bindIsolatedService returned false");
        } catch (Throwable t) {
            postResultOnce(context, finished, callback, "ERROR: bindIsolatedService failed - " + t.getMessage());
        }
    }

    private static void writeStringChunked(OutputStream os, String text, int chunkSize) throws IOException {
        if (os == null) throw new IllegalArgumentException("OutputStream == null");
        if (text == null) text = "";
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must > 0");
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(chunkSize, data.length - offset);
            os.write(data, offset, len);
            offset += len;
        }
    }
    private static void copyStream(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        int len;
        try {
            while ((len = in.read(buf)) != -1) {
                synchronized (out) {
                    out.write(buf, 0, len);
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }

    private static String snapshot(ByteArrayOutputStream stream) {
        synchronized (stream) {
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void closeProcessReadStreams(RemoteProcess process) {
        try {
            process.getInputStream().close();
        } catch (Throwable ignore) {}
        try {
            process.getErrorStream().close();
        } catch (Throwable ignore) {}
    }

    private static String diagnosticResult(
            String message,
            String phase,
            ByteArrayOutputStream stdoutBuffer,
            ByteArrayOutputStream stderrBuffer
    ) {
        String stdout = snapshot(stdoutBuffer);
        String stderr = snapshot(stderrBuffer);
        StringBuilder result = new StringBuilder(message)
                .append("\n[phase] ").append(phase).append('\n');
        if (!stdout.isEmpty()) result.append("[stdout]\n").append(stdout).append('\n');
        if (!stderr.isEmpty()) result.append("[stderr]\n").append(stderr).append('\n');
        return result.toString();
    }

    private static void safeUnbind(Context context, ServiceConnection conn) {
        try {
            context.unbindService(conn);
        } catch (Throwable ignore) {}
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static void postResultOnce(Context context, AtomicBoolean finished, ResultCallback callback, String result) {
        if (!finished.compareAndSet(false, true)) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onResult(result);
        } else {
            context.getMainExecutor().execute(() -> callback.onResult(result));
        }
    }
}
