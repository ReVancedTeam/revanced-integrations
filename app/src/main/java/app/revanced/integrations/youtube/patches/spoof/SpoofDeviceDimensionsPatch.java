package app.revanced.integrations.youtube.patches.spoof;

import app.revanced.integrations.youtube.settings.Settings;

public class SpoofDeviceDimensionsPatch {
    private static final boolean SPOOF = Settings.SPOOF_DEVICE_DIMENSIONS.getBoolean();
    public static int getMinHeightOrWidth(int minHeightOrWidth) {
        return SPOOF ? 64 : minHeightOrWidth;
    }

    public static int getMaxHeightOrWidth(int maxHeightOrWidth) {
        return SPOOF ? 4096 : maxHeightOrWidth;
    }
}