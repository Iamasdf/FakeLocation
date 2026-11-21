package com.iamasdf.pinlocation.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

public class DataBaseHistoryLocation extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "HistoryLocation";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_LOCATION = "DB_COLUMN_LOCATION";
    public static final String DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84";
    public static final String DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";
    public static final String DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM";
    public static final String DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM";
    public static final String DB_COLUMN_ADDRESS = "DB_COLUMN_ADDRESS";
    public static final String DB_COLUMN_PINNED = "DB_COLUMN_PINNED";
    public static final String DB_COLUMN_ALTITUDE = "DB_COLUMN_ALTITUDE";

    private static final int DB_VERSION = 2;
    private static final String DB_NAME = "HistoryLocation.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_LOCATION TEXT, " +
            "DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, " +
            "DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL, " +
            "DB_COLUMN_ADDRESS TEXT, DB_COLUMN_PINNED INTEGER NOT NULL DEFAULT 0, DB_COLUMN_ALTITUDE TEXT)";

    public DataBaseHistoryLocation(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        // 只做增量升级，保留既有的历史记录（历史不过期）
        if (oldVersion < 2) {
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + DB_COLUMN_ADDRESS + " TEXT");
            } catch (Exception e) {
                XLog.e("DATABASE: add address column error");
            }
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + DB_COLUMN_PINNED + " INTEGER NOT NULL DEFAULT 0");
            } catch (Exception e) {
                XLog.e("DATABASE: add pinned column error");
            }
            try {
                sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + DB_COLUMN_ALTITUDE + " TEXT");
            } catch (Exception e) {
                XLog.e("DATABASE: add altitude column error");
            }
        }
    }

    // 保存选择的位置，返回新记录的行号（失败返回 -1）；同一坐标重复保存时保留置顶状态与海拔
    public static long saveHistoryLocation(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            String longitudeWgs84 = contentValues.getAsString(DB_COLUMN_LONGITUDE_WGS84);
            String latitudeWgs84 = contentValues.getAsString(DB_COLUMN_LATITUDE_WGS84);

            int pinned = 0;
            String altitude = null;
            Cursor cursor = sqLiteDatabase.query(TABLE_NAME,
                    new String[]{DB_COLUMN_PINNED, DB_COLUMN_ALTITUDE},
                    DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " + DB_COLUMN_LATITUDE_WGS84 + " = ?",
                    new String[]{longitudeWgs84, latitudeWgs84}, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    pinned = cursor.getInt(0);
                    altitude = cursor.getString(1);
                }
            } finally {
                cursor.close();
            }

            // 先删除原来的记录，再插入新记录
            sqLiteDatabase.delete(TABLE_NAME,
                            DB_COLUMN_LONGITUDE_WGS84 + " = ? AND " +
                            DB_COLUMN_LATITUDE_WGS84 + " = ?",
                    new String[] {longitudeWgs84, latitudeWgs84});

            if (!contentValues.containsKey(DB_COLUMN_PINNED)) {
                contentValues.put(DB_COLUMN_PINNED, pinned);
            }
            if (!contentValues.containsKey(DB_COLUMN_ALTITUDE) && altitude != null) {
                contentValues.put(DB_COLUMN_ALTITUDE, altitude);
            }

            return sqLiteDatabase.insert(TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            XLog.e("DATABASE: insert error");
            return -1;
        }
    }

    // 修改历史记录名称
    public static void updateHistoryLocation(SQLiteDatabase sqLiteDatabase, String locID, String location) {
        try{
            ContentValues contentValues = new ContentValues();
            contentValues.put(DB_COLUMN_LOCATION, location);
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?", new String[]{locID});
        } catch (Exception e){
            XLog.e("DATABASE: update error");
        }
    }
}
