package app.revanced.integrations.adremover;

import android.view.View;

import app.revanced.integrations.log.LogHelper;
import app.revanced.integrations.preferences.BooleanPreferences;

public class HideCreateButtonPatch {

    //Todo: Switch BooleanPreferences to Settings class
    //Used by app.revanced.patches.youtube.layout.createbutton.patch.CreateButtonRemoverPatch
    public static void hideCreateButton(View view) {
        AdRemoverAPI.hideCreateButton(view);
    }
}
