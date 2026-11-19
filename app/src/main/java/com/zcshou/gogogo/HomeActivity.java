package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.elvishew.xlog.XLog;
import com.google.android.material.navigation.NavigationView;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;
import com.zcshou.utils.ShareUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeActivity extends BaseActivity {
    /** 当前目标位置对应的历史记录行号 */
    public static final String KEY_TARGET_ID = "KEY_TARGET_ID";

    private static final String PREF_JOYSTICK_ON = "home_joystick_on";
    private static final String PREF_RUNNING = "home_mock_running";

    private static final int MENU_HISTORY = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_DEV = 3;
    private static final int MENU_UPDATE = 4;
    private static final int MENU_FEEDBACK = 5;
    private static final int MENU_CONTACT = 6;

    private SharedPreferences mPrefs;
    private SQLiteDatabase mHistoryDB;

    private TextView mTargetAddr;
    private TextView mTargetCity;
    private TextView mTargetCoord;
    private TextView mStartBtn;
    private SwitchCompat mJoySwitch;
    private LinearLayout mHistoryContainer;
    private LinearLayout mFilterWrap;
    private EditText mFilterInput;

    private boolean mRunning = false;
    private boolean mBound = false;
    private boolean mUpdatingSwitch = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private long mTargetId = -1;
    private List<HistoryRow> mRows = new ArrayList<>();

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceBinder = (ServiceGo.ServiceGoBinder) service;
            mBound = true;
            applyCurrentPosition();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mServiceBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* 与 HistoryActivity 一致：除欢迎页外手动填充状态栏颜色 */
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary, getTheme()));

        setContentView(R.layout.activity_home);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
        mHistoryDB = dbLocation.getWritableDatabase();

        mTargetAddr = findViewById(R.id.home_target_addr);
        mTargetCity = findViewById(R.id.home_target_city);
        mTargetCoord = findViewById(R.id.home_target_coord);
        mStartBtn = findViewById(R.id.home_start_btn);
        mJoySwitch = findViewById(R.id.home_joy_switch);
        mHistoryContainer = findViewById(R.id.home_history_container);
        mFilterWrap = findViewById(R.id.home_filter_wrap);
        mFilterInput = findViewById(R.id.home_filter_input);

        DrawerLayout drawer = findViewById(R.id.drawer_layout_home);
        findViewById(R.id.home_menu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.home_more).setOnClickListener(this::showMoreMenu);
        findViewById(R.id.home_add).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, MainActivity.class)));
        findViewById(R.id.home_history_search).setOnClickListener(v -> toggleFilter());

        initNavigationView(findViewById(R.id.nav_view_home), drawer);

        mStartBtn.setOnClickListener(v -> toggleSimulation());

        mJoySwitch.setChecked(false);
        mPrefs.edit().putBoolean(PREF_JOYSTICK_ON, false).apply();
        mJoySwitch.setVisibility(View.GONE);
        mJoySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mUpdatingSwitch) {
                return;
            }
            mPrefs.edit().putBoolean(PREF_JOYSTICK_ON, isChecked).apply();
            if (!mRunning) {
                return;
            }
            if (isChecked && !Settings.canDrawOverlays(this)) {
                GoUtils.showEnableFloatWindowDialog(this);
                mUpdatingSwitch = true;
                mJoySwitch.setChecked(false);
                mUpdatingSwitch = false;
                mPrefs.edit().putBoolean(PREF_JOYSTICK_ON, false).apply();
                return;
            }
            if (mBound && mServiceBinder != null) {
                if (isChecked) {
                    mServiceBinder.showJoyStick();
                } else {
                    mServiceBinder.hideJoyStick();
                }
            }
        });

        mFilterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderHistory(s.toString());
            }
        });

        // 恢复运行状态：若上次模拟仍在运行，重新绑定服务
        mRunning = mPrefs.getBoolean(PREF_RUNNING, false);
        if (mRunning) {
            try {
                bindService(new Intent(this, ServiceGo.class), mConnection, BIND_AUTO_CREATE);
            } catch (Exception e) {
                XLog.e("HOME: restore running error");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从选择地址页返回时刷新目标位置与历史位置
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception e) {
                XLog.e("HOME: unbind error");
            }
            mBound = false;
        }
        if (mHistoryDB != null) {
            mHistoryDB.close();
        }
        super.onDestroy();
    }

    /*============================== 菜单 ==============================*/
    private void initNavigationView(NavigationView navView, DrawerLayout drawer) {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                handleMenu(MENU_HISTORY);
            } else if (id == R.id.nav_settings) {
                handleMenu(MENU_SETTINGS);
            } else if (id == R.id.nav_dev) {
                handleMenu(MENU_DEV);
            } else if (id == R.id.nav_update) {
                handleMenu(MENU_UPDATE);
            } else if (id == R.id.nav_feedback) {
                handleMenu(MENU_FEEDBACK);
            } else if (id == R.id.nav_contact) {
                handleMenu(MENU_CONTACT);
            }
            drawer.closeDrawer(GravityCompat.START);
            return true;
        });

        View headerView = navView.getHeaderView(0);
        TextView appVersion = headerView.findViewById(R.id.app_version);
        appVersion.setText(GoUtils.getVersionName(this));
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_HISTORY, 0, R.string.nav_menu_history);
        popup.getMenu().add(0, MENU_SETTINGS, 1, R.string.nav_menu_settings);
        popup.getMenu().add(0, MENU_DEV, 2, R.string.nav_menu_dev);
        popup.getMenu().add(0, MENU_UPDATE, 3, R.string.nav_menu_upgrade);
        popup.getMenu().add(0, MENU_FEEDBACK, 4, R.string.nav_menu_feedback);
        popup.getMenu().add(0, MENU_CONTACT, 5, R.string.nav_menu_contact);
        popup.setOnMenuItemClickListener(item -> {
            handleMenu(item.getItemId());
            return true;
        });
        popup.show();
    }

    private void handleMenu(int menuId) {
        if (menuId == MENU_HISTORY) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (menuId == MENU_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (menuId == MENU_DEV) {
            if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
            } else {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                }
            }
        } else if (menuId == MENU_UPDATE) {
            // 简化处理：直接打开发布页（原 MainActivity 内的应用内更新流程不再作为主入口）
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zcshou/gogogo/releases/latest")));
            } catch (Exception e) {
                XLog.e("HOME: open release page error");
            }
        } else if (menuId == MENU_FEEDBACK) {
            File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
            ShareUtils.shareFile(this, file, getResources().getString(R.string.nav_menu_feedback));
        } else if (menuId == MENU_CONTACT) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/itexp/gogogo/issues")));
            } catch (Exception e) {
                XLog.e("HOME: open contact page error");
            }
        }
    }

    /*============================== 目标位置 / 历史位置 ==============================*/
    private static class HistoryRow {
        long id;
        String name;
        String lngWgs;
        String latWgs;
        String lngCustom;
        String latCustom;
        String address;
        int pinned;
        String altitude;
        long timestamp;
    }

    private List<HistoryRow> queryRows() {
        List<HistoryRow> rows = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = mHistoryDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null,
                    DataBaseHistoryLocation.DB_COLUMN_PINNED + " DESC, " +
                            DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null);
            while (cursor.moveToNext()) {
                HistoryRow row = new HistoryRow();
                row.id = cursor.getLong(0);
                row.name = cursor.getString(1);
                row.lngWgs = cursor.getString(2);
                row.latWgs = cursor.getString(3);
                row.timestamp = cursor.getLong(4);
                row.lngCustom = cursor.getString(5);
                row.latCustom = cursor.getString(6);
                row.address = cursor.getString(7);
                row.pinned = cursor.getInt(8);
                row.altitude = cursor.getString(9);
                rows.add(row);
            }
        } catch (Exception e) {
            XLog.e("HOME: queryRows error");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rows;
    }

    private HistoryRow findRow(long id) {
        for (HistoryRow row : mRows) {
            if (row.id == id) {
                return row;
            }
        }
        return null;
    }

    private void refreshAll() {
        mRows = queryRows();
        mTargetId = mPrefs.getLong(KEY_TARGET_ID, -1);
        if (findRow(mTargetId) == null && !mRows.isEmpty()) {
            // 默认使用最近一次记录
            mTargetId = mRows.get(0).id;
            mPrefs.edit().putLong(KEY_TARGET_ID, mTargetId).apply();
        }
        renderTarget();
        renderHistory(mFilterInput.getText().toString());

        // 恢复运行状态时：确保服务坐标与悬浮摇杆状态与首页一致（不受回调时序影响）
        if (mRunning && mBound) {
            applyCurrentPosition();
        }
    }

    private void renderTarget() {
        HistoryRow row = findRow(mTargetId);
        if (row == null) {
            mTargetAddr.setText(R.string.home_no_target);
            mTargetCity.setText(R.string.home_pick_hint);
            mTargetCoord.setText("—");
        } else {
            mTargetAddr.setText(displayName(row));
            String city = parseCity(row.name);
            mTargetCity.setText(TextUtils.isEmpty(city) ? "中国" : city + " · 中国");
            mTargetCoord.setText(getResources().getString(R.string.home_coord_prefix) + row.lngWgs + "," + row.latWgs);
        }
        updateStartButton();
    }

    private void renderHistory(String filter) {
        mHistoryContainer.removeAllViews();

        String f = filter == null ? "" : filter.trim();
        int shown = 0;
        for (HistoryRow row : mRows) {
            if (!TextUtils.isEmpty(f) && (row.name == null || !row.name.contains(f))) {
                continue;
            }
            View item = getLayoutInflater().inflate(R.layout.home_history_item, mHistoryContainer, false);
            TextView name = item.findViewById(R.id.home_item_name);
            TextView coord = item.findViewById(R.id.home_item_coord);
            name.setText(displayName(row));
            coord.setText(getResources().getString(R.string.home_coord_prefix) + row.lngWgs + "," + row.latWgs);

            final long id = row.id;
            item.setOnClickListener(v -> {
                mPrefs.edit().putLong(KEY_TARGET_ID, id).apply();
                mTargetId = id;
                renderTarget();
                GoUtils.DisplayToast(HomeActivity.this, getResources().getString(R.string.home_selected));
            });
            item.setOnLongClickListener(v -> {
                showHistoryItemMenu(v, row);
                return true;
            });
            mHistoryContainer.addView(item);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(mRows.isEmpty() ? R.string.home_no_history : R.string.app_search_null);
            empty.setTextColor(getResources().getColor(R.color.home_muted, getTheme()));
            empty.setTextSize(14);
            int p = (int) (16 * getResources().getDisplayMetrics().density);
            empty.setPadding(p, p, p, p);
            mHistoryContainer.addView(empty);
        }
    }

    /*============================== 长按菜单：置顶 / 编辑 / 删除 ==============================*/
    private void showHistoryItemMenu(View anchor, HistoryRow row) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, row.pinned == 1 ? R.string.menu_unpin : R.string.menu_pin);
        popup.getMenu().add(0, 2, 1, R.string.menu_edit);
        popup.getMenu().add(0, 3, 2, R.string.menu_delete);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                setPinned(row, row.pinned != 1);
            } else if (id == 2) {
                Intent intent = new Intent(this, EditLocationActivity.class);
                intent.putExtra(EditLocationActivity.EXTRA_ID, row.id);
                startActivity(intent);
            } else if (id == 3) {
                confirmDelete(row);
            }
            return true;
        });
        popup.show();
    }

    private void setPinned(HistoryRow row, boolean pinned) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_PINNED, pinned ? 1 : 0);
            mHistoryDB.update(DataBaseHistoryLocation.TABLE_NAME, contentValues,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(row.id)});
        } catch (Exception e) {
            XLog.e("HOME: setPinned error");
        }
        refreshAll();
    }

    private void confirmDelete(HistoryRow row) {
        new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage(R.string.delete_confirm)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        mHistoryDB.delete(DataBaseHistoryLocation.TABLE_NAME,
                                DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(row.id)});
                    } catch (Exception e) {
                        XLog.e("HOME: delete error");
                    }
                    refreshAll();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String displayName(HistoryRow row) {
        String name = row.name;
        // 逆地理编码有时会返回 "null"/"nullnull" 之类的占位文本
        if (TextUtils.isEmpty(name) || "null".equalsIgnoreCase(name) || "nullnull".equalsIgnoreCase(name)) {
            return getResources().getString(R.string.history_location_default_name);
        }
        return name;
    }

    /** 从地址名称中提取“xx市”，失败返回空串 */
    private String parseCity(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile("(?:省|自治区|特别行政区)[^省市]{0,8}?([\\u4e00-\\u9fa5]{1,6}市)").matcher(name);
            if (matcher.find()) {
                return matcher.group(1);
            }
            matcher = Pattern.compile("([\\u4e00-\\u9fa5]{1,6}市)").matcher(name);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            XLog.e("HOME: parseCity error");
        }
        return "";
    }

    private void toggleFilter() {
        boolean show = mFilterWrap.getVisibility() != View.VISIBLE;
        mFilterWrap.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mFilterInput.requestFocus();
        } else {
            mFilterInput.setText("");
            renderHistory("");
        }
    }

    /*============================== 启动 / 停止模拟 ==============================*/
    private void toggleSimulation() {
        if (!mRunning) {
            HistoryRow row = findRow(mTargetId);
            if (row == null) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.home_need_target));
                return;
            }
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                return;
            }
            if (mJoySwitch.isChecked() && !Settings.canDrawOverlays(this)) {
                GoUtils.showEnableFloatWindowDialog(this);
                return;
            }
            try {
                double lng = Double.parseDouble(row.lngWgs);
                double lat = Double.parseDouble(row.latWgs);
                Intent serviceGoIntent = new Intent(this, ServiceGo.class);
                serviceGoIntent.putExtra(MainActivity.LNG_MSG_ID, lng);
                serviceGoIntent.putExtra(MainActivity.LAT_MSG_ID, lat);
                serviceGoIntent.putExtra(MainActivity.ALT_MSG_ID, getRowAltitude(row));

                startForegroundService(serviceGoIntent);
                bindService(new Intent(this, ServiceGo.class), mConnection, BIND_AUTO_CREATE);
                mRunning = true;
                mPrefs.edit().putBoolean(PREF_RUNNING, true).apply();
                GoUtils.DisplayToast(this, getResources().getString(R.string.home_started));
            } catch (NumberFormatException e) {
                XLog.e("HOME: target coord error");
                GoUtils.DisplayToast(this, getResources().getString(R.string.home_need_target));
                return;
            }
        } else {
            if (mBound) {
                try {
                    unbindService(mConnection);
                } catch (Exception e) {
                    XLog.e("HOME: unbind error");
                }
                mBound = false;
            }
            mServiceBinder = null;
            stopService(new Intent(this, ServiceGo.class));
            mRunning = false;
            mPrefs.edit().putBoolean(PREF_RUNNING, false).apply();
            GoUtils.DisplayToast(this, getResources().getString(R.string.home_stopped));
        }
        updateStartButton();
    }

    private void updateStartButton() {
        mStartBtn.setText(mRunning ? R.string.home_stop : R.string.home_start);
    }

    /** 服务连接后同步当前坐标；摇杆开关开启时显示悬浮摇杆 */
    private void applyCurrentPosition() {
        if (!mRunning || mServiceBinder == null) {
            return;
        }
        HistoryRow row = findRow(mTargetId);
        if (row == null) {
            return;
        }
        try {
            double lng = Double.parseDouble(row.lngWgs);
            double lat = Double.parseDouble(row.latWgs);
            mServiceBinder.setPosition(lng, lat, getRowAltitude(row));
        } catch (NumberFormatException e) {
            XLog.e("HOME: applyCurrentPosition error");
            return;
        }
        if (mJoySwitch.isChecked()) {
            mServiceBinder.showJoyStick();
        }
    }

    private double getAltitude() {
        try {
            return Double.parseDouble(mPrefs.getString("setting_altitude", "55.0"));
        } catch (NumberFormatException e) {
            return 55.0;
        }
    }

    /** 优先使用记录自身的海拔，未设置时回退到全局设置 */
    private double getRowAltitude(HistoryRow row) {
        if (row != null && !TextUtils.isEmpty(row.altitude)) {
            try {
                return Double.parseDouble(row.altitude);
            } catch (NumberFormatException e) {
                XLog.e("HOME: altitude parse error");
            }
        }
        return getAltitude();
    }
}
