package com.zcshou.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

public final class LocationHelper {
    private LocationHelper() {
    }

    public static Location getLastKnownLocation(Context context) {
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                return null;
            }
            Location best = null;
            String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
            for (String provider : providers) {
                try {
                    if (!manager.isProviderEnabled(provider)) {
                        continue;
                    }
                    Location location = manager.getLastKnownLocation(provider);
                    if (location != null && (best == null || location.getTime() > best.getTime())) {
                        best = location;
                    }
                } catch (SecurityException ignored) {
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }
}
