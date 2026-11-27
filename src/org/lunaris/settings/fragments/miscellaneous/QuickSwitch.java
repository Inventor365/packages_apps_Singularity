/*
 * Copyright (C) 2023 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.settings.fragments.miscellaneous;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.SearchIndexableResource;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

import com.android.internal.util.lunaris.Utils;

import com.android.internal.util.lunaris.SystemRestartUtils;

import com.android.internal.util.lunaris.VibrationUtils;

@SearchIndexable
public class QuickSwitch extends SettingsPreferenceFragment 
    implements Preference.OnPreferenceChangeListener, Indexable {
    
    private static final String TAG = "QuickSwitch";

    private static final String QUICKSWITCH_KEY = "persist.sys.default_launcher";
    private static final String WALLPAPER_OVERLAY = "com.android.system.qs.wallpaperoverlay";
    private static final String KEY_PIXEL_LAUNCHER_ENHANCED = "pixel_launcher_enhanced";
    private static final String PLE_PACKAGE = "com.drdisagree.pixellauncherenhanced";

    private ListPreference quickSwitchPref;
    private Preference pixelLauncherEnhancedPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.quick_switch);
        
        int defaultLauncher = SystemProperties.getInt(QUICKSWITCH_KEY, 0);
        quickSwitchPref = findPreference(QUICKSWITCH_KEY);
        quickSwitchPref.setOnPreferenceChangeListener(this);
        Context context = getContext();
        Resources res = context.getResources();
        String[] launcherEntries = res.getStringArray(R.array.quickswitch_launcher_entries);
        String[] launcherValues = res.getStringArray(R.array.quickswitch_launcher_values);
        List<String> quickSwitchEntries = new ArrayList<>();
        List<String> quickSwitchValues = new ArrayList<>();
        quickSwitchEntries.add(launcherEntries[0]);
        quickSwitchValues.add(launcherValues[0]);

        if (SystemProperties.getInt("persist.sys.quickswitch_pixel_shipped", 0) != 0) {
            quickSwitchEntries.add(launcherEntries[1]);
            quickSwitchValues.add(launcherValues[1]);
        }

        quickSwitchPref.setEntries(quickSwitchEntries.toArray(new CharSequence[0]));
        quickSwitchPref.setEntryValues(quickSwitchValues.toArray(new CharSequence[0]));
        quickSwitchPref.setValue(String.valueOf(defaultLauncher));

        pixelLauncherEnhancedPref = findPreference(KEY_PIXEL_LAUNCHER_ENHANCED);
        if (pixelLauncherEnhancedPref != null) {
            boolean pixelShipped = SystemProperties.getInt("persist.sys.quickswitch_pixel_shipped", 0) != 0;
            if (!pixelShipped || !isPackageInstalled(context, PLE_PACKAGE)) {
                getPreferenceScreen().removePreference(pixelLauncherEnhancedPref);
                pixelLauncherEnhancedPref = null;
            } else {
                updateEnhancedSummary(defaultLauncher);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateEnhancedSummary(SystemProperties.getInt(QUICKSWITCH_KEY, 0));
    }
    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        if (preference != null && KEY_PIXEL_LAUNCHER_ENHANCED.equals(preference.getKey())) {
            handlePixelLauncherEnhancedClick();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == quickSwitchPref) {
            int value = Integer.parseInt((String) newValue);
            applyLauncherSelection(value);
            return true;
        }
        return false;
    }

    private void applyLauncherSelection(int value) {
        Context context = getContext();
        if (context == null) return;
        if (value == 0) {
            Utils.toggleOverlay(context, WALLPAPER_OVERLAY, true);
        } else if (value == 1) {
            Utils.toggleOverlay(context, WALLPAPER_OVERLAY, false);
        }
        updateEnhancedSummary(value);
        SystemRestartUtils.showSystemRestartDialog(context);
    }

    private void updateEnhancedSummary(int launcherValue) {
        if (pixelLauncherEnhancedPref == null) return;
        pixelLauncherEnhancedPref.setSummary(launcherValue == 1
                ? R.string.pixel_launcher_enhanced_summary_active
                : R.string.pixel_launcher_enhanced_summary_warning);
    }

    private void handlePixelLauncherEnhancedClick() {
        Context context = getContext();
        if (context == null) return;
        int defaultLauncher = SystemProperties.getInt(QUICKSWITCH_KEY, 0);
        if (defaultLauncher == 1) {
            launchPixelLauncherEnhanced(context);
            return;
        }
        new android.app.AlertDialog.Builder(context)
                .setTitle(R.string.pixel_launcher_enhanced_warning_title)
                .setMessage(R.string.pixel_launcher_enhanced_warning_message)
                .setPositiveButton(R.string.pixel_launcher_enhanced_switch_action, (dialog, which) -> {
                    SystemProperties.set(QUICKSWITCH_KEY, "1");
                    if (quickSwitchPref != null) {
                        quickSwitchPref.setValue("1");
                    }
                    applyLauncherSelection(1);
                })
                .setNegativeButton(R.string.pixel_launcher_enhanced_open_anyway, (dialog, which) ->
                        launchPixelLauncherEnhanced(context))
                .show();
    }

    private static void launchPixelLauncherEnhanced(Context context) {
        android.content.Intent launchIntent =
                context.getPackageManager().getLaunchIntentForPackage(PLE_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        } catch (Exception e) {
            return false;
        }
    }
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                                                                            boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.quick_switch;
                    result.add(sir);
                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    return keys;
                }
            };
}
