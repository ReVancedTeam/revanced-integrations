package app.revanced.integrations.settingsmenu;

import static android.text.Html.fromHtml;
import static app.revanced.integrations.sponsorblock.StringRef.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.Html;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.util.Objects;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.sponsorblock.SegmentPlaybackController;
import app.revanced.integrations.sponsorblock.SponsorBlockSettings;
import app.revanced.integrations.sponsorblock.SponsorBlockUtils;
import app.revanced.integrations.sponsorblock.objects.SegmentCategory;
import app.revanced.integrations.sponsorblock.objects.SegmentCategoryListPreference;
import app.revanced.integrations.sponsorblock.objects.UserStats;
import app.revanced.integrations.sponsorblock.requests.SBRequester;
import app.revanced.integrations.sponsorblock.ui.SponsorBlockViewController;
import app.revanced.integrations.utils.ReVancedUtils;
import app.revanced.integrations.utils.SharedPrefHelper;

@SuppressWarnings("deprecation")
public class SponsorBlockSettingsFragment extends PreferenceFragment {
    private SwitchPreference sbEnabled;
    private SwitchPreference addNewSegment;
    private SwitchPreference votingEnabled;
    private SwitchPreference compactSkipButton;

    private SwitchPreference showSkipToast;
    private SwitchPreference countSkips;
    private SwitchPreference showTimeWithoutSegments;
    private EditTextPreference newSegmentStep;
    private EditTextPreference minSegmentDuration;
    private EditTextPreference privateUserId;
    private Preference apiUrl;
    private EditTextPreference importExport;
    private PreferenceCategory statsCategory;
    private PreferenceCategory segmentCategory;

    private void updateUI() {
        final boolean enabled = SettingsEnum.SB_ENABLED.getBoolean();
        if (!enabled) {
            SponsorBlockViewController.hideSkipButton();
            SponsorBlockViewController.hideNewSegmentLayout();
            SegmentPlaybackController.setCurrentVideoId(null);
        } else if (!SettingsEnum.SB_NEW_SEGMENT_ENABLED.getBoolean()) {
            SponsorBlockViewController.hideNewSegmentLayout();
        }
        // voting and add new segment buttons automatically shows/hides themselves

        sbEnabled.setChecked(enabled);
        sbEnabled.setTitle(str("sb_enable_sb"));
        sbEnabled.setSummary(str("sb_enable_sb_sum"));

        addNewSegment.setChecked(SettingsEnum.SB_NEW_SEGMENT_ENABLED.getBoolean());
        addNewSegment.setTitle(str("sb_enable_add_segment"));
        addNewSegment.setSummary(str("sb_enable_add_segment_sum"));
        addNewSegment.setEnabled(enabled);

        votingEnabled.setChecked(SettingsEnum.SB_VOTING_ENABLED.getBoolean());
        votingEnabled.setTitle(str("sb_enable_voting"));
        votingEnabled.setSummary(str("sb_enable_voting_sum"));
        votingEnabled.setEnabled(enabled);

        compactSkipButton.setChecked(SettingsEnum.SB_USE_COMPACT_SKIPBUTTON.getBoolean());
        compactSkipButton.setTitle(str("sb_enable_compact_skip_button"));
        compactSkipButton.setSummary(str("sb_enable_compact_skip_button_sum"));
        compactSkipButton.setEnabled(enabled);

        showSkipToast.setChecked(SettingsEnum.SB_SHOW_TOAST_WHEN_SKIP.getBoolean());
        showSkipToast.setTitle(str("sb_general_skiptoast"));
        showSkipToast.setSummary(str("sb_general_skiptoast_sum"));
        showSkipToast.setEnabled(enabled);

        countSkips.setTitle(str("sb_general_skipcount"));
        countSkips.setSummary(str("sb_general_skipcount_sum"));
        countSkips.setChecked(SettingsEnum.SB_COUNT_SKIPS.getBoolean());
        countSkips.setEnabled(enabled);

        showTimeWithoutSegments.setTitle(str("sb_general_time_without"));
        showTimeWithoutSegments.setSummary(str("sb_general_time_without_sum"));
        showTimeWithoutSegments.setChecked(SettingsEnum.SB_SHOW_TIME_WITHOUT_SEGMENTS.getBoolean());
        showTimeWithoutSegments.setEnabled(enabled);

        newSegmentStep.setTitle(str("sb_general_adjusting"));
        newSegmentStep.setSummary(str("sb_general_adjusting_sum"));
        newSegmentStep.setText(String.valueOf(SettingsEnum.SB_ADJUST_NEW_SEGMENT_STEP.getInt()));
        newSegmentStep.setEnabled(enabled);

        minSegmentDuration.setTitle(str("sb_general_min_duration"));
        minSegmentDuration.setSummary(str("sb_general_min_duration_sum"));
        minSegmentDuration.setText(String.valueOf(SettingsEnum.SB_MIN_DURATION.getFloat()));
        minSegmentDuration.setEnabled(enabled);

        privateUserId.setTitle(str("sb_general_uuid"));
        privateUserId.setSummary(str("sb_general_uuid_sum"));
        privateUserId.setText(SettingsEnum.SB_UUID.getString());
        privateUserId.setEnabled(enabled);

        apiUrl.setTitle(str("sb_general_api_url"));
        apiUrl.setSummary(Html.fromHtml(str("sb_general_api_url_sum")));
        apiUrl.setEnabled(enabled);

        importExport.setTitle(str("sb_settings_ie"));
        importExport.setSummary(str("sb_settings_ie_sum"));
        importExport.setEnabled(enabled);

        segmentCategory.setEnabled(enabled);
        statsCategory.setEnabled(enabled);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(SharedPrefHelper.SharedPrefNames.SPONSOR_BLOCK.getName());

        Activity context = this.getActivity();
        PreferenceScreen preferenceScreen = preferenceManager.createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);

