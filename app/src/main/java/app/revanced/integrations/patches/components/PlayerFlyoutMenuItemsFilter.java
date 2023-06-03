package app.revanced.integrations.patches.components;

import android.os.Build;

import androidx.annotation.RequiresApi;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.shared.PlayerType;
import app.revanced.integrations.utils.ReVancedUtils;

public class PlayerFlyoutMenuItemsFilter extends Filter {
    private static String[] exceptions;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public PlayerFlyoutMenuItemsFilter() {
        exceptions = new String[]{
                "comment", // Anything related to comment section.
                "CellType|", // Comment filter chips on top of comment section.
                "_sheet_", // Comment flyout panel for reporting and replying options.
                "video_with_context" // Prevent video player lags sometimes when new video is started.
        };

        protobufBufferFilterGroups.addAll(
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_QUALITY_MENU,
                        "yt_outline_gear"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_CAPTIONS_MENU,
                        "yt_outline_closed_caption"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_LOOP_VIDEO_MENU,
                        "yt_outline_arrow_repeat_1_"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_AMBIENT_MODE_MENU,
                        "yt_outline_screen_light"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_REPORT_MENU,
                        "yt_outline_flag"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_HELP_MENU,
                        "yt_outline_question_circle"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_MORE_INFO_MENU,
                        "yt_outline_info_circle"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_SPEED_MENU,
                        "yt_outline_play_arrow_half_circle"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_AUDIO_TRACK_MENU,
                        "yt_outline_person_radar"
                ),
                new ByteArrayAsStringFilterGroup(
                        SettingsEnum.HIDE_WATCH_IN_VR_MENU,
                        "yt_outline_vr"
                )
        );
    }

    @Override
    boolean isFiltered(String path, String identifier, byte[] _protobufBufferArray) {
        if (ReVancedUtils.containsAny(path, exceptions)) return false;

        if (PlayerType.getCurrent() == PlayerType.WATCH_WHILE_MAXIMIZED || PlayerType.getCurrent() == PlayerType.WATCH_WHILE_FULLSCREEN)
            return super.isFiltered(path, identifier, _protobufBufferArray);
        return false;
    }
}
