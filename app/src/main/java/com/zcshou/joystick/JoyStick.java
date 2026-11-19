package com.zcshou.joystick;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * OSM MVP placeholder. The floating joystick overlay is deferred until the map
 * migration is complete; this class keeps the ServiceGo API stable.
 */
public class JoyStick extends View {
    public interface JoyStickClickListener {
        void onMoveInfo(double speed, double disLng, double disLat, double angle);

        void onPositionInfo(double lng, double lat, double alt);
    }

    private JoyStickClickListener listener;

    public JoyStick(Context context) {
        super(context);
    }

    public JoyStick(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public JoyStick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setListener(JoyStickClickListener listener) {
        this.listener = listener;
    }

    public void setCurrentPosition(double lng, double lat, double alt) {
    }

    public void show() {
    }

    public void hide() {
    }

    public void destroy() {
    }
}