        SponsorBlockSettings.initialize();

        sbEnabled = new SwitchPreference(context);
        preferenceScreen.addPreference(sbEnabled);
        sbEnabled.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_ENABLED.saveValue(newValue);
            updateUI();
            return true;
        });

        addNewSegment = new SwitchPreference(context);
        preferenceScreen.addPreference(addNewSegment);
        addNewSegment.setOnPreferenceChangeListener((preference1, o) -> {
            Boolean newValue = (Boolean) o;
            if (newValue && !SettingsEnum.SB_SEEN_GUIDELINES.getBoolean()) {
                SettingsEnum.SB_SEEN_GUIDELINES.saveValue(true);
                new AlertDialog.Builder(preference1.getContext())
                        .setTitle(str("sb_guidelines_popup_title"))
                        .setMessage(str("sb_guidelines_popup_content"))
                        .setNegativeButton(str("sb_guidelines_popup_already_read"), null)
                        .setPositiveButton(str("sb_guidelines_popup_open"), (dialogInterface, i) -> openGuidelines())
                        .show();
            }
            SettingsEnum.SB_NEW_SEGMENT_ENABLED.saveValue(newValue);
            updateUI();
            return true;
        });

        votingEnabled = new SwitchPreference(context);
        preferenceScreen.addPreference(votingEnabled);
        votingEnabled.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_VOTING_ENABLED.saveValue(newValue);
            updateUI();
            return true;
        });

        compactSkipButton = new SwitchPreference(context);
        preferenceScreen.addPreference(compactSkipButton);
        compactSkipButton.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_USE_COMPACT_SKIPBUTTON.saveValue(newValue);
            updateUI();
            return true;
        });

        addGeneralCategory(context, preferenceScreen);

        segmentCategory = new PreferenceCategory(context);
        segmentCategory.setTitle(str("sb_diff_segments"));
        preferenceScreen.addPreference(segmentCategory);
        updateSegmentCategories();

        statsCategory = new PreferenceCategory(context);
        statsCategory.setTitle(str("sb_stats"));
        preferenceScreen.addPreference(statsCategory);
        fetchAndDisplayStats();

        addAboutCategory(context, preferenceScreen);

        updateUI();
    }

    private void addGeneralCategory(final Context context, PreferenceScreen screen) {
        final PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        category.setTitle(str("sb_general"));

        Preference guidelinePreferences = new Preference(context);
        guidelinePreferences.setTitle(str("sb_guidelines_preference_title"));
        guidelinePreferences.setSummary(str("sb_guidelines_preference_sum"));
        guidelinePreferences.setOnPreferenceClickListener(preference1 -> {
            openGuidelines();
            return false;
        });
        category.addPreference(guidelinePreferences);


        showSkipToast = new SwitchPreference(context);
        showSkipToast.setOnPreferenceClickListener(preference1 -> {
            ReVancedUtils.showToastShort(str("skipped_sponsor"));
            return false;
        });
        showSkipToast.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_SHOW_TOAST_WHEN_SKIP.saveValue(newValue);
            updateUI();
            return true;
        });
        category.addPreference(showSkipToast);


        countSkips = new SwitchPreference(context);
        countSkips.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_COUNT_SKIPS.saveValue(newValue);
            updateUI();
            return true;
        });
        category.addPreference(countSkips);


        showTimeWithoutSegments = new SwitchPreference(context);
        showTimeWithoutSegments.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_SHOW_TIME_WITHOUT_SEGMENTS.saveValue(newValue);
            updateUI();
            return true;
        });
        category.addPreference(showTimeWithoutSegments);


        newSegmentStep = new EditTextPreference(context);
        newSegmentStep.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        newSegmentStep.setOnPreferenceChangeListener((preference1, newValue) -> {
            final int newAdjustmentValue = Integer.parseInt(newValue.toString());
            if (newAdjustmentValue == 0) {
                ReVancedUtils.showToastLong(str("sb_general_adjusting_invalid"));
            } else {
                SettingsEnum.SB_ADJUST_NEW_SEGMENT_STEP.saveValue(newAdjustmentValue);
                updateUI();
            }
            return true;
        });
        category.addPreference(newSegmentStep);


        minSegmentDuration = new EditTextPreference(context);
        minSegmentDuration.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minSegmentDuration.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_MIN_DURATION.saveValue(Float.valueOf(newValue.toString()));
            updateUI();
            return true;
        });
        category.addPreference(minSegmentDuration);


        privateUserId = new EditTextPreference(context);
        privateUserId.setOnPreferenceChangeListener((preference1, newValue) -> {
            SettingsEnum.SB_UUID.saveValue(newValue.toString());
            fetchAndDisplayStats();
            updateUI();
            return true;
        });
        category.addPreference(privateUserId);


        apiUrl = new Preference(context);
        apiUrl.setOnPreferenceClickListener(preference1 -> {
            EditText editText = new EditText(context);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            editText.setText(SettingsEnum.SB_API_URL.getString());

            APIURLChangeListener urlListener = new APIURLChangeListener(editText);
            new AlertDialog.Builder(context)
                    .setTitle(apiUrl.getTitle())
                    .setView(editText)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(str("reset"), urlListener)
                    .setPositiveButton(android.R.string.ok, urlListener)
                    .show();
            return true;
        });
        category.addPreference(apiUrl);


        importExport = new EditTextPreference(context);
        importExport.setOnPreferenceClickListener(preference1 -> {
            importExport.getEditText().setText(SponsorBlockSettings.exportSettings());
            return true;
        });
        importExport.setOnPreferenceChangeListener((preference1, newValue) -> {
            SponsorBlockSettings.importSettings((String) newValue);
            updateSegmentCategories();
            fetchAndDisplayStats();
            updateUI();
            return true;
        });
        category.addPreference(importExport);
    }

    private void updateSegmentCategories() {
        segmentCategory.removeAll();

        Activity activity = getActivity();
        for (SegmentCategory category : SegmentCategory.valuesWithoutUnsubmitted()) {
            segmentCategory.addPreference(new SegmentCategoryListPreference(activity, category));
        }
    }

    private void addAboutCategory(Context context, PreferenceScreen screen) {
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        category.setTitle(str("sb_about"));

        {
            Preference preference = new Preference(context);
            screen.addPreference(preference);
            preference.setTitle(str("sb_about_api"));
            preference.setSummary(str("sb_about_api_sum"));
            preference.setOnPreferenceClickListener(preference1 -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://sponsor.ajay.app"));
                preference1.getContext().startActivity(i);
                return false;
            });
        }

        {
            Preference preference = new Preference(context);
            screen.addPreference(preference);
            preference.setSummary(str("sb_about_made_by"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                preference.setSingleLineTitle(false);
            }
            preference.setSelectable(false);
        }
    }

    private void openGuidelines() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://wiki.sponsor.ajay.app/w/Guidelines"));
        getActivity().startActivity(intent);
    }

    private void fetchAndDisplayStats() {
        statsCategory.removeAll();
        Preference loadingPlaceholderPreference = new Preference(this.getActivity());
        loadingPlaceholderPreference.setEnabled(false);
        statsCategory.addPreference(loadingPlaceholderPreference);
        if (SettingsEnum.SB_ENABLED.getBoolean()) {
            loadingPlaceholderPreference.setTitle(str("sb_stats_loading"));
            ReVancedUtils.runOnBackgroundThread(() -> {
                UserStats stats = SBRequester.retrieveUserStats();
                ReVancedUtils.runOnMainThread(() -> { // get back on main thread to modify UI elements
                    addUserStats(loadingPlaceholderPreference, stats);
                });
            });
        } else {
            loadingPlaceholderPreference.setTitle(str("sb_stats_sb_disabled"));
        }
    }

    private static final DecimalFormat statsNumberOfSegmentsSkippedFormatter = new DecimalFormat("#,###,###");

    private void addUserStats(@NonNull Preference loadingPlaceholder, @Nullable UserStats stats) {
        ReVancedUtils.verifyOnMainThread();
        if (stats == null) {
            loadingPlaceholder.setTitle(str("sb_stats_connection_failure"));
            return;
        }
        statsCategory.removeAll();
        Context context = statsCategory.getContext();

        {
            EditTextPreference preference = new EditTextPreference(context);
            statsCategory.addPreference(preference);
            String userName = stats.userName;
            preference.setTitle(fromHtml(str("sb_stats_username", userName)));
            preference.setSummary(str("sb_stats_username_change"));
            preference.setText(userName);
            preference.setOnPreferenceChangeListener((preference1, value) -> {
                ReVancedUtils.runOnBackgroundThread(() -> {
                    String newUserName = (String) value;
                    String errorMessage = SBRequester.setUsername(newUserName);
                    ReVancedUtils.runOnMainThread(() -> {
                        if (errorMessage == null) {
                            preference.setTitle(fromHtml(str("sb_stats_username", newUserName)));
                            preference.setText(newUserName);
                            ReVancedUtils.showToastLong(str("sb_stats_username_changed"));
                        } else {
                            preference.setText(userName); // revert to previous
                            ReVancedUtils.showToastLong(errorMessage);
                        }
                    });
                });
                return true;
            });
        }

        {
            // number of segment submissions (does not include ignored segments)
            Preference preference = new Preference(context);
            statsCategory.addPreference(preference);
            String formatted = statsNumberOfSegmentsSkippedFormatter.format(stats.segmentCount);
            preference.setTitle(fromHtml(str("sb_stats_submissions", formatted)));
            if (stats.segmentCount == 0) {
                preference.setSelectable(false);
            } else {
                preference.setOnPreferenceClickListener(preference1 -> {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("https://sb.ltn.fi/userid/" + stats.publicUserId));
                    preference1.getContext().startActivity(i);
                    return true;
                });
            }
        }

        {
            // "user reputation".  Usually not useful, since it appears most users have zero reputation.
            // But if there is a reputation, then show it here
            Preference preference = new Preference(context);
            preference.setTitle(fromHtml(str("sb_stats_reputation", stats.reputation)));
            preference.setSelectable(false);
            if (stats.reputation != 0) {
                statsCategory.addPreference(preference);
            }
        }

        {
            // time saved for other users
            Preference preference = new Preference(context);
            statsCategory.addPreference(preference);

            String stats_saved;
            String stats_saved_sum;
            if (stats.segmentCount == 0) {
                stats_saved = str("sb_stats_saved_zero");
                stats_saved_sum = str("sb_stats_saved_sum_zero");
            } else {
                stats_saved = str("sb_stats_saved", statsNumberOfSegmentsSkippedFormatter.format(stats.viewCount));
                stats_saved_sum = str("sb_stats_saved_sum", SponsorBlockUtils.getTimeSavedString((long) (60 * stats.minutesSaved)));
            }
            preference.setTitle(fromHtml(stats_saved));
            preference.setSummary(fromHtml(stats_saved_sum));
            preference.setOnPreferenceClickListener(preference1 -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://sponsor.ajay.app/stats/"));
                preference1.getContext().startActivity(i);
                return false;
            });
        }

        {
            // time the user saved by using SB
            Preference preference = new Preference(context);
            statsCategory.addPreference(preference);

            Runnable updateStatsSelfSaved = () -> {
                String formatted = statsNumberOfSegmentsSkippedFormatter.format(SettingsEnum.SB_SKIPPED_SEGMENTS.getInt());
                preference.setTitle(fromHtml(str("sb_stats_self_saved", formatted)));
                String formattedSaved = SponsorBlockUtils.getTimeSavedString(SettingsEnum.SB_SKIPPED_SEGMENTS_TIME.getLong() / 1000);
                preference.setSummary(fromHtml(str("sb_stats_self_saved_sum", formattedSaved)));
            };
            updateStatsSelfSaved.run();
            preference.setOnPreferenceClickListener(preference1 -> {
                new AlertDialog.Builder(preference1.getContext())
                        .setTitle(str("sb_stats_self_saved_reset_title"))
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            SettingsEnum.SB_SKIPPED_SEGMENTS.saveValue(SettingsEnum.SB_SKIPPED_SEGMENTS.getDefaultValue());
                            SettingsEnum.SB_SKIPPED_SEGMENTS_TIME.saveValue(SettingsEnum.SB_SKIPPED_SEGMENTS_TIME.getDefaultValue());
                            updateStatsSelfSaved.run();
                        })
                        .setNegativeButton(android.R.string.no, null).show();
                return true;
            });
        }
    }

    private static class APIURLChangeListener implements DialogInterface.OnClickListener {
        private final EditText editText;

        public APIURLChangeListener(EditText editText) {
            this.editText = Objects.requireNonNull(editText);
        }

        @Override
        public void onClick(DialogInterface dialog, int buttonPressed) {
            if (buttonPressed == DialogInterface.BUTTON_NEUTRAL) {
                SettingsEnum.SB_API_URL.saveValue(SettingsEnum.SB_API_URL.getDefaultValue());
                ReVancedUtils.showToastLong(str("sb_api_url_reset"));
            } else if (buttonPressed == DialogInterface.BUTTON_POSITIVE) {
                String textAsString = editText.getText().toString();
                if (!Patterns.WEB_URL.matcher(textAsString).matches()) {
                    ReVancedUtils.showToastLong(str("sb_api_url_invalid"));
                } else if (!textAsString.equals(SettingsEnum.SB_API_URL.getString())) {
                    SettingsEnum.SB_API_URL.saveValue(textAsString);
                    ReVancedUtils.showToastLong(str("sb_api_url_changed"));
                }
            }
        }
    }

}
