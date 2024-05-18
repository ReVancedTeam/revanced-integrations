package app.revanced.integrations.youtube.patches.spoof;

import android.net.Uri;

import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.youtube.settings.Settings;

@SuppressWarnings("unused")
public class ClientSpoofPatch {
    private static final boolean CLIENT_SPOOF_ENABLED = Settings.SPOOF_CLIENT.get();
    private static final String LOCALHOST_URL = "https://127.0.0.1";
    private static final Uri LOCALHOST_URI = Uri.parse(LOCALHOST_URL);

    /**
     * Injection point.
     * Blocks /get_watch requests by returning a localhost URI.
     *
     * @param playerRequestUri The URI of the player request.
     * @return Localhost URI if the request is a /get_watch request, otherwise the original URI.
     */
    public static Uri blockGetWatchRequest(Uri playerRequestUri) {
        try {
            if (CLIENT_SPOOF_ENABLED) {
                String path = playerRequestUri.getPath();
                if (path != null && path.contains("get_watch")) {
                    return LOCALHOST_URI;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "blockGetWatchRequest failure", ex);
        }

        return playerRequestUri;
    }

    /**
     * Injection point.
     */
    public static String blockInitPlaybackRequest(String originalUrl) {
        if (CLIENT_SPOOF_ENABLED) {
            return LOCALHOST_URL;
        }

        return originalUrl;
    }

    /**
     * Injection point.
     */
    public static boolean isClientSpoofingEnabled() {
        return CLIENT_SPOOF_ENABLED;
    }
}
