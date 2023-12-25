package app.revanced.integrations.patches.components;

import android.app.Instrumentation;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.LogHelper;
import app.revanced.integrations.utils.ReVancedUtils;
import app.revanced.integrations.utils.StringTrieSearch;

@SuppressWarnings("unused")
public final class AdsFilter extends Filter {
    // region Fullscreen ad
    private static long lastTimeClosedFullscreenAd = 0;
    private static final Instrumentation instrumentation = new Instrumentation();
    private final StringFilterGroup fullscreenAd;
    private final StringFilterGroup interstitialAd;

    // endregion

    private final StringTrieSearch exceptions = new StringTrieSearch();
    private final StringFilterGroup shoppingLinks;

    public AdsFilter() {
        exceptions.addPatterns(
                "home_video_with_context", // Don't filter anything in the home page video component.
                "related_video_with_context", // Don't filter anything in the related video component.
                "comment_thread", // Don't filter anything in the comments.
                "|comment.", // Don't filter anything in the comments replies.
                "library_recent_shelf"
        );

        // Identifiers.


        final var carouselAd = new StringFilterGroup(
                SettingsEnum.HIDE_GENERAL_ADS,
                "carousel_ad"
        );
        addIdentifierCallbacks(carouselAd);

        // Paths.

        fullscreenAd = new StringFilterGroup(
                SettingsEnum.HIDE_FULLSCREEN_ADS,
                "fullscreen_ad"
        );

        interstitialAd = new StringFilterGroup(
                SettingsEnum.HIDE_GENERAL_ADS,
                "_interstitial"
        );

        final var buttonedAd = new StringFilterGroup(
                SettingsEnum.HIDE_BUTTONED_ADS,
                "_buttoned_layout",
                "full_width_square_image_layout",
                "_ad_with",
                "text_image_button_group_layout",
                "video_display_button_group_layout",
                "landscape_image_wide_button_layout",
                "video_display_carousel_button_group_layout"
        );

        final var generalAds = new StringFilterGroup(
                SettingsEnum.HIDE_GENERAL_ADS,
                "ads_video_with_context",
                "banner_text_icon",
                "square_image_layout",
                "watch_metadata_app_promo",
                "video_display_full_layout",
                "hero_promo_image",
                "statement_banner",
                "carousel_footered_layout",
                "text_image_button_layout",
                "primetime_promo",
                "product_details",
                "carousel_headered_layout",
                "full_width_portrait_image_layout",
                "brand_video_shelf"
        );

        final var movieAds = new StringFilterGroup(
                SettingsEnum.HIDE_MOVIES_SECTION,
                "browsy_bar",
                "compact_movie",
                "horizontal_movie_shelf",
                "movie_and_show_upsell_card",
                "compact_tvfilm_item",
                "offer_module_root"
        );

        final var viewProducts = new StringFilterGroup(
                SettingsEnum.HIDE_PRODUCTS_BANNER,
                "product_item",
                "products_in_video"
        );

        shoppingLinks = new StringFilterGroup(
                SettingsEnum.HIDE_SHOPPING_LINKS,
                "expandable_list"
        );

        final var webLinkPanel = new StringFilterGroup(
                SettingsEnum.HIDE_WEB_SEARCH_RESULTS,
                "web_link_panel"
        );

        final var merchandise = new StringFilterGroup(
                SettingsEnum.HIDE_MERCHANDISE_BANNERS,
                "product_carousel"
        );

        final var selfSponsor = new StringFilterGroup(
                SettingsEnum.HIDE_SELF_SPONSOR,
                "cta_shelf_card"
        );

        addPathCallbacks(
                generalAds,
                buttonedAd,
                merchandise,
                viewProducts,
                selfSponsor,
                fullscreenAd,
                interstitialAd,
                webLinkPanel,
                shoppingLinks,
                movieAds
        );
    }

    @Override
    public boolean isFiltered(@Nullable String identifier, String path, byte[] protobufBufferArray,
                              StringFilterGroup matchedGroup, FilterContentType contentType, int contentIndex) {
        if (exceptions.matches(path))
            return false;

        if ((matchedGroup == fullscreenAd || matchedGroup == interstitialAd) && path.contains("|ImageType|")) {
            closeFullscreenAd();
        }

        // Check for the index because of likelihood of false positives.
        if (matchedGroup == shoppingLinks && contentIndex != 0)
            return false;

        return super.isFiltered(identifier, path, protobufBufferArray, matchedGroup, contentType, contentIndex);
    }

    /**
     * Hide the view, which shows ads in the homepage.
     *
     * @param view The view, which shows ads.
     */
    public static void hideAdAttributionView(View view) {
        ReVancedUtils.hideViewBy1dpUnderCondition(SettingsEnum.HIDE_GENERAL_ADS, view);
    }

    /**
     * Close the fullscreen ad.
     * <p>
     * The strategy is to send a back button event to the app to close the fullscreen ad using the back button event.
     */
    private static void closeFullscreenAd() {
        final var currentTime = System.currentTimeMillis();

        // Prevent spamming the back button.
        if (currentTime - lastTimeClosedFullscreenAd < 10000) return;
        lastTimeClosedFullscreenAd = currentTime;

        LogHelper.printDebug(() -> "closing Fullscreen Ad");

        ReVancedUtils.runOnMainThreadDelayed(() -> instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK), 1000);
    }
}
