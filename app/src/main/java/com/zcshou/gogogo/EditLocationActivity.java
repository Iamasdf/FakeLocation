package com.zcshou.gogogo;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.elvishew.xlog.XLog;
import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.MapUtils;
import com.zcshou.utils.NominatimGeocoder;
import com.zcshou.utils.OsmMapController;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Locale;

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
    private OsmMapController mMapController;
    private NominatimGeocoder mGeocoder;

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
        mGeocoder = new NominatimGeocoder(this);
        mMapController = new OsmMapController(this, mMapView, (point, longPress) -> {
            applyPoint(point);
            if (longPress) {
                requestAddress(point);
            }
        });

        findViewById(R.id.edit_back).setOnClickListener(v -> finish());
        findViewById(R.id.edit_save).setOnClickListener(v -> saveAndFinish());

        if (!loadRecord()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.edit_location_missing));
            finish();
        }
    }

    private void applyPoint(GeoPoint point) {
        mLatInput.setText(String.valueOf(point.getLatitude()));
        mLngInput.setText(String.valueOf(point.getLongitude()));
        mMapController.showMarker(point, null, formatCoordinate(point));
        mMapController.moveTo(point, 17.0);
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
                mAddressInput.setText(address);
            }

            @Override
            public void onError(String message) {
                XLog.i("Nominatim reverse failed: " + message);
            }
        });
    }

    private static String formatCoordinate(GeoPoint point) {
        return String.format(Locale.US, "%.6f, %.6f", point.getLatitude(), point.getLongitude());
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
        if (mGeocoder != null) {
            mGeocoder.shutdown();
        }
        mMapView.onDetach();
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
                String lng = cursor.getString(2);
                String lat = cursor.getString(3);
                String address = cursor.getString(7);
                String altitude = cursor.getString(9);

                if (TextUtils.isEmpty(lng) || TextUtils.isEmpty(lat)) {
                    String legacyLng = cursor.getString(5);
                    String legacyLat = cursor.getString(6);
                    if (!TextUtils.isEmpty(legacyLng) && !TextUtils.isEmpty(legacyLat)) {
                        double[] wgs = MapUtils.bd2wgs(
                                Double.parseDouble(legacyLng), Double.parseDouble(legacyLat));
                        lng = String.valueOf(wgs[0]);
                        lat = String.valueOf(wgs[1]);
                    }
                }

                mNameInput.setText(name == null ? "" : name);
                mAddressInput.setText(address == null ? (name == null ? "" : name) : address);
                mLatInput.setText(lat == null ? "" : lat);
                mLngInput.setText(lng == null ? "" : lng);
                mAltInput.setText(altitude == null ? "0.0" : altitude);

                double dLat = Double.parseDouble(lat);
                double dLng = Double.parseDouble(lng);
                GeoPoint point = new GeoPoint(dLat, dLng);
                mMapController.moveTo(point, 17.0);
                mMapController.showMarker(point, name, formatCoordinate(point));
                return true;
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            XLog.e("EDIT_LOCATION: loadRecord error");
            return false;
        }
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
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_ADDRESS, address);
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(lng));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(lat));
            // Keep legacy columns in sync for older readers.
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(lng));
            contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(lat));
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
