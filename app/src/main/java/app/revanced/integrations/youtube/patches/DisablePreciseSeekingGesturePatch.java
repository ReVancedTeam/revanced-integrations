package app.revanced.integrations.youtube.patches;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import app.revanced.integrations.youtube.settings.Setting;

public final class DisablePreciseSeekingGesturePatch {
    /**
     * Disables the gesture that is used to seek precisely.
     * @param tracker The velocity tracker that is used to determine the gesture.
     * @param event The motion event that is used to determine the gesture.
     */
    public static void disableGesture(VelocityTracker tracker, MotionEvent event) {
        if (Setting.DISABLE_PRECISE_SEEKING_GESTURE.getBoolean()) return;

        tracker.addMovement(event);
    }
}
