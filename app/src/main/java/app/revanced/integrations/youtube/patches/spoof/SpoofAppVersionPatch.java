package app.revanced.integrations.youtube.patches.spoof;

import app.revanced.integrations.youtube.settings.Setting;

public class SpoofAppVersionPatch {

    private static final boolean SPOOF_APP_VERSION_ENABLED = Setting.SPOOF_APP_VERSION.getBoolean();
    private static final String SPOOF_APP_VERSION_TARGET = Setting.SPOOF_APP_VERSION_TARGET.getString();

    /**
     * Injection point
     */
    public static String getYouTubeVersionOverride(String version) {
        if (SPOOF_APP_VERSION_ENABLED) return SPOOF_APP_VERSION_TARGET;
        return version;
    }

    public static boolean isSpoofingToEqualOrLessThan(String version) {
        return SPOOF_APP_VERSION_ENABLED && SPOOF_APP_VERSION_TARGET.compareTo(version) <= 0;
    }

}
