package app.revanced.integrations.youtube.patches.spoof;

import static app.revanced.integrations.youtube.patches.spoof.requests.StoryboardRendererRequester.getStoryboardRenderer;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.youtube.patches.VideoInformation;
import app.revanced.integrations.youtube.settings.Settings;

@SuppressWarnings("unused")
public class ClientSpoofPatch {
    private static final boolean CLIENT_SPOOF_ENABLED = Settings.CLIENT_SPOOF.get();
    private static final boolean CLIENT_SPOOF_USE_IOS = Settings.CLIENT_SPOOF_USE_IOS.get();
    private static final boolean CLIENT_SPOOF_STORYBOARD = CLIENT_SPOOF_ENABLED && !CLIENT_SPOOF_USE_IOS;

    /**
     * Any unreachable ip address.  Used to intentionally fail requests.
     */
    private static final String UNREACHABLE_HOST_URI_STRING = "https://127.0.0.0";
    private static final Uri UNREACHABLE_HOST_URI = Uri.parse(UNREACHABLE_HOST_URI_STRING);

    /**
     * Last video id loaded. Used to prevent reloading the same spec multiple times.
     */
    @Nullable
    private static volatile String lastPlayerResponseVideoId;

    // TODO: use a storyboard renderer cache, specifically for Shorts as swiping back to
    //  a previous Short causes additional fetches to occur.

    @Nullable
    private static volatile Future<StoryboardRenderer> rendererFuture;

