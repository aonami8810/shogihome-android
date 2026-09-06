package jp.shogihome.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import shogi.oex.IEngineService;
import shogi.oex.IEngineServiceCallback;

@CapacitorPlugin(name = "ShogiHomeUSIEngine")
public class USIEnginePlugin extends Plugin {
    static {
        Log.i("USIEnginePlugin", "USIEnginePlugin LOADED");
    }

    private static final String TAG = "USIEnginePlugin";
    private static final String ACTION_OEX_ENGINE = "shogi.oex.ENGINE";

    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private final Map<Integer, EngineSession> sessions = new HashMap<>();

    @Override
    public void load() {
        super.load();
        Log.i(TAG, "USIEnginePlugin instance loaded into bridge!");
    }

    private interface ISession {
        void sendCommand(String command);
        void close();
    }

    private class EngineSession implements ServiceConnection, ISession {
        final int id;
        IEngineService service;
        boolean bound = false;
        PluginCall launchCall; // launchメソッドの完了を待機するためのコール
        private final List<String> commandQueue = new ArrayList<>();

        EngineSession(int id, PluginCall call) {
            this.id = id;
            this.launchCall = call;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "AIDL Service Connected: " + name.flattenToString());
            this.service = IEngineService.Stub.asInterface(binder);
            try {
                this.service.registerCallback(new IEngineServiceCallback.Stub() {
                    @Override
                    public void onReceiveResponse(String response) throws RemoteException {
                        Log.d(TAG, "AIDL Response: " + response);
                        JSObject ret = new JSObject();
                        ret.put("sessionId", id);
                        ret.put("message", response);
                        notifyListeners("usiMessage", ret);
                    }
                });

                // 接続を待っていた launch コールを解決する (Promiseの解決に相当)
                if (launchCall != null) {
                    JSObject ret = new JSObject();
                    ret.put("sessionId", id);
                    launchCall.resolve(ret);
                    launchCall = null;
                }

                // 接続待ちの間に溜まったコマンドがあれば即座に送信
                synchronized (commandQueue) {
                    for (String cmd : commandQueue) {
                        this.service.sendCommand(cmd);
                    }
                    commandQueue.clear();
                }
            } catch (Throwable e) {
                Log.w(TAG, "Communication error during AIDL initialization: " + e.getMessage());
                if (launchCall != null) {
                    launchCall.reject(e.getMessage());
                    launchCall = null;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "AIDL Service Disconnected: " + name.flattenToString());
            this.service = null;
        }

        @Override
        public void sendCommand(String command) {
            if (service != null) {
                try {
                    service.sendCommand(command);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to send command to AIDL service", e);
                }
            } else {
                // まだ接続されていない場合はキューに入れる
                synchronized (commandQueue) {
                    commandQueue.add(command);
                }
            }
        }

        @Override
        public void close() {
            if (bound) {
                getContext().unbindService(this);
                bound = false;
            }
        }
    }

    @PluginMethod
    public void getEngines(PluginCall call) {
        Log.i(TAG, "Searching for available AIDL engines...");
        PackageManager pm = getContext().getPackageManager();
        Map<String, JSObject> engineMap = new HashMap<>();

        Intent aidlIntent = new Intent(ACTION_OEX_ENGINE);
        List<ResolveInfo> services = pm.queryIntentServices(aidlIntent, PackageManager.GET_META_DATA);
        for (ResolveInfo info : services) {
            JSObject engine = new JSObject();
            engine.put("name", info.loadLabel(pm).toString());
            engine.put("packageName", info.serviceInfo.packageName);
            engine.put("type", "aidl");
            engineMap.put(info.serviceInfo.packageName, engine);
        }

        com.getcapacitor.JSArray enginesArray = new com.getcapacitor.JSArray();
        for (JSObject engine : engineMap.values()) {
            enginesArray.put(engine);
        }

        JSObject ret = new JSObject();
        ret.put("engines", enginesArray);
        call.resolve(ret);
    }

    @PluginMethod
    public void launch(PluginCall call) {
        String packageName = call.getString("packageName");
        if (packageName == null) {
            call.reject("packageName is required");
            return;
        }

        int sessionId = nextSessionId.getAndIncrement();
        // launchコール自体をセッションに持たせ、onServiceConnectedでresolveする
        EngineSession session = new EngineSession(sessionId, call);

        Intent intent = new Intent(ACTION_OEX_ENGINE);
        intent.setPackage(packageName);

        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        if (services.isEmpty()) {
            call.reject("Engine service not found");
            return;
        }
        ResolveInfo ri = services.get(0);
        intent.setComponent(new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name));

