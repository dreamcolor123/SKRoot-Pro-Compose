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
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class MagicaRootHelper {
    public interface ResultCallback {
        void onResult(String result);
    }
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static void executeMagicaRootScript(Context context, String script, ResultCallback callback) {
        AtomicBoolean finished = new AtomicBoolean(false);
        ServiceConnection connection = new ServiceConnection() {
            private IRemoteService remoteService;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
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
                    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
                    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

                    Thread outThread = new Thread(() -> copyStream(process.getInputStream(), stdoutBuffer), "Magica-stdout");
                    Thread errThread = new Thread(() -> copyStream(process.getErrorStream(), stderrBuffer), "Magica-stderr");
                    try {
                        outThread.start();
                        errThread.start();
                        OutputStream stdin = process.getOutputStream();
                        writeStringChunked(stdin, script, 4096);
                        stdin.flush();
                        stdin.close();

                        int exitCode = process.waitFor();

                        outThread.join();
                        errThread.join();

                        String stdout = stdoutBuffer.toString(StandardCharsets.UTF_8.name());
                        String stderr = stderrBuffer.toString(StandardCharsets.UTF_8.name());

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
                            stdout = stdoutBuffer.toString(StandardCharsets.UTF_8.name());
                            stderr = stderrBuffer.toString(StandardCharsets.UTF_8.name());
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        StringBuilder msg = new StringBuilder();
                        msg.append("ERROR: ").append(t.getMessage()).append('\n');

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
                        try {
                            if (process != null) process.destroy();
                        } catch (Throwable ignore) {}
                        safeUnbind(context, this);
                    }
                }, "Magica-Exec").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                postResultOnce(context, finished, callback, "ERROR: service disconnected");
            }
        };

        // Isolated-service startup or its shell can occasionally stop producing
        // callbacks. End the binding and release the UI after the same timeout
        // used by MainViewModel's hotload operation.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!finished.get()) {
                postResultOnce(context, finished, callback, "ERROR: Magica execution timed out after 75 seconds");
                safeUnbind(context, connection);
            }
        }, 75_000L);

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
                out.write(buf, 0, len);
            }
        } catch (IOException ignored) {
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
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
