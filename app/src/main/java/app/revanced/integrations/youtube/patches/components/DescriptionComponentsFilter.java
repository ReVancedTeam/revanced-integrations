package app.revanced.integrations.youtube.patches.components;

import androidx.annotation.Nullable;
import app.revanced.integrations.youtube.settings.Setting;
import app.revanced.integrations.youtube.utils.StringTrieSearch;

@SuppressWarnings("unused")
final class DescriptionComponentsFilter extends Filter {

    private final StringTrieSearch exceptions = new StringTrieSearch();

    public DescriptionComponentsFilter() {
        exceptions.addPatterns(
                "compact_channel",
                "description",
                "grid_video",
                "inline_expander",
                "metadata"
        );

        final StringFilterGroup chapterSection = new StringFilterGroup(
                Setting.HIDE_CHAPTERS,
                "macro_markers_carousel"
        );

        final StringFilterGroup infoCardsSection = new StringFilterGroup(
                Setting.HIDE_INFO_CARDS_SECTION,
                "infocards_section"
        );

        final StringFilterGroup gameSection = new StringFilterGroup(
                Setting.HIDE_GAME_SECTION,
                "gaming_section"
        );

        final StringFilterGroup musicSection = new StringFilterGroup(
                Setting.HIDE_MUSIC_SECTION,
                "music_section",
                "video_attributes_section"
        );

        final StringFilterGroup podcastSection = new StringFilterGroup(
                Setting.HIDE_PODCAST_SECTION,
                "playlist_section"
        );

        final StringFilterGroup transcriptSection = new StringFilterGroup(
                Setting.HIDE_TRANSCIPT_SECTION,
                "transcript_section"
        );

        addPathCallbacks(
                chapterSection,
                infoCardsSection,
                gameSection,
                musicSection,
                podcastSection,
                transcriptSection
        );
    }


    @Override
    boolean isFiltered(@Nullable String identifier, String path, byte[] protobufBufferArray,
                       StringFilterGroup matchedGroup, FilterContentType contentType, int contentIndex) {
        if (exceptions.matches(path)) return false;

        return super.isFiltered(path, identifier, protobufBufferArray, matchedGroup, contentType, contentIndex);
    }
}