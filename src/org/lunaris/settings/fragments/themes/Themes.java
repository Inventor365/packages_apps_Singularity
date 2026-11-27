/*
 * Copyright (C) 2016-2025 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.fragments.themes;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.fragments.themes.SmartPixels;
import org.lunaris.settings.utils.SystemUtils;
import org.lunaris.settings.preferences.SecureSettingPrimarySwitchPreference;
import org.lunaris.settings.preferences.SystemSettingListPreference;
import org.lunaris.settings.preferences.SystemSettingPrimarySwitchPreference;
import org.lunaris.settings.preferences.SystemPropertySwitchPreference;
import org.lunaris.settings.preferences.SystemSettingSwitchPreference;

import com.android.internal.util.lunaris.VibrationUtils;

import com.android.internal.util.lunaris.ThemeUtils;

import com.android.internal.util.lunaris.SystemRestartUtils;

import java.util.List;

@SearchIndexable
public class Themes extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "UserInterface";

    private static final String KEY_FORCE_FULL_SCREEN = "display_cutout_force_fullscreen_settings";
    private static final String KEY_VOLUME_DIALOG_TYPE = "volume_dialog_type";
    private static final String KEY_QUICKSWITCH = "quickswitch";
    private static final String KEY_PIXEL_LAUNCHER_ENHANCED = "pixel_launcher_enhanced";
    private static final String PLE_PACKAGE = "com.drdisagree.pixellauncherenhanced";
    private static final String QUICKSWITCH_PROP = "persist.sys.default_launcher";
    private static final String KEY_SHOW_VOLUME_PERCENTAGE = "show_volume_percentage";
    private static final String KEY_AXION_VOLUME_STYLE = "compose_volume_style";
    private static final String KEY_LOCK_GLYMPS = "lock_glymps_enabled";
    private static final String KEY_UNLOCK_ANIMATION = "lockscreen_unlock_animation";
    private static final String KEY_UNLOCK_EFFECT_HFR = "lockscreen_unlock_effect_hfr";
    private static final String KEY_WATER_RIPPLE_FULLSCREEN = "lockscreen_water_ripple_fullscreen";

    private static final int VOLUME_TYPE_AXION = 0;
    private static final int VOLUME_TYPE_REDESIGNED = 1;
    private static final int VOLUME_TYPE_STOCK = 2;

    private Preference mShowCutoutForce;
    private Preference mQuickSwitch;
    private Preference mPixelLauncherEnhanced;
    private SystemSettingListPreference mVolumeDialogType;
    private SystemSettingSwitchPreference mCompactVolumePill;
    private SystemSettingListPreference mAxionVolumeStyle;
    private SystemSettingSwitchPreference mShowVolumePercentage;
    private SystemSettingListPreference mUnlockAnimation;
    private Preference mUnlockEffectHfr;
    private Preference mWaterRippleFullscreen;
    private ThemeUtils mThemeUtils;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int unlockAnimation = getCurrentUnlockAnimation();
        addPreferencesFromResource(R.xml.lunaris_settings_themes);

        findPreference(KEY_LOCK_GLYMPS).setOnPreferenceChangeListener(this);

        Context mContext = getActivity().getApplicationContext();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mThemeUtils = ThemeUtils.getInstance(getActivity());

	    final String displayCutout =
            mContext.getResources().getString(com.android.internal.R.string.config_mainBuiltInDisplayCutout);

        if (TextUtils.isEmpty(displayCutout)) {
            mShowCutoutForce = (Preference) findPreference(KEY_FORCE_FULL_SCREEN);
            prefScreen.removePreference(mShowCutoutForce);
        }

        mQuickSwitch = (Preference) prefScreen.findPreference(KEY_QUICKSWITCH);
        mPixelLauncherEnhanced = (Preference) prefScreen.findPreference(KEY_PIXEL_LAUNCHER_ENHANCED);
        boolean withGoogleApps = android.os.SystemProperties.getBoolean("persist.sys.with_google_apps", false);
        boolean pixelShipped = android.os.SystemProperties.getInt("persist.sys.quickswitch_pixel_shipped", 0) != 0;
        if (!withGoogleApps) {
            if (mQuickSwitch != null) prefScreen.removePreference(mQuickSwitch);
        }
        if (mPixelLauncherEnhanced != null) {
            if (!withGoogleApps || !pixelShipped || !isPackageInstalled(mContext, PLE_PACKAGE)) {
                prefScreen.removePreference(mPixelLauncherEnhanced);
                mPixelLauncherEnhanced = null;
            } else {
                updatePixelLauncherEnhancedPreference();
            }
        }
        mVolumeDialogType = findPreference(KEY_VOLUME_DIALOG_TYPE);
        if (mVolumeDialogType != null) {
            mVolumeDialogType.setOnPreferenceChangeListener(this);
        }

        mCompactVolumePill = findPreference("compose_volume_compact_pill");
        mAxionVolumeStyle = findPreference(KEY_AXION_VOLUME_STYLE);
        mShowVolumePercentage = findPreference(KEY_SHOW_VOLUME_PERCENTAGE);

        updateVolumeRelatedVisibility(getCurrentVolumeDialogType());

        mUnlockAnimation = findPreference(KEY_UNLOCK_ANIMATION);
        mUnlockEffectHfr = findPreference(KEY_UNLOCK_EFFECT_HFR);
        mWaterRippleFullscreen = findPreference(KEY_WATER_RIPPLE_FULLSCREEN);
        if (mUnlockAnimation != null) {
            mUnlockAnimation.setValue(Integer.toString(unlockAnimation));
            mUnlockAnimation.setOnPreferenceChangeListener(this);
        }
        updateUnlockRelatedVisibility(unlockAnimation);
    }

    @Override
    public void onResume() {
        super.onResume();
        for (String key : new String[] {
                "edge_aurora_enabled", "edge_light_enabled", KEY_LOCK_GLYMPS }) {
            SystemSettingPrimarySwitchPreference preference = findPreference(key);
            if (preference != null) {
                preference.loadValue();
            }
        }
        SecureSettingPrimarySwitchPreference smartPixels =
                findPreference("smart_pixel_filter_enabled");
        if (smartPixels != null) {
            smartPixels.loadValue();
        }
        final int unlockAnimation = getCurrentUnlockAnimation();
        if (mUnlockAnimation != null) {
            mUnlockAnimation.setValue(Integer.toString(unlockAnimation));
        }
        updateUnlockRelatedVisibility(unlockAnimation);
        updatePixelLauncherEnhancedPreference();
    }

    private int getCurrentUnlockAnimation() {
        final ContentResolver resolver = getContext().getContentResolver();
        final int legacy = Settings.System.getIntForUser(resolver,
                "lockscreen_water_ripple_enabled", 0, UserHandle.USER_CURRENT);
        final int selected = Settings.System.getIntForUser(resolver,
                KEY_UNLOCK_ANIMATION, legacy == 1 ? 1 : 0, UserHandle.USER_CURRENT);
        return selected >= 0 && selected <= 4 ? selected : 0;
    }

    private void updateUnlockRelatedVisibility(int style) {
        if (mUnlockEffectHfr != null) {
            mUnlockEffectHfr.setVisible(style >= 1 && style <= 4);
        }
        if (mWaterRippleFullscreen != null) {
            mWaterRippleFullscreen.setVisible(style == 1);
        }
    }

    private int getCurrentVolumeDialogType() {
        return Settings.System.getIntForUser(
                getContext().getContentResolver(),
                KEY_VOLUME_DIALOG_TYPE,
                VOLUME_TYPE_AXION,
                UserHandle.USER_CURRENT);
    }

    private void updateVolumeRelatedVisibility(int type) {
        if (mCompactVolumePill != null) {
            mCompactVolumePill.setVisible(type == VOLUME_TYPE_AXION);
        }
        if (mAxionVolumeStyle != null) {
            mAxionVolumeStyle.setVisible(type == VOLUME_TYPE_AXION);
        }
        if (mShowVolumePercentage != null) {
            mShowVolumePercentage.setVisible(type == VOLUME_TYPE_REDESIGNED);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();
        final ContentResolver resolver = context.getContentResolver();

        if (KEY_LOCK_GLYMPS.equals(preference.getKey())) {
            LockGlympsSettings.onEnabledChanged(context, (Boolean) newValue);
            return true;
        } else if (preference == mUnlockAnimation) {
            updateUnlockRelatedVisibility(Integer.parseInt((String) newValue));
            return true;
        } else if (preference == mVolumeDialogType) {
            SystemUtils.showSystemUiRestartDialog(getActivity());
            int val = Integer.parseInt((String) newValue);
            updateVolumeRelatedVisibility(val);
            return true;
        }
        
        return false;
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

    private void updatePixelLauncherEnhancedPreference() {
        if (mPixelLauncherEnhanced == null) return;
        boolean isPixelActive = android.os.SystemProperties.getInt(QUICKSWITCH_PROP, 0) == 1;
        mPixelLauncherEnhanced.setSummary(isPixelActive
                ? R.string.pixel_launcher_enhanced_summary_active
                : R.string.pixel_launcher_enhanced_summary_warning);
    }

    private void handlePixelLauncherEnhancedClick() {
        Context context = getContext();
        if (context == null) return;
        boolean isPixelActive = android.os.SystemProperties.getInt(QUICKSWITCH_PROP, 0) == 1;
        if (isPixelActive) {
            launchPixelLauncherEnhanced(context);
            return;
        }
        new android.app.AlertDialog.Builder(context)
                .setTitle(R.string.pixel_launcher_enhanced_warning_title)
                .setMessage(R.string.pixel_launcher_enhanced_warning_message)
                .setPositiveButton(R.string.pixel_launcher_enhanced_switch_action, (dialog, which) -> {
                    Intent intent = new Intent();
                    intent.setClassName("com.android.settings", "com.android.settings.SubSettings");
                    intent.putExtra(":settings:show_fragment",
                            "org.lunaris.settings.fragments.miscellaneous.QuickSwitch");
                    startActivity(intent);
                })
                .setNegativeButton(R.string.pixel_launcher_enhanced_open_anyway, (dialog, which) ->
                        launchPixelLauncherEnhanced(context))
                .show();
    }

    private static void launchPixelLauncherEnhanced(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(PLE_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings_themes) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

	                final String displayCutout =
                        context.getResources().getString(com.android.internal.R.string.config_mainBuiltInDisplayCutout);

                    if (TextUtils.isEmpty(displayCutout)) {
                        keys.add(KEY_FORCE_FULL_SCREEN);
                    }

                    boolean withGoogleApps = android.os.SystemProperties.getBoolean("persist.sys.with_google_apps", false);
                    boolean pixelShipped = android.os.SystemProperties.getInt("persist.sys.quickswitch_pixel_shipped", 0) != 0;
                    if (!withGoogleApps) {
                        keys.add(KEY_QUICKSWITCH);
                    }
                    if (!withGoogleApps || !pixelShipped || !isPackageInstalled(context, PLE_PACKAGE)) {
                        keys.add(KEY_PIXEL_LAUNCHER_ENHANCED);
                    }

                    return keys;
                }
            };
}
