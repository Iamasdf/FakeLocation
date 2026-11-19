package com.zcshou.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.zcshou.gogogo.R;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class OsmMapController {
    public interface OnPointSelectedListener {
        void onPointSelected(GeoPoint point, boolean longPress);
    }

    private final Context context;
    private final MapView mapView;
    private Marker marker;

    public OsmMapController(Context context, MapView mapView, OnPointSelectedListener listener) {
        this.context = context.getApplicationContext();
        this.mapView = mapView;

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
        mapView.getController().setZoom(17.0);

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                if (listener != null) {
                    listener.onPointSelected(point, false);
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                if (listener != null) {
                    listener.onPointSelected(point, true);
                }
                return true;
            }
        });
        mapView.getOverlays().add(eventsOverlay);
        mapView.getOverlays().add(new CopyrightOverlay(this.context));
    }

    public MapView getMapView() {
        return mapView;
    }

    public void moveTo(GeoPoint point, double zoom) {
        if (point == null) {
            return;
        }
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(point);
        mapView.invalidate();
    }

    public void showMarker(GeoPoint point, String title, String snippet) {
        if (point == null) {
            return;
        }
        clearMarker();
        Marker newMarker = new Marker(mapView);
        newMarker.setPosition(point);
        newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.icon_gcoding);
        if (icon != null) {
            newMarker.setIcon(icon);
        }
        if (title != null && !title.isEmpty()) {
            newMarker.setTitle(title);
        }
        if (snippet != null && !snippet.isEmpty()) {
            newMarker.setSnippet(snippet);
        }
        mapView.getOverlays().add(newMarker);
        marker = newMarker;
        mapView.invalidate();
    }

    public void clearMarker() {
        if (marker != null) {
            mapView.getOverlays().remove(marker);
            marker = null;
            mapView.invalidate();
        }
    }

    public void zoomIn() {
        mapView.getController().zoomIn();
        mapView.invalidate();
    }

    public void zoomOut() {
        mapView.getController().zoomOut();
        mapView.invalidate();
    }
}
