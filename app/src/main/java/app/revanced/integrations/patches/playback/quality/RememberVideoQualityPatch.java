package app.revanced.integrations.patches.playback.quality;

import static app.revanced.integrations.utils.ReVancedUtils.NetworkType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.utils.ReVancedUtils;

public class RememberVideoQualityPatch {
    private static final int AUTOMATIC_VIDEO_QUALITY_VALUE = -2;
    private static final SettingsEnum wifiQualitySetting = SettingsEnum.VIDEO_QUALITY_DEFAULT_WIFI;
    private static final SettingsEnum mobileQualitySetting = SettingsEnum.VIDEO_QUALITY_DEFAULT_MOBILE;

    private static boolean newVideo;
    /**
     * If the user selected a new resolution from the flyout menu,
     * and {@link SettingsEnum#VIDEO_QUALITY_REMEMBER_LAST_SELECTED} is enabled.
     */
    private static boolean userChangedDefaultQuality;
    /**
     * Index of the video quality chosen by the user from the flyout menu.
     */
    private static int userSelectedQualityIndex;

    private static void changeDefaultQuality(int defaultQuality) {
        NetworkType networkType = ReVancedUtils.getNetworkType();
        if (networkType == NetworkType.NONE) {
            ReVancedUtils.showToastShort("No internet connection");
            return;
        }
        String networkTypeMessage;
        if (networkType == NetworkType.MOBILE) {
            mobileQualitySetting.saveValue(defaultQuality);
            networkTypeMessage = "mobile";
        } else {
            wifiQualitySetting.saveValue(defaultQuality);
            networkTypeMessage = "Wi-Fi";
        }
        ReVancedUtils.showToastShort("Changed default " + networkTypeMessage
                + " quality to: " + defaultQuality +"p");
    }

    /**
     * Injection point.
     *
     * @param qualities Video qualities available, ordered from largest to smallest, with index 0 being the 'automatic' value of -2
     * @param originalQualityIndex quality index to use, as chosen by YouTube
     */
    public static int setVideoQuality(Object[] qualities, final int originalQualityIndex, Object qInterface, String qIndexMethod) {
        try {
            if (!(newVideo || userChangedDefaultQuality) || qInterface == null) {
                return originalQualityIndex;
            }
            newVideo = false;

            final int preferredResolution;
            if (ReVancedUtils.getNetworkType() == NetworkType.MOBILE) {
                preferredResolution = mobileQualitySetting.getInt();
            } else {
                preferredResolution = wifiQualitySetting.getInt();
            }
            if (!userChangedDefaultQuality && preferredResolution == AUTOMATIC_VIDEO_QUALITY_VALUE) {
                return originalQualityIndex; // nothing to do
            }

            // create list of human readable resolutions
            List<Integer> streamResolutions = new ArrayList<>();
            try {
                for (Object streamQuality : qualities) {
                    for (Field field : streamQuality.getClass().getFields()) {
                        if (field.getType().isAssignableFrom(Integer.TYPE)
                                && field.getName().length() <= 2) {
                            streamResolutions.add(field.getInt(streamQuality));
                        }
                    }
                }
                LogHelper.printDebug(() -> "Qualities: " + streamResolutions);
            } catch (Exception ignored) {
                // edit: what could be caught here?
            }

            if (userChangedDefaultQuality) {
                userChangedDefaultQuality = false;
                final int streamResolution = streamResolutions.get(userSelectedQualityIndex);
                LogHelper.printDebug(() -> "User changed default to resolution: " + streamResolution
                        + " index: " + userSelectedQualityIndex);
                changeDefaultQuality(streamResolution);
                return userSelectedQualityIndex;
            }

            // find the highest resolution that is equal to or less than the preferred resolution
            List<Integer> sortedStreamResolutions = new ArrayList<>(streamResolutions);
            Collections.sort(sortedStreamResolutions);
            int resolutionToUse = sortedStreamResolutions.get(0);
            for (Integer resolution : sortedStreamResolutions) {
                if (resolution <= preferredResolution) {
                    resolutionToUse = resolution;
                }
            }
            final int qualityIndex = streamResolutions.indexOf(resolutionToUse);
            if (qualityIndex == originalQualityIndex) {
                LogHelper.printDebug(() -> "Ignoring video that is already preferred default resolution: " + preferredResolution);
                return originalQualityIndex;
            }

            Method m = qInterface.getClass().getMethod(qIndexMethod, Integer.TYPE);
            LogHelper.printDebug(() -> "Method is: " + qIndexMethod);
            m.invoke(qInterface, resolutionToUse);
            final int resolutionToUseLog = resolutionToUse;
            LogHelper.printDebug(() -> "Quality changed from index: " + originalQualityIndex
                    + " to index: " + qualityIndex + " resolution: " + resolutionToUseLog);
            return qualityIndex;
        } catch (Exception ex) {
            LogHelper.printException(() -> "Failed to set quality", ex);
            return originalQualityIndex;
        }
    }

    /**
     * Injection point.
     */
    public static void userChangedQuality(int selectedQuality) {
        if (!SettingsEnum.VIDEO_QUALITY_REMEMBER_LAST_SELECTED.getBoolean()) return;

        userSelectedQualityIndex = selectedQuality;
        userChangedDefaultQuality = true;
    }

    /**
     * Injection point.
     */
    public static void newVideoStarted(String currentVideo) {
        // The same videoId can be passed in multiple times for a single video playback.
        // Such as closing and opening the app, and sometimes when turning off/on the device screen.
        //
        // Known limitation, if:
        // 1. a default video quality exists, and remember quality is turned off
        // 2. user opens a video
        // 3. user changes the video resolution
        // 4. user turns on then off the device screen (or does anything else that triggers the video id hook)
        // result: the video resolution of the current video will revert back to the saved default
        //
        // The videoId could be checked if it changed,
        // but then if the user closes and re-opens the same video the default video quality will not be applied.
        LogHelper.printDebug(() -> "newVideoStarted: " + currentVideo);
        newVideo = true;
    }
}