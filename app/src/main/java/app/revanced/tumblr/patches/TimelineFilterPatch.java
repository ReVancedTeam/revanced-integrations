package app.revanced.tumblr.patches;

import com.tumblr.rumblr.model.TimelineObject;
import com.tumblr.rumblr.model.Timelineable;

import java.util.ArrayList;
import java.util.List;

public final class TimelineFilterPatch {
    private static final List<String> blockedObjectTypes = new ArrayList<>();

    static {
        // This dummy gets removed by the TimelineFilterPatch and in its place,
        // equivalent instructions with a different constant string
        // will be inserted for each Timeline object type filter.
        // Modifying this line may break the patch.
        blockedObjectTypes.add("BLOCKED_OBJECT_DUMMY");
    }

    // Calls to this method are injected where the list of Timeline objects is first received.
    // We modify the list filter out elements that we want to hide.
    public static void filterTimeline(final List<TimelineObject<? extends Timelineable>> timelineObjects) {
        final var iterator = timelineObjects.iterator();
        while (iterator.hasNext()) {
            var timelineElement = iterator.next();
            if (timelineElement == null) continue;

            String elementType = timelineElement.getData().getTimelineObjectType().toString();
            if (blockedObjectTypes.contains(elementType)) iterator.remove();
        }
    }
}
