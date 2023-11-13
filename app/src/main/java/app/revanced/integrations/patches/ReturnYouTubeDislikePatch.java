package app.revanced.integrations.patches;

import android.graphics.Rect;
import android.os.Build;
import android.text.*;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.revanced.integrations.patches.components.ReturnYouTubeDislikeFilterPatch;
import app.revanced.integrations.returnyoutubedislike.ReturnYouTubeDislike;
import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.shared.PlayerType;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.utils.ReVancedUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static app.revanced.integrations.returnyoutubedislike.ReturnYouTubeDislike.Vote;

/**
 * Handles all interaction of UI patch components.
 *
 * Known limitation:
 * Litho based Shorts player can experience temporarily frozen video playback if the RYD fetch takes too long.
 *
 * Temporary work around:
 * Enable app spoofing to version 18.33.40 or older, as that uses a non litho Shorts player.
 *
 * Permanent fix (yet to be implemented), either of:
 * - Modify patch to hook onto the Shorts Litho TextView, and update the dislikes asynchronously.
 * - Find a way to force Litho to rebuild it's component tree
 *   (and use that hook to force the shorts dislikes to update after the fetch is completed).
 */
public class ReturnYouTubeDislikePatch {

    /**
     * RYD data for the current video on screen.
     */
    @Nullable
    private static volatile ReturnYouTubeDislike currentVideoData;

    /**
     * The last litho based Shorts loaded.
     * May be the same value as {@link #currentVideoData}, but usually is the next short to swipe to.
     */
    @Nullable
    private static volatile ReturnYouTubeDislike lastLithoShortsVideoData;

    /**
     * Because the litho Shorts spans are created after {@link ReturnYouTubeDislikeFilterPatch}
     * detects the video ids, after the user votes the litho will update
     * but {@link #lastLithoShortsVideoData} is not the correct data to use.
     * If this is true, then instead use {@link #currentVideoData}.
     */
    private static volatile boolean lithoShortsShouldUseCurrentData;

    /**
     * Last video id prefetched. Field is prevent prefetching the same video id multiple times in a row.
     */
    @Nullable
    private static volatile String lastPrefetchedVideoId;

    public static void onRYDStatusChange(boolean rydEnabled) {
        if (!rydEnabled) {
            // Must remove all values to protect against using stale data
            // if the user enables RYD while a video is on screen.
            clearData();
        }
    }

    private static void clearData() {
        currentVideoData = null;
        lastLithoShortsVideoData = null;
        rollingNumberText = null;
        lithoShortsShouldUseCurrentData = false;
    }

    //
    // 17.x non litho regular video player.
    //

    /**
     * Resource identifier of old UI dislike button.
     */
    private static final int OLD_UI_DISLIKE_BUTTON_RESOURCE_ID
            = ReVancedUtils.getResourceIdentifier("dislike_button", "id");

    /**
     * Dislikes text label used by old UI.
     */
    @NonNull
    private static WeakReference<TextView> oldUITextViewRef = new WeakReference<>(null);

    /**
     * Original old UI 'Dislikes' text before patch modifications.
     * Required to reset the dislikes when changing videos and RYD is not available.
     * Set only once during the first load.
     */
    private static Spanned oldUIOriginalSpan;

    /**
     * Replacement span that contains dislike value. Used by {@link #oldUiTextWatcher}.
     */
    @Nullable
    private static Spanned oldUIReplacementSpan;

