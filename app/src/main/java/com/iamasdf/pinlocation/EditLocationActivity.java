package com.iamasdf.pinlocation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.model.LatLng;
import com.elvishew.xlog.XLog;
import com.iamasdf.pinlocation.database.DataBaseHistoryLocation;
import com.iamasdf.pinlocation.utils.GoUtils;
import com.iamasdf.pinlocation.utils.MapUtils;

public class EditLocationActivity extends BaseActivity {
    public static final String EXTRA_ID = "EXTRA_ID";

    private SQLiteDatabase mHistoryDB;
    private long mId = -1;

    private EditText mNameInput;
    private EditText mAddressInput;
    private EditText mLatInput;
    private EditText mLngInput;
    private EditText mAltInput;

    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private final BitmapDescriptor mIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary, getTheme()));
        setContentView(R.layout.activity_location_edit);

        mId = getIntent().getLongExtra(EXTRA_ID, -1);

        DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
        mHistoryDB = dbLocation.getWritableDatabase();

        mNameInput = findViewById(R.id.edit_name);
        mAddressInput = findViewById(R.id.edit_address);
        mLatInput = findViewById(R.id.edit_lat);
        mLngInput = findViewById(R.id.edit_lng);
        mAltInput = findViewById(R.id.edit_alt);

        mMapView = findViewById(R.id.edit_map_view);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);

        findViewById(R.id.edit_back).setOnClickListener(v -> finish());
        findViewById(R.id.edit_save).setOnClickListener(v -> saveAndFinish());

        if (!loadRecord()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.edit_location_missing));
            finish();
            return;
        }

        // 点击地图移动标记并更新经纬度
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                applyPoint(point);
            }

            @Override
            public void onMapPoiClick(MapPoi poi) {
                applyPoint(poi.getPosition());
            }
        });
    }

    private void applyPoint(LatLng point) {
        mLatInput.setText(String.valueOf(point.latitude));
        mLngInput.setText(String.valueOf(point.longitude));
        showMarker(point);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        if (mIndicator != null) {
            mIndicator.recycle();
        }
        if (mHistoryDB != null) {
            mHistoryDB.close();
        }
        super.onDestroy();
    }

    private boolean loadRecord() {
        if (mId <= 0) {
            return false;
        }
        try {
            Cursor cursor = mHistoryDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(mId)},
                    null, null, null);
            try {
                if (!cursor.moveToFirst()) {
                    return false;
                }
                String name = cursor.getString(1);
                String lng = cursor.getString(5);
                String lat = cursor.getString(6);
                String address = cursor.getString(7);
                String altitude = cursor.getString(9);

                mNameInput.setText(name == null ? "" : name);
                mAddressInput.setText(address == null ? (name == null ? "" : name) : address);
                mLatInput.setText(lat == null ? "" : lat);
                mLngInput.setText(lng == null ? "" : lng);
                mAltInput.setText(altitude == null ? "0.0" : altitude);

                double dLat = Double.parseDouble(lat);
                double dLng = Double.parseDouble(lng);
                LatLng latLng = new LatLng(dLat, dLng);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 17.0f));
                showMarker(latLng);
                return true;
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            XLog.e("EDIT_LOCATION: loadRecord error");
            return false;
        }
    }

    private void showMarker(LatLng latLng) {
        mBaiduMap.clear();
        mBaiduMap.addOverlay(new MarkerOptions().position(latLng).icon(mIndicator));
    }

    private void saveAndFinish() {
        double lat;
        double lng;
        double alt = 0.0;

        try {
            lat = Double.parseDouble(mLatInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_latitude));
            return;
        }
        try {
            lng = Double.parseDouble(mLngInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_longitude));
            return;
        }
        if (lat > 90.0 || lat < -90.0) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_latitude));
            return;
        }
        if (lng > 180.0 || lng < -180.0) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_longitude));
            return;
        }
        try {
            String altStr = mAltInput.getText().toString().trim();
            if (!altStr.isEmpty()) {
                alt = Double.parseDouble(altStr);
            }
        } catch (NumberFormatException e) {
            alt = 0.0;
        }

        String name = mNameInput.getText().toString().trim();
        String address = mAddressInput.getText().toString().trim();
        if (name.isEmpty()) {
            name = address.isEmpty()
                    ? getResources().getString(R.string.history_location_default_name)
                    : address;
        }

        try {
            double[] latLng = MapUtils.bd2wgs(lng, lat);
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_ADDRESS, address);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_ALTITUDE, Double.toString(alt));

            mHistoryDB.update(DataBaseHistoryLocation.TABLE_NAME, contentValues,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(mId)});
            GoUtils.DisplayToast(this, getResources().getString(R.string.save_ok));
        } catch (Exception e) {
            XLog.e("EDIT_LOCATION: save error");
            GoUtils.DisplayToast(this, getResources().getString(R.string.save_failed));
            return;
        }

        finish();
    }
}
