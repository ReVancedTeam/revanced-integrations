package app.revanced.integrations.youtube.settings.preference;

import android.os.Build;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;

import androidx.annotation.RequiresApi;

import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.shared.settings.preference.AbstractPreferenceFragment;
import app.revanced.integrations.youtube.patches.DownloadsPatch;
import app.revanced.integrations.youtube.patches.playback.speed.CustomPlaybackSpeedPatch;
import app.revanced.integrations.youtube.settings.Settings;

/**
 * Preference fragment for ReVanced settings.
 *
 * @noinspection deprecation
 */
public class ReVancedPreferenceFragment extends AbstractPreferenceFragment {

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void initialize() {
        super.initialize();

        try {
            // If the preference was included, then initialize it based on the available playback speed
            Preference defaultSpeedPreference = findPreference(Settings.PLAYBACK_SPEED_DEFAULT.key);
            if (defaultSpeedPreference instanceof ListPreference) {
                CustomPlaybackSpeedPatch.initializeListPreference((ListPreference) defaultSpeedPreference);
            }

            // Action button hook does not work on older versions.
            // Remove the preference to make things simpler.
            if (DownloadsPatch.shouldHideActionButtonOverridePreference()) {
                Preference downloadActionButton = findPreference(Settings.EXTERNAL_DOWNLOADER_ACTION_BUTTON.key);
                if (downloadActionButton != null) {
                    PreferenceGroup group = downloadActionButton.getParent();
                    if (group != null) {
                        downloadActionButton.getParent().removePreference(downloadActionButton);
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "initialize failure", ex);
        }
    }
}