        boolean ok = getContext().bindService(intent, session, Context.BIND_AUTO_CREATE);
        if (ok) {
            session.bound = true;
            sessions.put(sessionId, session);
            // ここでは call.resolve() を呼ばず、onServiceConnected を待つ
        } else {
            call.reject("Failed to bind to engine service");
        }
    }

    @PluginMethod
    public void sendCommand(PluginCall call) {
        Integer sessionId = call.getInt("sessionId");
        String command = call.getString("command");
        if (sessionId == null || command == null) {
            call.reject("sessionId and command are required");
            return;
        }

        EngineSession session = sessions.get(sessionId);
        if (session == null) {
            call.reject("Session not found");
            return;
        }

        session.sendCommand(command);
        call.resolve();
    }

    @PluginMethod
    public void quit(PluginCall call) {
        Integer sessionId = call.getInt("sessionId");
        if (sessionId == null) {
            call.reject("sessionId is required");
            return;
        }

        EngineSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
        call.resolve();
    }

    @PluginMethod
    public void getInfo(PluginCall call) {
        String packageName = call.getString("packageName");
        int timeoutSeconds = call.getInt("timeoutSeconds", 15);
        if (packageName == null) {
            call.reject("packageName is required");
            return;
        }

        Intent intent = new Intent(ACTION_OEX_ENGINE);
        intent.setPackage(packageName);
        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        if (services.isEmpty()) {
            call.reject("Engine service not found");
            return;
        }
        ResolveInfo ri = services.get(0);
        intent.setComponent(new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name));

        final long timeoutMs = timeoutSeconds * 1000L;
        final StringBuilder infoResponse = new StringBuilder();
        final boolean[] usiOkReceived = {false};

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.i(TAG, "getInfo connecting to: " + name.flattenToString());

                try {
                    PackageManager pm = getContext().getPackageManager();
                    PackageInfo pi = pm.getPackageInfo(name.getPackageName(), 0);
                    Log.i(TAG, "TARGET APP: " + name.getPackageName() + " Version: " + pi.versionName + " Updated: " + pi.lastUpdateTime);
                } catch (Exception ignored) {}

                IEngineService service = IEngineService.Stub.asInterface(binder);
                try {
                    service.registerCallback(new IEngineServiceCallback.Stub() {
                        @Override
                        public void onReceiveResponse(String response) throws RemoteException {
                            synchronized (usiOkReceived) {
                                if (usiOkReceived[0]) return;
                                Log.i(TAG, "Engine Initial Response: " + response);
                                infoResponse.append(response).append("\n");
                                if (response.trim().equals("usiok")) {
                                    usiOkReceived[0] = true;
                                    usiOkReceived.notify();
                                }
                            }
                        }
                    });

                    service.sendCommand("usi");

                } catch (Throwable e) {
                    Log.w(TAG, "Error in AIDL handshake: " + e.getMessage());
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "AIDL Disconnected: " + name.flattenToString());
            }
        };

        boolean bound = getContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);
        if (!bound) {
            call.reject("Failed to bind service for getInfo");
            return;
        }

        new Thread(() -> {
            try {
                synchronized (usiOkReceived) {
                    if (!usiOkReceived[0]) {
                        usiOkReceived.wait(timeoutMs + 2000);
                    }
                }
                if (usiOkReceived[0]) {
                    JSObject ret = new JSObject();
                    ret.put("rawInfo", infoResponse.toString());
                    call.resolve(ret);
                } else {
                    String lastWords = infoResponse.toString().trim();
                    String errorMessage = "Timeout waiting for usiok";
                    if (!lastWords.isEmpty()) {
                        errorMessage += "\n\nEngine output:\n" + lastWords;
                    }
                    Log.w(TAG, errorMessage + " from " + packageName);
                    call.reject(errorMessage);
                }
            } catch (InterruptedException e) {
                call.reject("Interrupted");
            } finally {
                getContext().unbindService(conn);
            }
        }).start();
    }

    @PluginMethod
    public void getMachineSpec(PluginCall call) {
        JSObject ret = new JSObject();
        int cores = Runtime.getRuntime().availableProcessors();
        ret.put("cpuCores", cores);

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        ret.put("memory", mi.totalMem);

        call.resolve(ret);
    }
}