    /**
     * Injection point.
     * Blocks /get_watch requests by returning a localhost URI.
     *
     * @param playerRequestUri The URI of the player request.
     * @return Localhost URI if the request is a /get_watch request, otherwise the original URI.
     */
    public static Uri blockGetWatchRequest(Uri playerRequestUri) {
        if (CLIENT_SPOOF_ENABLED) {
            try {
                String path = playerRequestUri.getPath();

                if (path != null && path.contains("get_watch")) {
                    Logger.printDebug(() -> "Blocking: " + playerRequestUri + " by returning: " + UNREACHABLE_HOST_URI_STRING);

                    return UNREACHABLE_HOST_URI;
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockGetWatchRequest failure", ex);
            }
        }

        return playerRequestUri;
    }

    /**
     * Injection point.
     * Blocks /initplayback requests.
     * For iOS, an unreachable host URL can be used, but for Android Testsuite, this is not possible.
     */
    public static String blockInitPlaybackRequest(String originalUrlString) {
        if (CLIENT_SPOOF_ENABLED) {
            try {
                var originalUri = Uri.parse(originalUrlString);
                String path = originalUri.getPath();

                if (path != null && path.contains("initplayback")) {
                    String replacementUriString = CLIENT_SPOOF_USE_IOS ? UNREACHABLE_HOST_URI_STRING :
                            // TODO: Ideally, a local proxy could be setup and block
                            //  the request the same way as Burp Suite is capable of
                            //  because that way the request is never sent to YouTube unnecessarily.
                            //  Just using localhost unfortunately does not work.
                            originalUri.buildUpon().clearQuery().build().toString();

                    Logger.printDebug(() -> "Blocking: " + originalUrlString + " by returning: " + replacementUriString);

                    return replacementUriString;
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockInitPlaybackRequest failure", ex);
            }
        }

        return originalUrlString;
    }

    private static ClientType clientTypeToSpoof() {
        if (CLIENT_SPOOF_USE_IOS) {
            return ClientType.IOS;
        }

        // Video is private or otherwise not available.  Use iOS client instead.
        StoryboardRenderer renderer = getRenderer(false);
        if (renderer == null) {
            Logger.printDebug(() -> "Using iOS client for paid or otherwise restricted video");
            return ClientType.IOS;
        }

        // Test client does not support live streams.
        // Use the storyboard renderer information to fallback to iOS if a live stream is opened.
        if (renderer.isLiveStream) {
            Logger.printDebug(() -> "Using iOS client for livestream: " + renderer.videoId);
            return ClientType.IOS;
        }

        return ClientType.ANDROID_TESTSUITE;
    }

    /**
     * Injection point.
     */
    public static int getClientTypeId(int originalClientTypeId) {
        if (CLIENT_SPOOF_ENABLED) {
            return clientTypeToSpoof().id;
        }

        return originalClientTypeId;
    }

    /**
     * Injection point.
     */
    public static String getClientVersion(String originalClientVersion) {
        if (CLIENT_SPOOF_ENABLED) {
            return clientTypeToSpoof().version;
        }

        return originalClientVersion;
    }

    /**
     * Injection point.
     */
    public static boolean isClientSpoofingEnabled() {
        return CLIENT_SPOOF_ENABLED;
    }

    //
    // Storyboard.
    //

    /**
     * Injection point.
     */
    public static String setPlayerResponseVideoId(String parameters, String videoId, boolean isShortAndOpeningOrPlaying) {
        if (CLIENT_SPOOF_STORYBOARD) {
            try {
                // VideoInformation is not a dependent patch, and only this single helper method is used.
                // Hook can be called when scrolling thru the feed and a Shorts shelf is present.
                // Ignore these videos.
                if (!isShortAndOpeningOrPlaying && VideoInformation.playerParametersAreShort(parameters)) {
                    Logger.printDebug(() -> "Ignoring Short: " + videoId);
                    return parameters;
                }

                fetchStoryboardRenderer(videoId);
            } catch (Exception ex) {
                Logger.printException(() -> "setPlayerResponseVideoId failure", ex);
            }
        }

        return parameters; // Return the original value since we are observing and not modifying.
    }

    private static void fetchStoryboardRenderer(String videoId) {
        if (!videoId.equals(lastPlayerResponseVideoId)) {
            rendererFuture = Utils.submitOnBackgroundThread(() -> getStoryboardRenderer(videoId));
            lastPlayerResponseVideoId = videoId;
        }
        // Block until the renderer fetch completes.
        // This is desired because if this returns without finishing the fetch
        // then video will start playback but the storyboard is not ready yet.
        getRenderer(true);
    }

    @Nullable
    private static StoryboardRenderer getRenderer(boolean waitForCompletion) {
        var future = rendererFuture;
        if (future != null) {
            try {
                if (waitForCompletion || future.isDone()) {
                    return future.get(20000, TimeUnit.MILLISECONDS); // Any arbitrarily large timeout.
                } // else, return null.
            } catch (TimeoutException ex) {
                Logger.printDebug(() -> "Could not get renderer (get timed out)");
            } catch (ExecutionException | InterruptedException ex) {
                // Should never happen.
                Logger.printException(() -> "Could not get renderer", ex);
            }
        }
        return null;
    }

    /**
     * Injection point.
     * Called from background threads and from the main thread.
     */
    @Nullable
    public static String getStoryboardRendererSpec(String originalStoryboardRendererSpec) {
        return getStoryboardRendererSpec(originalStoryboardRendererSpec, false);
    }

    /**
     * Injection point.
     * Uses additional check to handle live streams.
     * Called from background threads and from the main thread.
     */
    @Nullable
    public static String getStoryboardDecoderRendererSpec(String originalStoryboardRendererSpec) {
        return getStoryboardRendererSpec(originalStoryboardRendererSpec, true);
    }

    /**
     * Injection point.
     * Called from background threads and from the main thread.
     */
    @Nullable
    private static String getStoryboardRendererSpec(String originalStoryboardRendererSpec, boolean returnNullIfLiveStream) {
        if (CLIENT_SPOOF_STORYBOARD) {
            StoryboardRenderer renderer = getRenderer(false);
            if (renderer != null) {
                if (returnNullIfLiveStream && renderer.isLiveStream) {
                    return null;
                }
                if (renderer.spec != null) {
                    return renderer.spec;
                }
            }
        }

        return originalStoryboardRendererSpec;
    }

    /**
     * Injection point.
     */
    public static int getRecommendedLevel(int originalLevel) {
        if (CLIENT_SPOOF_STORYBOARD) {
            StoryboardRenderer renderer = getRenderer(false);
            if (renderer != null) {
                if (renderer.recommendedLevel != null) {
                    return renderer.recommendedLevel;
                }
            }
        }

        return originalLevel;
    }

    /**
     * Injection point.  Forces seekbar to be shown for paid videos.
     */
    public static boolean getSeekbarThumbnailOverrideValue() {
        if (CLIENT_SPOOF_STORYBOARD) {
            StoryboardRenderer renderer = getRenderer(false);
            if (renderer == null) {
                // Video is paid, or the storyboard fetch timed out.
                return true;
            }

            return renderer.spec != null;
        }

        return false;
    }

    enum ClientType {
        ANDROID_TESTSUITE(30, "1.9"),
        IOS(5, Utils.getAppVersionName());

        final int id;
        final String version;

        ClientType(int id, String version) {
            this.id = id;
            this.version = version;
        }
    }
}