    /**
     * Old UI dislikes can be set multiple times by YouTube.
     * To prevent reverting changes made here, this listener overrides any future changes YouTube makes.
     */
    private static final TextWatcher oldUiTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
        public void afterTextChanged(Editable s) {
            if (oldUIReplacementSpan == null || oldUIReplacementSpan.toString().equals(s.toString())) {
                return;
            }
            s.replace(0, s.length(), oldUIReplacementSpan); // Causes a recursive call back into this listener
        }
    };

    private static void updateOldUIDislikesTextView() {
        ReturnYouTubeDislike videoData = currentVideoData;
        if (videoData == null) {
            return;
        }
        TextView oldUITextView = oldUITextViewRef.get();
        if (oldUITextView == null) {
            return;
        }
        oldUIReplacementSpan = videoData.getDislikesSpanForRegularVideo(oldUIOriginalSpan, false);
        if (!oldUIReplacementSpan.equals(oldUITextView.getText())) {
            oldUITextView.setText(oldUIReplacementSpan);
        }
    }

    /**
     * Injection point.  Called on main thread.
     *
     * Used when spoofing to 16.x and 17.x versions.
     */
    public static void setOldUILayoutDislikes(int buttonViewResourceId, @Nullable TextView textView) {
        try {
            if (!SettingsEnum.RYD_ENABLED.getBoolean()
                    || buttonViewResourceId != OLD_UI_DISLIKE_BUTTON_RESOURCE_ID
                    || textView == null) {
                return;
            }
            LogHelper.printDebug(() -> "setOldUILayoutDislikes");

            if (oldUIOriginalSpan == null) {
                // Use value of the first instance, as it appears TextViews can be recycled
                // and might contain dislikes previously added by the patch.
                oldUIOriginalSpan = (Spanned) textView.getText();
            }
            oldUITextViewRef = new WeakReference<>(textView);
            // No way to check if a listener is already attached, so remove and add again.
            textView.removeTextChangedListener(oldUiTextWatcher);
            textView.addTextChangedListener(oldUiTextWatcher);

            /**
             * If the patch is changed to include the dislikes button as a parameter to this method,
             * then if the button is already selected the dislikes could be adjusted using
             * {@link ReturnYouTubeDislike#setUserVote(Vote)}
             */

            updateOldUIDislikesTextView();

        } catch (Exception ex) {
            LogHelper.printException(() -> "setOldUILayoutDislikes failure", ex);
        }
    }


    //
    // Litho player for both regular videos and Shorts.
    //

    /**
     * Injection point.
     *
     * Called when a litho text component is initially created,
     * and also when a Span is later reused again (such as scrolling off/on screen).
     *
     * This method is sometimes called on the main thread, but it usually is called _off_ the main thread.
     * This method can be called multiple times for the same UI element (including after dislikes was added).
     *
     * @param textRef Optional cache reference to the like/dislike char sequence,
     *                which may or may not be the same as the original span parameter.
     *                If dislikes are added, the atomic reference must be set to the replacement span.
     * @param original Original char sequence was created or reused by Litho.
     * @return The original char sequence (if nothing should change), or a replacement char sequence that contains dislikes.
     */
    @NonNull
    public static CharSequence onLithoTextLoaded(@NonNull Object conversionContext,
                                                 @Nullable AtomicReference<CharSequence> textRef,
                                                 @NonNull CharSequence original) {
        try {
            if (!SettingsEnum.RYD_ENABLED.getBoolean()) {
                return original;
            }

            String conversionContextString = conversionContext.toString();

            final CharSequence replacement;
            if (conversionContextString.contains("|segmented_like_dislike_button.eml|")) {
                // Regular video
                ReturnYouTubeDislike videoData = currentVideoData;
                if (videoData == null) {
                    return original; // User enabled RYD while a video was on screen.
                }
                replacement = videoData.getDislikesSpanForRegularVideo((Spannable) original, true);

                // When spoofing between 17.09.xx and 17.30.xx the UI is the old layout
                // but uses litho and the dislikes is "|dislike_button.eml|".
                // But spoofing to that range gives a broken UI layout so no point checking for that.
            } else if (conversionContextString.contains("|shorts_dislike_button.eml|")) {
                // Litho Shorts player.
                if (!SettingsEnum.RYD_SHORTS.getBoolean()) {
                    // Must clear the current video here, otherwise if the user opens a regular video
                    // then opens a litho short (while keeping the regular video on screen), then closes the short,
                    // the original video may show the incorrect dislike value.
                    clearData();
                    return original;
                }
                ReturnYouTubeDislike videoData = lastLithoShortsVideoData;
                if (videoData == null) {
                    // The Shorts litho video id filter did not detect the video id.
                    // This is normal if in incognito mode, but otherwise is not normal.
                    LogHelper.printDebug(() -> "Cannot modify Shorts litho span, data is null");
                    return original;
                }
                // Use the correct dislikes data after voting.
                if (lithoShortsShouldUseCurrentData) {
                    lithoShortsShouldUseCurrentData = false;
                    videoData = currentVideoData;
                    if (videoData == null) {
                        LogHelper.printException(() -> "currentVideoData is null"); // Should never happen
                        return original;
                    }
                    LogHelper.printDebug(() -> "Using current video data for litho span");
                }
                replacement = videoData.getDislikeSpanForShort((Spannable) original);
            } else {
                return original;
            }

            if (textRef != null) textRef.set(replacement);
            return replacement;
        } catch (Exception ex) {
            LogHelper.printException(() -> "onLithoTextLoaded failure", ex);
        }
        return original;
    }

    //
    // Rolling Number
    //

    /**
     * Current regular video rolling text, if one exists.
     * This is saved to a field as it's used in every draw() call.
     */
    @Nullable
    private static volatile CharSequence rollingNumberText;

    /**
     * Injection point.
     */
    public static CharSequence onRollingNumberLoaded(@NonNull Object conversionContext,
                                                     @NonNull CharSequence original) {
        try {
            if (SettingsEnum.RYD_ENABLED.getBoolean()) {
                final boolean isSpannable = original instanceof Spanned;
                Spanned originalSpan = isSpannable ? (Spanned) original : new SpannableString(original);
                CharSequence replacement = onLithoTextLoaded(conversionContext, null, originalSpan);
                rollingNumberText = replacement;

                if (isSpannable) return replacement;
                return replacement.toString();
            }
        } catch (Exception ex) {
            LogHelper.printException(() -> "onRollingNumberLoaded failure", ex);
        }
        return original;
    }

    /**
     * Injection point.
     */
    public static CharSequence updateRollingText(CharSequence text) {
        try {
            if (SettingsEnum.RYD_ENABLED.getBoolean()) {
                // Called for all instances of RollingNumber, so must check if text is for a dislikes.
                // Text will already have the correct content, but it's missing the separators and Span styling.
                if (!ReturnYouTubeDislike.isPreviouslyCreatedSegmentedSpan(text.toString())) {
                    return text;
                }
                CharSequence replacement = rollingNumberText;
                if (replacement == null) {
                    // User enabled RYD while a video was open,
                    // or user opened/closed a Short while a regular video was opened.
                    LogHelper.printDebug(() -> "Cannot update rolling number (field is null");
                    return text;
                }
                return rollingNumberText;
            }
        } catch (Exception ex) {
            LogHelper.printException(() -> "updateRollingText failure", ex);
        }
        return text;
    }

    //
    // Non litho Shorts player.
    //

    /**
     * Replacement text to use for "Dislikes" while RYD is fetching.
     */
    private static final Spannable SHORTS_LOADING_SPAN = new SpannableString("-");

    /**
     * Dislikes TextViews used by Shorts.
     *
     * Multiple TextViews are loaded at once (for the prior and next videos to swipe to).
     * Keep track of all of them, and later pick out the correct one based on their on screen position.
     */
    private static final List<WeakReference<TextView>> shortsTextViewRefs = new ArrayList<>();

    private static void clearRemovedShortsTextViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // YouTube requires Android N or greater
            shortsTextViewRefs.removeIf(ref -> ref.get() == null);
        }
    }

    /**
     * Injection point.  Called when a Shorts dislike is updated.  Always on main thread.
     * Handles update asynchronously, otherwise Shorts video will be frozen while the UI thread is blocked.
     *
     * @return if RYD is enabled and the TextView was updated.
     */
    public static boolean setShortsDislikes(@NonNull View likeDislikeView) {
        try {
            if (!SettingsEnum.RYD_ENABLED.getBoolean()) {
                return false;
            }
            if (!SettingsEnum.RYD_SHORTS.getBoolean()) {
                // Must clear the data here, in case a new video was loaded while PlayerType
                // suggested the video was not a short (can happen when spoofing to an old app version).
                clearData();
                return false;
            }
            LogHelper.printDebug(() -> "setShortsDislikes");

            TextView textView = (TextView) likeDislikeView;
            textView.setText(SHORTS_LOADING_SPAN); // Change 'Dislike' text to the loading text.
            shortsTextViewRefs.add(new WeakReference<>(textView));

            if (likeDislikeView.isSelected() && isShortTextViewOnScreen(textView)) {
                LogHelper.printDebug(() -> "Shorts dislike is already selected");
                ReturnYouTubeDislike videoData = currentVideoData;
                if (videoData != null) videoData.setUserVote(Vote.DISLIKE);
            }

            // For the first short played, the Shorts dislike hook is called after the video id hook.
            // But for most other times this hook is called before the video id (which is not ideal).
            // Must update the TextViews here, and also after the videoId changes.
            updateOnScreenShortsTextViews(false);

            return true;
        } catch (Exception ex) {
            LogHelper.printException(() -> "setShortsDislikes failure", ex);
            return false;
        }
    }

    /**
     * @param forceUpdate if false, then only update the 'loading text views.
     *                    If true, update all on screen text views.
     */
    private static void updateOnScreenShortsTextViews(boolean forceUpdate) {
        try {
            clearRemovedShortsTextViews();
            if (shortsTextViewRefs.isEmpty()) {
                return;
            }
            ReturnYouTubeDislike videoData = currentVideoData;
            if (videoData == null) {
                return;
            }

            LogHelper.printDebug(() -> "updateShortsTextViews");

            Runnable update = () -> {
                Spanned shortsDislikesSpan = videoData.getDislikeSpanForShort(SHORTS_LOADING_SPAN);
                ReVancedUtils.runOnMainThreadNowOrLater(() -> {
                    String videoId = videoData.getVideoId();
                    if (!videoId.equals(VideoInformation.getVideoId())) {
                        // User swiped to new video before fetch completed
                        LogHelper.printDebug(() -> "Ignoring stale dislikes data for short: " + videoId);
                        return;
                    }

                    // Update text views that appear to be visible on screen.
                    // Only 1 will be the actual textview for the current Short,
                    // but discarded and not yet garbage collected views can remain.
                    // So must set the dislike span on all views that match.
                    for (WeakReference<TextView> textViewRef : shortsTextViewRefs) {
                        TextView textView = textViewRef.get();
                        if (textView == null) {
                            continue;
                        }
                        if (isShortTextViewOnScreen(textView)
                                && (forceUpdate || textView.getText().toString().equals(SHORTS_LOADING_SPAN.toString()))) {
                            LogHelper.printDebug(() -> "Setting Shorts TextView to: " + shortsDislikesSpan);
                            textView.setText(shortsDislikesSpan);
                        }
                    }
                });
            };
            if (videoData.fetchCompleted()) {
                update.run(); // Network call is completed, no need to wait on background thread.
            } else {
                ReVancedUtils.runOnBackgroundThread(update);
            }
        } catch (Exception ex) {
            LogHelper.printException(() -> "updateOnScreenShortsTextViews failure", ex);
        }
    }

    /**
     * Check if a view is within the screen bounds.
     */
    private static boolean isShortTextViewOnScreen(@NonNull View view) {
        final int[] location = new int[2];
        view.getLocationInWindow(location);
        if (location[0] <= 0 && location[1] <= 0) { // Lower bound
            return false;
        }
        Rect windowRect = new Rect();
        view.getWindowVisibleDisplayFrame(windowRect); // Upper bound
        return location[0] < windowRect.width() && location[1] < windowRect.height();
    }


    //
    // Video Id and voting hooks (all players).
    //

    /**
     * Injection point.  Uses 'playback response' video id hook to preload RYD.
     */
    public static void preloadVideoId(@NonNull String videoId, boolean videoIsOpeningOrPlaying) {
        try {
            // Shorts shelf in home and subscription feed causes player response hook to be called,
            // and the 'is opening/playing' parameter will be false.
            // This hook will be called again when the Short is actually opened.
            if (!videoIsOpeningOrPlaying || !SettingsEnum.RYD_ENABLED.getBoolean()) {
                return;
            }
            if (!SettingsEnum.RYD_SHORTS.getBoolean() && PlayerType.getCurrent().isNoneHiddenOrSlidingMinimized()) {
                return;
            }
            if (videoId.equals(lastPrefetchedVideoId)) {
                return;
            }
            lastPrefetchedVideoId = videoId;
            LogHelper.printDebug(() -> "Prefetching RYD for video: " + videoId);
            ReturnYouTubeDislike.getFetchForVideoId(videoId);
        } catch (Exception ex) {
            LogHelper.printException(() -> "preloadVideoId failure", ex);
        }
    }

    /**
     * Injection point.  Uses 'current playing' video id hook.  Always called on main thread.
     */
    public static void newVideoLoaded(@NonNull String videoId) {
        newVideoLoaded(videoId, false);
    }

    /**
     * Called both on and off main thread.
     *
     * @param isShortsLithoVideoId If the video id is from {@link ReturnYouTubeDislikeFilterPatch}.
     *                             if true, then the video id can be null indicating the filter did
     *                             not find any video id.
     */
    public static void newVideoLoaded(@Nullable String videoId, boolean isShortsLithoVideoId) {
        try {
            if (!SettingsEnum.RYD_ENABLED.getBoolean()) return;

            PlayerType currentPlayerType = PlayerType.getCurrent();
            final boolean isNoneHiddenOrSlidingMinimized = currentPlayerType.isNoneHiddenOrSlidingMinimized();
            if (isNoneHiddenOrSlidingMinimized && !SettingsEnum.RYD_SHORTS.getBoolean()) {
                // Must clear here, otherwise the wrong data can be used for a minimized regular video.
                clearData();
                return;
            }

            if (isShortsLithoVideoId) {
                // Litho Shorts video.
                if (videoIdIsSame(lastLithoShortsVideoData, videoId)) {
                    return;
                }
                if (videoId == null) {
                    // Litho filter did not detect the video id.  App is in incognito mode,
                    // or the proto buffer structure was changed and the video id is no longer present.
                    // Must clear both currently playing and last litho data otherwise the
                    // next regular video may use the wrong data.
                    LogHelper.printDebug(() -> "Litho filter did not find any video ids");
                    clearData();
                    return;
                }
                ReturnYouTubeDislike videoData = ReturnYouTubeDislike.getFetchForVideoId(videoId);
                videoData.setVideoIdIsShort(true);
                lastLithoShortsVideoData = videoData;
                lithoShortsShouldUseCurrentData = false;
            } else {
                Objects.requireNonNull(videoId);
                // All other playback (including non-litho Shorts).
                if (videoIdIsSame(currentVideoData, videoId)) {
                    return;
                }
                ReturnYouTubeDislike data = ReturnYouTubeDislike.getFetchForVideoId(videoId);
                // Pre-emptively set the data to short status.
                // Required to prevent Shorts data from being used on a minimized video in incognito mode.
                if (isNoneHiddenOrSlidingMinimized) {
                    data.setVideoIdIsShort(true);
                }
                currentVideoData = data;
            }

            LogHelper.printDebug(() -> "New video id: " + videoId + " playerType: " + currentPlayerType
                    + " isShortsLithoHook: " + isShortsLithoVideoId);

            // Current video id hook can be called out of order with the non litho Shorts text view hook.
            // Must manually update again here.
            if (!isShortsLithoVideoId && isNoneHiddenOrSlidingMinimized) {
                updateOnScreenShortsTextViews(true);
            }
        } catch (Exception ex) {
            LogHelper.printException(() -> "newVideoLoaded failure", ex);
        }
    }

    private static boolean videoIdIsSame(@Nullable ReturnYouTubeDislike fetch, @Nullable String videoId) {
        return (fetch == null && videoId == null)
                || (fetch != null && fetch.getVideoId().equals(videoId));
    }

    /**
     * Injection point.
     *
     * Called when the user likes or dislikes.
     *
     * @param vote int that matches {@link ReturnYouTubeDislike.Vote#value}
     */
    public static void sendVote(int vote) {
        try {
            if (!SettingsEnum.RYD_ENABLED.getBoolean()) {
                return;
            }
            final boolean isNoneHiddenOrMinimized = PlayerType.getCurrent().isNoneHiddenOrMinimized();
            if (isNoneHiddenOrMinimized && !SettingsEnum.RYD_SHORTS.getBoolean()) {
                return;
            }
            ReturnYouTubeDislike videoData = currentVideoData;
            if (videoData == null) {
                LogHelper.printDebug(() -> "Cannot send vote, as current video data is null");
                return; // User enabled RYD while a regular video was minimized.
            }

            for (Vote v : Vote.values()) {
                if (v.value == vote) {
                    videoData.sendVote(v);

                    if (isNoneHiddenOrMinimized) {
                        if (lastLithoShortsVideoData != null) {
                            lithoShortsShouldUseCurrentData = true;
                        }
                        updateOldUIDislikesTextView();
                    }

                    return;
                }
            }
            LogHelper.printException(() -> "Unknown vote type: " + vote);
        } catch (Exception ex) {
            LogHelper.printException(() -> "sendVote failure", ex);
        }
    }
}
