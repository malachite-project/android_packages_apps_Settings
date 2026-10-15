/*
 * Copyright (C) 2020 The LineageOS Project
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

package com.android.settings.display;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MinRefreshRatePreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnStart, OnStop, Preference.OnPreferenceChangeListener {

    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";

    private ListPreference mListPreference;

    private List<Float> mRates = new ArrayList<>();

    // The list offers no rate above the maximum refresh rate, so follow it.
    private final ContentObserver mPeakObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    if (mListPreference != null) {
                        updateState(mListPreference);
                    }
                }
            };

    public MinRefreshRatePreferenceController(Context context) {
        super(context, KEY_MIN_REFRESH_RATE);

        if (mContext.getResources().getBoolean(R.bool.config_show_min_refresh_rate_switch)) {
            final int lowest = mContext.getResources().getInteger(
                    R.integer.config_min_refresh_rate_lowest);
            Display.Mode mode = mContext.getDisplay().getMode();
            Display.Mode[] modes = mContext.getDisplay().getSupportedModes();
            Arrays.sort(modes, (mode1, mode2) ->
                Float.compare(mode2.getRefreshRate(), mode1.getRefreshRate()));
            for (Display.Mode m : modes) {
                if (m.getPhysicalWidth() == mode.getPhysicalWidth() &&
                        m.getPhysicalHeight() == mode.getPhysicalHeight() &&
                        Math.round(m.getRefreshRate()) >= lowest) {
                    mRates.add(m.getRefreshRate());
                }
            }
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return mRates.size() > 1 ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MIN_REFRESH_RATE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mListPreference = screen.findPreference(getPreferenceKey());

        super.displayPreference(screen);
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(PEAK_REFRESH_RATE), false, mPeakObserver);
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mPeakObserver);
    }

    @Override
    public void updateState(Preference preference) {
        final float currentValue = Settings.System.getFloat(mContext.getContentResolver(),
                MIN_REFRESH_RATE, 60.00f);
        final float peak = getPeakRefreshRate();
        final List<String> entries = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        for (float rate : mRates) {
            // Keep the current value even if it is above the maximum, so it shows as set.
            if (Math.round(rate) <= Math.round(peak)
                    || Math.round(rate) == Math.round(currentValue)) {
                entries.add(String.format("%.02fHz", rate).replaceAll("[\\.,]00", ""));
                values.add(String.format(Locale.US, "%.02f", rate));
            }
        }
        mListPreference.setEntries(entries.toArray(new String[0]));
        mListPreference.setEntryValues(values.toArray(new String[0]));

        int index = mListPreference.findIndexOfValue(
                String.format(Locale.US, "%.02f", currentValue));
        if (index < 0) index = values.size() - 1;
        mListPreference.setValueIndex(index);
        mListPreference.setSummary(mListPreference.getEntries()[index]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.System.putFloat(mContext.getContentResolver(), MIN_REFRESH_RATE,
                Float.valueOf((String) newValue));
        updateState(preference);
        return true;
    }

    private float getPeakRefreshRate() {
        final float defaultPeak = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultPeakRefreshRate);
        final float peak = Settings.System.getFloat(mContext.getContentResolver(),
                PEAK_REFRESH_RATE, defaultPeak > 0 ? defaultPeak : Float.POSITIVE_INFINITY);
        // Infinity means the highest rate.
        return Float.isInfinite(peak) || mRates.isEmpty()
                ? Float.POSITIVE_INFINITY : Math.min(peak, mRates.get(0));
    }
}
