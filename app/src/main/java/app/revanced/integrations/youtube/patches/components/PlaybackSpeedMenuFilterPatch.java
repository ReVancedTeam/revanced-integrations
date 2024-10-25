package app.revanced.integrations.youtube.patches.components;

import androidx.annotation.Nullable;

import app.revanced.integrations.youtube.patches.playback.speed.CustomPlaybackSpeedPatch;
import app.revanced.integrations.youtube.settings.Settings;

/**
 * Abuse LithoFilter for {@link CustomPlaybackSpeedPatch}.
 */
public final class PlaybackSpeedMenuFilterPatch extends Filter {

    /**
     * Old litho based speed selection menu.
     */
    public static volatile boolean isOldPlaybackSpeedMenuVisible;

    /**
     * 0.05x speed selection menu.
     */
    public static volatile boolean isPlaybackRateSelectorMenuVisible;

    private final StringFilterGroup playbackRateSelectorGroup;

    public PlaybackSpeedMenuFilterPatch() {
        // 0.05x litho speed menu.
        playbackRateSelectorGroup = new StringFilterGroup(
                Settings.CUSTOM_SPEED_MENU,
                "playback_rate_selector_menu_sheet.eml-js"
        );

        // Old litho based speed menu.
        var oldPlaybackMenu = new StringFilterGroup(
                Settings.CUSTOM_SPEED_MENU,
                "playback_speed_sheet_content.eml-js");

        addPathCallbacks(playbackRateSelectorGroup, oldPlaybackMenu);
    }

    @Override
    boolean isFiltered(@Nullable String identifier, String path, byte[] protobufBufferArray,
                       StringFilterGroup matchedGroup, FilterContentType contentType, int contentIndex) {
        if (matchedGroup == playbackRateSelectorGroup) {
            isPlaybackRateSelectorMenuVisible = true;
        } else {
            isOldPlaybackSpeedMenuVisible = true;
        }

        return false;
    }
}
