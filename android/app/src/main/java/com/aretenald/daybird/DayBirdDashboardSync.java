package com.aretenald.daybird;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getcapacitor.JSObject;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class DayBirdDashboardSync {
    interface Callback<T> {
        void complete(T value, Exception error);
    }

    @FunctionalInterface
    interface RefreshOperation {
        void run() throws Exception;
    }

    private static final String PERIODIC_WORK = "daybird-dashboard-periodic-sync";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Object LISTENER_LOCK = new Object();
    private static ListenerRegistration listener;
    private static String listenerPath = "";

    private DayBirdDashboardSync() {}

    static void runAsync(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DayBirdDashboardWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        );
    }

    static void startListener(Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String ownerUid = DayBirdDashboardState.ownerUid(appContext);
        if (user == null || ownerUid.isBlank()) return;
        String path = "users/" + ownerUid + "/dashboard/latest";
        synchronized (LISTENER_LOCK) {
            if (listener != null && path.equals(listenerPath)) return;
            if (listener != null) listener.remove();
            listenerPath = path;
            listener = FirebaseFirestore.getInstance().document(path).addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    DayBirdDashboardState.saveError(appContext, error);
                    if (error instanceof FirebaseFirestoreException firestoreError
                        && firestoreError.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        FirebaseAuth.getInstance().signOut();
                        DayBirdDashboardState.clearConnection(appContext);
                        stopListener();
                    }
                    return;
                }
                if (snapshot != null && snapshot.exists()) saveDocument(appContext, snapshot);
            });
        }
    }

    static void stopListener() {
        synchronized (LISTENER_LOCK) {
            if (listener != null) listener.remove();
            listener = null;
            listenerPath = "";
        }
    }

    static boolean refreshBlocking(Context context) throws Exception {
        Context appContext = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String ownerUid = DayBirdDashboardState.ownerUid(appContext);
        if (user == null || ownerUid.isBlank()) return false;
        DocumentSnapshot snapshot = Tasks.await(
            FirebaseFirestore.getInstance().document("users/" + ownerUid + "/dashboard/latest").get(),
            25,
            TimeUnit.SECONDS
        );
        return snapshot.exists() && saveDocument(appContext, snapshot);
    }

    static boolean hasConnectedSession(Context context) {
        return shouldRequestOverlay(
            FirebaseAuth.getInstance().getCurrentUser() != null,
            DayBirdDashboardState.ownerUid(context),
            DayBirdDashboardState.authUid(context)
        );
    }

    static boolean shouldRequestOverlay(boolean signedIn, String ownerUid, String authUid) {
        return signedIn
            && ownerUid != null && !ownerUid.isBlank()
            && authUid != null && !authUid.isBlank();
    }

    static void refreshPeriodicBlocking(Context context) throws Exception {
        runPeriodicRefresh(
            hasConnectedSession(context),
            () -> requestRefreshBlocking(context),
            () -> refreshBlocking(context)
        );
    }

    static void runPeriodicRefresh(
        boolean requestOverlay,
        RefreshOperation overlayRefresh,
        RefreshOperation snapshotRefresh
    ) throws Exception {
        if (!requestOverlay) {
            snapshotRefresh.run();
            return;
        }
        try {
            overlayRefresh.run();
        } catch (Exception overlayError) {
            try {
                snapshotRefresh.run();
            } catch (Exception snapshotError) {
                overlayError.addSuppressed(snapshotError);
                throw overlayError;
            }
        }
    }

    static void refreshAsync(Context context, Callback<Boolean> callback) {
        runAsync(() -> {
            try {
                callback.complete(refreshBlocking(context), null);
            } catch (Exception error) {
                DayBirdDashboardState.saveError(context, error);
                callback.complete(false, error);
            }
        });
    }

    static JSObject exchangePairingBlocking(Context context, String code) throws Exception {
        String fcmToken = "";
        try {
            fcmToken = Tasks.await(FirebaseMessaging.getInstance().getToken(), 20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Pairing must remain available while Play services or FCM registration is warming up.
            // DayBirdMessagingService registers the token later through onNewToken.
        }
        JSONObject body = new JSONObject()
            .put("code", code)
            .put("deviceId", DayBirdDashboardState.deviceId(context))
            .put("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
        if (fcmToken != null && !fcmToken.isBlank()) body.put("fcmToken", fcmToken);
        JSONObject response = DayBirdDashboardHttp.post(context, "pairings/exchange", body, null);
        String customToken = response.getString("customToken");
        String ownerUid = response.getString("ownerUid");
        String authUid = response.getString("authUid");
        Tasks.await(FirebaseAuth.getInstance().signInWithCustomToken(customToken), 25, TimeUnit.SECONDS);
        DayBirdDashboardState.saveConnection(context, ownerUid, authUid);
        schedule(context);
        startListener(context);
        refreshBlocking(context);
        return DayBirdDashboardState.status(context);
    }

    static JSONObject requestRefreshBlocking(Context context) throws Exception {
        String token = currentIdToken();
        JSONObject response = DayBirdDashboardHttp.post(context, "refresh", new JSONObject(), token);
        refreshBlocking(context);
        return response;
    }

    static JSONObject saveWeightsBlocking(Context context, JSONObject weights) throws Exception {
        String token = currentIdToken();
        JSONObject response = DayBirdDashboardHttp.post(
            context,
            "settings",
            new JSONObject().put("weights", weights),
            token
        );
        refreshBlocking(context);
        return response;
    }

    static JSONObject registerFcmTokenBlocking(Context context, String fcmToken) throws Exception {
        if (FirebaseAuth.getInstance().getCurrentUser() == null || DayBirdDashboardState.ownerUid(context).isBlank()) return null;
        return DayBirdDashboardHttp.post(
            context,
            "devices",
            new JSONObject()
                .put("fcmToken", fcmToken)
                .put("deviceName", Build.MANUFACTURER + " " + Build.MODEL),
            currentIdToken()
        );
    }

    static JSONObject disconnectBlocking(Context context) throws Exception {
        JSONObject response;
        try {
            response = DayBirdDashboardHttp.post(context, "disconnect", new JSONObject(), currentIdToken());
        } catch (Exception error) {
            response = new JSONObject().put("disconnected", false).put("localOnly", true);
        } finally {
            stopListener();
            FirebaseAuth.getInstance().signOut();
            DayBirdDashboardState.clearConnection(context);
        }
        return response;
    }

    private static String currentIdToken() throws Exception {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("DayBird is not connected");
        return Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).getToken();
    }

    private static boolean saveDocument(Context context, DocumentSnapshot snapshot) {
        try {
            JSONObject json = objectToJson(snapshot.getData());
            return DayBirdDashboardState.saveSnapshot(context, json);
        } catch (Exception error) {
            DayBirdDashboardState.saveError(context, error);
            return false;
        }
    }

    private static JSONObject objectToJson(Map<String, Object> map) throws Exception {
        JSONObject object = new JSONObject();
        if (map == null) return object;
        for (Map.Entry<String, Object> entry : map.entrySet()) object.put(entry.getKey(), jsonValue(entry.getValue()));
        return object;
    }

    private static Object jsonValue(Object value) throws Exception {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Timestamp timestamp) return timestamp.toDate().getTime();
        if (value instanceof Map<?, ?> map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) object.put(String.valueOf(entry.getKey()), jsonValue(entry.getValue()));
            return object;
        }
        if (value instanceof List<?> list) {
            JSONArray array = new JSONArray();
            for (Object item : list) array.put(jsonValue(item));
            return array;
        }
        return value;
    }
}
