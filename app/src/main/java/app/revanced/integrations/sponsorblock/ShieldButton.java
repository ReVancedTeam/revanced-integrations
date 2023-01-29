package app.revanced.integrations.sponsorblock;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.utils.ReVancedUtils;

public class ShieldButton {
    private static WeakReference<ImageView> buttonReference = new WeakReference<>(null);
    private static Animation fadeIn;
    private static Animation fadeOut;
    private static boolean isShowing;

    private static final View.OnClickListener sponsorBlockBtnListener = v -> {
        LogHelper.printDebug(() -> "Shield button clicked");
        NewSegmentHelperLayout.toggle();
    };

    /**
     * injection point
     */
    public static void initialize(Object viewStub) {
        try {
            LogHelper.printDebug(() -> "initializing shield button");

            RelativeLayout youtubeControlsLayout = (RelativeLayout) viewStub;
            String buttonIdentifier = "sponsorblock_button";
            ImageView imageView = youtubeControlsLayout.findViewById(getIdentifier(buttonIdentifier, "id"));
            if (imageView == null) {
                LogHelper.printException(() -> "Couldn't find imageView with \"" + buttonIdentifier + "\"");
                return;
            }
            imageView.setOnClickListener(sponsorBlockBtnListener);
            buttonReference = new WeakReference<>(imageView);

            // Animations
            if (fadeIn == null) {
                fadeIn = getAnimation("fade_in");
                fadeIn.setDuration(getInteger("fade_duration_fast"));
                fadeOut = getAnimation("fade_out");
                fadeOut.setDuration(getInteger("fade_duration_scheduled"));
            }
            isShowing = true;
            changeVisibilityImmediate(false);
        } catch (Exception ex) {
            LogHelper.printException(() -> "Unable to set RelativeLayout", ex);
        }
    }

    public static void changeVisibilityImmediate(boolean visible) {
        changeVisibility(visible, true);
    }

    /**
     * injection point
     */
    public static void changeVisibilityNegatedImmediate(boolean visible) {
        changeVisibility(!visible, true);
    }

    /**
     * injection point
     */
    public static void changeVisibility(boolean visible) {
        changeVisibility(visible, false);
    }

    public static void changeVisibility(boolean visible, boolean immediate) {
        try {
            if (isShowing == visible) return;
            isShowing = visible;

            ImageView iView = buttonReference.get();
            if (iView == null) return;

            if (visible && shouldBeShown()) {
                if (PlayerController.getLastKnownVideoTime() >= PlayerController.getCurrentVideoLength()) {
                    return;
                }
                LogHelper.printDebug(() -> "Fading in");

                iView.setVisibility(View.VISIBLE);
                if (!immediate)
                    iView.startAnimation(fadeIn);
                return;
            }

            if (iView.getVisibility() == View.VISIBLE) {
                LogHelper.printDebug(() -> "Fading out");
                if (!immediate)
                    iView.startAnimation(fadeOut);
                iView.setVisibility(View.GONE);
            }
        } catch (Exception ex) {
            LogHelper.printException(() -> "changeVisibility failure", ex);
        }
    }

    private static boolean shouldBeShown() {
        return SettingsEnum.SB_ENABLED.getBoolean() && SettingsEnum.SB_NEW_SEGMENT_ENABLED.getBoolean();
    }

    public static void showIfShouldBeShown() {
        if (isShowing || !shouldBeShown()) {
            return;
        }
        ReVancedUtils.verifyOnMainThread();
        View v = buttonReference.get();
        if (v == null) {
            return;
        }
        v.setVisibility(View.VISIBLE);
        v.bringToFront();
        v.requestLayout();
        v.invalidate();
        isShowing = true;
    }

    public static void hide() {
        if (!isShowing) {
            return;
        }
        ReVancedUtils.verifyOnMainThread();
        View v = buttonReference.get();
        if (v == null) {
            return;
        }
        v.setVisibility(View.GONE);
        isShowing = false;
    }

    //region Helpers
    private static int getIdentifier(String name, String defType) {
        Context context = ReVancedUtils.getContext();
        return context.getResources().getIdentifier(name, defType, context.getPackageName());
    }

    private static int getInteger(String name) {
        return ReVancedUtils.getContext().getResources().getInteger(getIdentifier(name, "integer"));
    }

    private static Animation getAnimation(String name) {
        return AnimationUtils.loadAnimation(ReVancedUtils.getContext(), getIdentifier(name, "anim"));
    }
    //endregion
}
