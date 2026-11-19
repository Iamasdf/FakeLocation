package com.zcshou.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zcshou.gogogo.BuildConfig;

import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NominatimGeocoder {
    public interface Callback {
        void onAddress(@NonNull String address);

        void onError(@Nullable String message);
    }

    private static final long MIN_REQUEST_INTERVAL_MS = 1100L;
    private static final long DEBOUNCE_MS = 250L;

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable sendRunnable;
    private final String userAgent;

    private Call currentCall;
    private GeoPoint pendingPoint;
    private Callback pendingCallback;
    private long lastRequestAt;
    private int sequence;
    private boolean shutdown;

    public NominatimGeocoder(Context context) {
        userAgent = "GoGoGo/" + BuildConfig.VERSION_NAME
                + " (https://github.com/zcshou/gogogo; " + context.getPackageName() + ")";
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        sendRunnable = this::sendPending;
    }

    public void reverse(@NonNull GeoPoint point, @NonNull Callback callback) {
        if (shutdown) {
            return;
        }
        pendingPoint = point;
        pendingCallback = callback;
        mainHandler.removeCallbacks(sendRunnable);
        long delay = Math.max(0L, MIN_REQUEST_INTERVAL_MS
                - (System.currentTimeMillis() - lastRequestAt));
        mainHandler.postDelayed(sendRunnable, delay + DEBOUNCE_MS);
    }

    public void cancel() {
        mainHandler.removeCallbacks(sendRunnable);
        pendingPoint = null;
        pendingCallback = null;
        sequence++;
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    public void shutdown() {
        shutdown = true;
        cancel();
        client.dispatcher().executorService().shutdown();
        // Do not call connectionPool().evictAll() here: it performs network I/O
        // and would throw NetworkOnMainThreadException when called from onDestroy().
    }

    private void sendPending() {
        final GeoPoint point = pendingPoint;
        final Callback callback = pendingCallback;
        pendingPoint = null;
        pendingCallback = null;
        if (point == null || callback == null || shutdown) {
            return;
        }

        if (currentCall != null) {
            currentCall.cancel();
        }
        lastRequestAt = System.currentTimeMillis();
        final int requestId = ++sequence;

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("nominatim.openstreetmap.org")
                .addPathSegment("reverse")
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("addressdetails", "1")
                .addQueryParameter("zoom", "18")
                .addQueryParameter("lat", String.format(Locale.US, "%.7f", point.getLatitude()))
                .addQueryParameter("lon", String.format(Locale.US, "%.7f", point.getLongitude()))
                .addQueryParameter("accept-language", "zh-CN")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5")
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (requestId == sequence && !shutdown) {
                    deliver(callback, null, e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (requestId != sequence || shutdown) {
                    response.close();
                    return;
                }
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        deliver(callback, null, "HTTP " + response.code());
                        return;
                    }
                    String address = parseAddress(body, point);
                    if (address.isEmpty()) {
                        deliver(callback, null, "no address");
                    } else {
                        deliver(callback, address, null);
                    }
                } catch (Exception e) {
                    deliver(callback, null, e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private void deliver(@Nullable Callback callback, @Nullable String address, @Nullable String error) {
        if (callback == null || shutdown) {
            return;
        }
        mainHandler.post(() -> {
            if (shutdown) {
                return;
            }
            if (address != null) {
                callback.onAddress(address);
            } else {
                callback.onError(error);
            }
        });
    }

    private static String parseAddress(String body, GeoPoint point) {
        try {
            JSONObject json = new JSONObject(body);
            String displayName = json.optString("display_name", "").trim();
            if (!displayName.isEmpty()) {
                return displayName;
            }

            JSONObject address = json.optJSONObject("address");
            if (address != null) {
                StringBuilder builder = new StringBuilder();
                String[] keys = {"country", "state", "city", "county", "suburb", "road", "house_number"};
                for (String key : keys) {
                    String part = address.optString(key, "").trim();
                    if (!part.isEmpty() && builder.indexOf(part) < 0) {
                        if (builder.length() > 0) {
                            builder.append(", ");
                        }
                        builder.append(part);
                    }
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return String.format(Locale.US, "%.6f, %.6f", point.getLatitude(), point.getLongitude());
    }
}
