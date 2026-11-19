package com.zcshou.gogogo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.elvishew.xlog.XLog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.LocationHelper;
import com.zcshou.utils.MapUtils;
import com.zcshou.utils.NominatimGeocoder;
import com.zcshou.utils.OsmMapController;
import com.zcshou.utils.ShareUtils;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    /* 首页持久化所选位置的偏好键 */
    private static final String KEY_MARK_LAT = "KEY_MARK_LAT";
    private static final String KEY_MARK_LNG = "KEY_MARK_LNG";
    private static final String KEY_MARK_NAME = "KEY_MARK_NAME";
    private static final String KEY_MARK_WGS84 = "KEY_MARK_WGS84";

    private static final GeoPoint DEFAULT_POINT = new GeoPoint(36.547743718042415, 117.07018449827267);

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    private MapView mMapView;
    private OsmMapController mMapController;
    private NominatimGeocoder mGeocoder;
    private TextView mAddressText;
    private static GeoPoint mMarkPoint = DEFAULT_POINT;
    private static String mMarkName = null;
    private static boolean mHasSelectedLocation = false;
    private boolean mSelectPending = false;
    private final android.os.Handler mSelectHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private FloatingActionButton mButtonStart;

    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;

    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();
        initMap();
        restoreSelectedLocation();
        initMapButton();
        initGoBtn();
        initStoreHistory();
        initUpdateVersion();
        checkUpdateVersion(false);
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mMapView.onResume();
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        unregisterReceiver(mDownloadBdRcv);
        if (mGeocoder != null) {
            mGeocoder.shutdown();
        }
        mSelectHandler.removeCallbacksAndMessages(null);
        mMapView.onDetach();

        if (mLocationHistoryDB != null) {
            mLocationHistoryDB.close();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                    }
                }
            } else if (id == R.id.nav_update) {
                checkUpdateVersion(true);
            } else if (id == R.id.nav_feedback) {
                File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
                ShareUtils.shareFile(this, file, item.getTitle().toString());
            } else if (id == R.id.nav_contact) {
                Uri uri = Uri.parse("https://gitee.com/itexp/gogogo/issues");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        mMapView = findViewById(R.id.osm_map_view);
        mAddressText = findViewById(R.id.osm_address);
        mGeocoder = new NominatimGeocoder(this);
        mMapController = new OsmMapController(this, mMapView, (point, longPress) -> {
            mMarkPoint = point;
            mHasSelectedLocation = true;
            mMarkName = null;
            markMap();
            showAddress(getString(R.string.home_address_loading));
            requestAddress(point);
        });
        mMapController.moveTo(mMarkPoint, 17.0);
        mMapView.post(() -> mMapController.moveTo(mMarkPoint, 17.0));
        showAddress(getString(R.string.home_address_hint));
    }

    private void requestAddress(GeoPoint point) {
        if (mGeocoder == null) {
            return;
        }
        mGeocoder.reverse(point, new NominatimGeocoder.Callback() {
            @Override
            public void onAddress(@NonNull String address) {
                if (isFinishing()) {
                    return;
                }
                mMarkName = address;
                showAddress(address);
                markMap();
                saveSelectedLocation(MainActivity.this);
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) {
                    return;
                }
                XLog.i("Nominatim reverse failed: " + message);
                showAddress(formatCoordinate(point));
            }
        });
    }

    private void showAddress(String address) {
        if (mAddressText != null) {
            mAddressText.setText(address);
        }
    }

    private static String formatCoordinate(GeoPoint point) {
        return String.format(Locale.US, "%.6f, %.6f", point.getLatitude(), point.getLongitude());
    }

    private void initMapButton() {
        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mMapController.zoomIn());

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mMapController.zoomOut());

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> showInputPositionDialog());
    }

    private void showInputPositionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("请输入经度和纬度（WGS84）");
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
        builder.setView(view);
        AlertDialog dialog = builder.show();

        EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
        EditText dialog_lat = view.findViewById(R.id.joystick_latitude);

        Button btnGo = view.findViewById(R.id.input_position_ok);
        btnGo.setOnClickListener(v2 -> {
            String lngText = dialog_lng.getText().toString().trim();
            String latText = dialog_lat.getText().toString().trim();
            if (TextUtils.isEmpty(lngText) || TextUtils.isEmpty(latText)) {
                GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
                return;
            }
            try {
                double lng = Double.parseDouble(lngText);
                double lat = Double.parseDouble(latText);
                if (lng > 180.0 || lng < -180.0) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_longitude));
                    return;
                }
                if (lat > 90.0 || lat < -90.0) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_latitude));
                    return;
                }
                mMarkPoint = new GeoPoint(lat, lng);
                mHasSelectedLocation = true;
                mMarkName = getString(R.string.input_position_manual);
                markMap();
                mMapController.moveTo(mMarkPoint, 17.0);
                showAddress(getString(R.string.home_address_loading));
                requestAddress(mMarkPoint);
                dialog.dismiss();
            } catch (NumberFormatException e) {
                GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
            }
        });

        Button btnCancel = view.findViewById(R.id.input_position_cancel);
        btnCancel.setOnClickListener(v1 -> dialog.dismiss());
    }

    private void markMap() {
        if (mMarkPoint == null) {
            return;
        }
        mMapController.showMarker(mMarkPoint, mMarkName, formatCoordinate(mMarkPoint));
        mHasSelectedLocation = true;
        saveSelectedLocation(this);
    }

    private static void saveSelectedLocation(Context context) {
        if (mMarkPoint == null) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_MARK_LAT, String.valueOf(mMarkPoint.getLatitude()))
                .putString(KEY_MARK_LNG, String.valueOf(mMarkPoint.getLongitude()))
                .putString(KEY_MARK_NAME, mMarkName == null ? "" : mMarkName)
                .putBoolean(KEY_MARK_WGS84, true)
                .apply();
    }

    private void restoreSelectedLocation() {
        String lat = sharedPreferences.getString(KEY_MARK_LAT, null);
        String lng = sharedPreferences.getString(KEY_MARK_LNG, null);

        if (lat == null || lng == null || lat.isEmpty() || lng.isEmpty()) {
            return;
        }

        try {
            double latD = Double.parseDouble(lat);
            double lngD = Double.parseDouble(lng);

            if (!sharedPreferences.getBoolean(KEY_MARK_WGS84, false)) {
                double[] wgs = MapUtils.bd2wgs(lngD, latD);
                lngD = wgs[0];
                latD = wgs[1];
                sharedPreferences.edit()
                        .putString(KEY_MARK_LAT, String.valueOf(latD))
                        .putString(KEY_MARK_LNG, String.valueOf(lngD))
                        .putBoolean(KEY_MARK_WGS84, true)
                        .apply();
            }

            mMarkName = sharedPreferences.getString(KEY_MARK_NAME, null);
            mMarkPoint = new GeoPoint(latD, lngD);
            markMap();
            mMapController.moveTo(mMarkPoint, 17.0);
            showAddress(TextUtils.isEmpty(mMarkName) ? formatCoordinate(mMarkPoint) : mMarkName);
        } catch (NumberFormatException e) {
            XLog.e("ERROR: restoreSelectedLocation");
        }
    }

    private void resetMap() {
        Location last = LocationHelper.getLastKnownLocation(this);
        if (last == null) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_current_location));
            mMapController.moveTo(mMarkPoint, 17.0);
            return;
        }
        mMapController.moveTo(new GeoPoint(last.getLatitude(), last.getLongitude()), 17.0);
    }

    private void saveSelectionAndFinish(String name, GeoPoint pos) {
        if (pos == null) {
            finish();
            return;
        }

        double lat = pos.getLatitude();
        double lng = pos.getLongitude();
        String displayName = TextUtils.isEmpty(name)
                ? getResources().getString(R.string.history_location_default_name)
                : name;

        long rowId = -1;
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, displayName);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_ADDRESS, name == null ? "" : name);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(lng));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(lat));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
            // Keep the legacy columns in sync for older readers.
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(lng));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(lat));
            rowId = DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
        } catch (Exception e) {
            XLog.e("ERROR: saveSelection");
        }

        if (rowId > 0) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putLong(HomeActivity.KEY_TARGET_ID, rowId)
                    .apply();
        }

        GoUtils.DisplayToast(this, getResources().getString(R.string.home_selected));
        finish();
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private void doGoLocation(View v) {
        if (mMarkPoint == null) {
            Snackbar.make(v, R.string.home_need_target, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }

        if (mSelectPending) {
            return;
        }

        if (!TextUtils.isEmpty(mMarkName)) {
            saveSelectionAndFinish(mMarkName, mMarkPoint);
            return;
        }

        final GeoPoint selectedPoint = mMarkPoint;
        mSelectPending = true;
        mGeocoder.reverse(selectedPoint, new NominatimGeocoder.Callback() {
            @Override
            public void onAddress(@NonNull String address) {
                if (!mSelectPending) {
                    return;
                }
                mSelectPending = false;
                mSelectHandler.removeCallbacksAndMessages(null);
                mMarkName = address;
                showAddress(address);
                markMap();
                saveSelectionAndFinish(address, selectedPoint);
            }

            @Override
            public void onError(String message) {
                if (!mSelectPending) {
                    return;
                }
                mSelectPending = false;
                mSelectHandler.removeCallbacksAndMessages(null);
                saveSelectionAndFinish(null, selectedPoint);
            }
        });

        mSelectHandler.removeCallbacksAndMessages(null);
        mSelectHandler.postDelayed(() -> {
            if (mSelectPending) {
                mSelectPending = false;
                saveSelectionAndFinish(mMarkName, selectedPoint);
            }
        }, 5000);
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager = (DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE);

        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installNewVersion();
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean result) {
        String mapApiUrl = "https://api.github.com/repos/zcshou/gogogo/releases/latest";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("更新检测失败");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject getRetJson = new JSONObject(resp);
                            String curVersion = GoUtils.getVersionName(MainActivity.this);

                            if (curVersion != null
                                    && (!getRetJson.getString("name").contains(curVersion)
                                    || !getRetJson.getString("tag_name").contains(curVersion))) {
                                final android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.show();
                                alertDialog.setCancelable(false);
                                Window window = alertDialog.getWindow();
                                if (window != null) {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                                    window.setContentView(R.layout.update);
                                    window.setGravity(Gravity.CENTER);
                                    window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

                                    TextView updateTitle = window.findViewById(R.id.update_title);
                                    updateTitle.setText(getRetJson.getString("name"));
                                    TextView updateTime = window.findViewById(R.id.update_time);
                                    updateTime.setText(getRetJson.getString("created_at"));
                                    TextView updateCommit = window.findViewById(R.id.update_commit);
                                    updateCommit.setText(getRetJson.getString("target_commitish"));

                                    TextView updateContent = window.findViewById(R.id.update_content);
                                    final Markwon markwon = Markwon.create(MainActivity.this);
                                    markwon.setMarkdown(updateContent, getRetJson.getString("body"));

                                    Button updateCancel = window.findViewById(R.id.update_ignore);
                                    updateCancel.setOnClickListener(v -> alertDialog.cancel());

                                    JSONArray jsonArray = new JSONArray(getRetJson.getString("assets"));
                                    JSONObject jsonObject = jsonArray.getJSONObject(0);
                                    String download_url = jsonObject.getString("browser_download_url");
                                    mUpdateFilename = jsonObject.getString("name");

                                    Button updateAgree = window.findViewById(R.id.update_agree);
                                    updateAgree.setOnClickListener(v -> {
                                        alertDialog.cancel();
                                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_downloading));
                                        downloadNewVersion(download_url);
                                    });
                                }
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve json");
                        }
                    });
                }
            }
        });
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(GoUtils.getAppName(this));
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");

        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists()) {
            if (!file.delete()) {
                return;
            }
        }
        request.setDestinationUri(Uri.fromFile(file));

        mDownloadId = mDownloadManager.enqueue(request);
    }

    private void installNewVersion() {
        Intent install = new Intent(Intent.ACTION_VIEW);
        Uri downloadFileUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (downloadFileUri != null) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(MainActivity.this, file), "application/vnd.android.package-archive");
            startActivity(install);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            intent.addCategory("android.intent.category.DEFAULT");
            startActivity(intent);
        }
    }
}
