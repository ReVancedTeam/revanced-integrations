package app.revanced.integrations.youtube.patches;

import static app.revanced.integrations.shared.StringRef.str;
import static app.revanced.integrations.youtube.patches.MiniplayerPatch.MiniplayerType.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.shared.settings.Setting;
import app.revanced.integrations.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class MiniplayerPatch {

    /**
     * Mini player type. Null fields indicates to use the original un-patched value.
     */
    public enum MiniplayerType {
        /** Unmodified type, and same as un-patched. */
        ORIGINAL(null, null),
        PHONE(false, null),
        TABLET(true, null),
        MODERN_1(null, 1),
        MODERN_2(null, 2),
        MODERN_3(null, 3);

        /**
         * Legacy tablet hook value.
         */
        @Nullable
        final Boolean legacyTabletOverride;

        /**
         * Modern player type used by YT.
         */
        @Nullable
        final Integer modernPlayerType;

        MiniplayerType(@Nullable Boolean legacyTabletOverride, @Nullable Integer modernPlayerType) {
            this.legacyTabletOverride = legacyTabletOverride;
            this.modernPlayerType = modernPlayerType;
        }

        public boolean isModern() {
            return modernPlayerType != null;
        }
    }

    /**
     * Modern subtitle overlay for {@link MiniplayerType#MODERN_2}.
     * Resource is not present in older targets, and this field will be zero.
     */
    private static final int MODERN_OVERLAY_SUBTITLE_TEXT
            = Utils.getResourceIdentifier("modern_miniplayer_subtitle_text", "id");

    private static final MiniplayerType CURRENT_TYPE = Settings.MINIPLAYER_TYPE.get();

    private static final boolean DOUBLE_TAP_ACTION_ENABLED =
            (CURRENT_TYPE == MODERN_1 || CURRENT_TYPE == MODERN_2 || CURRENT_TYPE == MODERN_3)
                    && Settings.MINIPLAYER_DOUBLE_TAP_ACTION.get();

    private static final boolean DRAG_AND_DROP_ENABLED =
            CURRENT_TYPE == MODERN_1 && Settings.MINIPLAYER_DRAG_AND_DROP.get();

    private static final boolean HIDE_EXPAND_CLOSE_ENABLED =
            (CURRENT_TYPE == MODERN_1 || CURRENT_TYPE == MODERN_3)
                    && !DRAG_AND_DROP_ENABLED && Settings.MINIPLAYER_HIDE_EXPAND_CLOSE.get();

    private static final boolean HIDE_SUBTEXT_ENABLED =
            (CURRENT_TYPE == MODERN_1 || CURRENT_TYPE == MODERN_3) && Settings.MINIPLAYER_HIDE_SUBTEXT.get();

    private static final boolean HIDE_REWIND_FORWARD_ENABLED =
            CURRENT_TYPE == MODERN_1 && Settings.MINIPLAYER_HIDE_REWIND_FORWARD.get();

    private static final int OPACITY_LEVEL;

    public static final class MiniplayerHideExpandCloseAvailability implements Setting.Availability {
        Setting.Availability modernOneOrThree = Settings.MINIPLAYER_TYPE.availability(MODERN_1, MODERN_3);

        @Override
        public boolean isAvailable() {
            return modernOneOrThree.isAvailable() && !Settings.MINIPLAYER_DRAG_AND_DROP.get();
        }
    }

    static {
        int opacity = Settings.MINIPLAYER_OPACITY.get();

        if (opacity < 0 || opacity > 100) {
            Utils.showToastLong(str("revanced_miniplayer_opacity_invalid_toast"));
            Settings.MINIPLAYER_OPACITY.resetToDefault();
            opacity = Settings.MINIPLAYER_OPACITY.defaultValue;
        }

        OPACITY_LEVEL = (opacity * 255) / 100;
    }

    /**
     * Injection point.
     */
    public static boolean getLegacyTabletMiniplayerOverride(boolean original) {
        Boolean isTablet = CURRENT_TYPE.legacyTabletOverride;
        return isTablet == null
                ? original
                : isTablet;
    }

    /**
     * Injection point.
     */
    public static boolean getModernMiniplayerOverride(boolean original) {
        return CURRENT_TYPE == ORIGINAL
                ? original
                : CURRENT_TYPE.isModern();
    }

    /**
     * Injection point.
     */
    public static int getModernMiniplayerOverrideType(int original) {
        Integer modernValue = CURRENT_TYPE.modernPlayerType;
        return modernValue == null
                ? original
                : modernValue;
    }

    /**
     * Injection point.
     */
    public static void adjustMiniplayerOpacity(ImageView view) {
        if (CURRENT_TYPE == MODERN_1) {
            view.setImageAlpha(OPACITY_LEVEL);
        }
    }

    /**
     * Injection point.
     */
    public static boolean enableMiniplayerDoubleTapAction(boolean original) {
        return DOUBLE_TAP_ACTION_ENABLED;
    }

    /**
     * Injection point.
     */
    public static boolean enableMiniplayerDragAndDrop(boolean original) {
        return DRAG_AND_DROP_ENABLED;
    }

    /**
     * Injection point.
     */
    public static void hideMiniplayerExpandClose(ImageView view) {
        Utils.hideViewByRemovingFromParentUnderCondition(HIDE_EXPAND_CLOSE_ENABLED, view);
    }

    /**
     * Injection point.
     */
    public static void hideMiniplayerRewindForward(ImageView view) {
        Utils.hideViewByRemovingFromParentUnderCondition(HIDE_REWIND_FORWARD_ENABLED, view);
    }

    /**
     * Injection point.
     */
    public static void hideMiniplayerSubTexts(View view) {
        try {
            // Different subviews are passed in, but only TextView is of interest here.
            if (HIDE_SUBTEXT_ENABLED && view instanceof TextView) {
                Logger.printDebug(() -> "Hiding subtext view");
                Utils.hideViewByRemovingFromParentUnderCondition(true, view);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "hideMiniplayerSubTexts failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void playerOverlayGroupCreated(View group) {
        try {
            // Modern 2 has an half broken subtitle that is always present.
            // Always hide it to make the miniplayer mostly usable.
            if (CURRENT_TYPE == MODERN_2 && MODERN_OVERLAY_SUBTITLE_TEXT != 0) {
                if (group instanceof ViewGroup) {
                    View subtitleText = Utils.getChildView((ViewGroup) group, true,
                            view -> view.getId() == MODERN_OVERLAY_SUBTITLE_TEXT);

                    if (subtitleText != null) {
                        subtitleText.setVisibility(View.GONE);
                        Logger.printDebug(() -> "Modern overlay subtitle view set to hidden");
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "playerOverlayGroupCreated failure", ex);
        }
    }
}
