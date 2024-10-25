package app.revanced.integrations.youtube.patches.playback.speed;

import static app.revanced.integrations.shared.StringRef.str;

import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.youtube.patches.VideoInformation;
import app.revanced.integrations.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class RememberPlaybackSpeedPatch {

    private static final long TOAST_DELAY_MILLISECONDS = 750;

    private static long lastTimeSpeedChanged;

    /**
     * Injection point.
     */
    public static void newVideoStarted(VideoInformation.PlaybackController ignoredPlayerController) {
        Logger.printDebug(() -> "newVideoStarted");
        VideoInformation.overridePlaybackSpeed(Settings.PLAYBACK_SPEED_DEFAULT.get());
    }

    /**
     * Injection point.
     * Called when user selects a playback speed.
     *
     * @param playbackSpeed The playback speed the user selected
     */
    public static void userSelectedPlaybackSpeed(float playbackSpeed) {
        if (Settings.REMEMBER_PLAYBACK_SPEED_LAST_SELECTED.get()) {
            // With the 0.05x menu, if the speed is set by integrations to higher than 2.0x
            // then the menu will allow increasing without bounds but the max speed is
            // still capped to under 8.0x.
            playbackSpeed = Math.min(playbackSpeed, CustomPlaybackSpeedPatch.MAXIMUM_PLAYBACK_SPEED - 0.05f);

            // Prevent toast spamming if using the 0.05x adjustments.
            // Show exactly one toast after the user stops interacting with the speed menu.
            final long now = System.currentTimeMillis();
            lastTimeSpeedChanged = now;

            final float finalPlaybackSpeed = playbackSpeed;
            Utils.runOnMainThreadDelayed(() -> {
                if (lastTimeSpeedChanged != now) {
                    // The user made additional speed adjustments and this call is outdated.
                    return;
                }

                if (Settings.PLAYBACK_SPEED_DEFAULT.get() == finalPlaybackSpeed) {
                    // User changed to a different speed and immediately changed back.
                    return;
                }
                Settings.PLAYBACK_SPEED_DEFAULT.save(finalPlaybackSpeed);

                Utils.showToastLong(str("revanced_remember_playback_speed_toast", (finalPlaybackSpeed + "x")));
            }, TOAST_DELAY_MILLISECONDS);
        }
    }

    /**
     * Injection point.
     * Overrides the video speed.  Called after video loads, and immediately after user selects a different playback speed
     */
    public static float getPlaybackSpeedOverride() {
        return VideoInformation.getPlaybackSpeed();
    }

}
