package app.revanced.integrations.youtube.patches.components;

import androidx.annotation.Nullable;

import app.revanced.integrations.youtube.patches.playback.speed.CustomPlaybackSpeedPatch;

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

    private final StringFilterGroup playbackRateSelector;

    public PlaybackSpeedMenuFilterPatch() {
        // 0.05x speed menu.
        playbackRateSelector = new StringFilterGroup(
                null,
                "playback_rate_selector_menu_sheet.eml-js"
        );

        // Old litho based speed menu.
        var oldPlaybackMenu = new StringFilterGroup(
                null,
                "playback_speed_sheet_content.eml-js");

        addPathCallbacks(playbackRateSelector, oldPlaybackMenu);
    }

    @Override
    boolean isFiltered(@Nullable String identifier, String path, byte[] protobufBufferArray,
                       StringFilterGroup matchedGroup, FilterContentType contentType, int contentIndex) {
        if (matchedGroup == playbackRateSelector) {
            isPlaybackRateSelectorMenuVisible = true;
        } else {
            isOldPlaybackSpeedMenuVisible = true;
        }

        return false;
    }
}
