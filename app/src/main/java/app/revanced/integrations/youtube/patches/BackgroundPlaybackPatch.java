package app.revanced.integrations.youtube.patches;

import app.revanced.integrations.youtube.settings.Settings;
import app.revanced.integrations.youtube.shared.PlayerType;

@SuppressWarnings("unused")
public class BackgroundPlaybackPatch {

    /**
     * Injection point.
     */
    public static boolean isBackgroundPlaybackAllowed() {
        // Steps to verify most edge cases:
        // 1. Open a regular video
        // 2. Minimize app (PIP should appear)
        // 3. Reopen app
        // 4. Open a Short (without closing the regular video)
        //    (try opening both Shorts in the video player suggestions AND Shorts from the home feed)
        // 5. Minimize the app (PIP should not appear)
        // 6. Reopen app
        // 7. Close the Short
        // 8. Resume playing the regular video
        // 9. Minimize the app (PIP should appear)
        if (VideoInformation.lastVideoIdIsShort()) {
            return !Settings.DISABLE_BACKGROUND_SHORTS.get();
        }

        return true;
    }

    /**
     * Injection point.
     */
    public static boolean overrideBackgroundPlaybackAvailable() {
        // This could be done entirely in the patch,
        // but having a unique method to search for makes manually inspecting the patched apk much easier.
        return true;
    }

}
