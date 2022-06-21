package app.revanced.integrations.sponsorblock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.DisplayMetrics;

import android.widget.Toast;

import com.google.android.apps.youtube.app.YouTubeTikTokRoot_Application;

import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.sponsorblock.objects.SponsorSegment;

import static app.revanced.integrations.sponsorblock.player.ui.SponsorBlockView.hideSkipButton;
import static app.revanced.integrations.sponsorblock.player.ui.SponsorBlockView.showSkipButton;
import static app.revanced.integrations.sponsorblock.PlayerController.VERBOSE;

@SuppressLint({"RtlHardcoded", "SetTextI18n", "LongLogTag", "AppCompatCustomView"})
public class SkipSegmentView {

    private static SponsorSegment lastNotifiedSegment;

    public static void show() {
        showSkipButton();
    }

    public static void hide() {
        hideSkipButton();
    }

    public static void notifySkipped(SponsorSegment segment) {
        if (segment == lastNotifiedSegment) {
            if (VERBOSE)
                LogHelper.debug("SkipSegmentView", "notifySkipped; segment == lastNotifiedSegment");
            return;
        }
        lastNotifiedSegment = segment;
        String skipMessage = segment.category.skipMessage.toString();
        Context context = YouTubeTikTokRoot_Application.getAppContext();
        if (VERBOSE)
            LogHelper.debug("SkipSegmentView", String.format("notifySkipped; message=%s", skipMessage));

        if (context != null)
            Toast.makeText(context, skipMessage, Toast.LENGTH_SHORT).show();
    }

    public static float convertDpToPixel(float dp, Context context) {
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }
}
