/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.os.SystemProperties;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;

/**
 * Battery saver > Limit CPU speed. Stores the cap, in percent of each CPU cluster's top
 * frequency, in persist.sys.battery_saver_cpu_cap (0 = off). The device's init scripts
 * apply it while battery saver is on. Shown only with config_battery_saver_cpu_cap.
 */
public class BatterySaverCpuCapPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    static final String PROPERTY = "persist.sys.battery_saver_cpu_cap";
    private static final int[] CAPS = {0, 90, 80, 70, 60, 50};

    public BatterySaverCpuCapPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_battery_saver_cpu_cap)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final ListPreference preference = screen.findPreference(getPreferenceKey());
        if (preference == null) {
            return;
        }
        final CharSequence[] entries = new CharSequence[CAPS.length];
        final CharSequence[] values = new CharSequence[CAPS.length];
        for (int i = 0; i < CAPS.length; i++) {
            entries[i] = label(CAPS[i]);
            values[i] = String.valueOf(CAPS[i]);
        }
        preference.setEntries(entries);
        preference.setEntryValues(values);
    }

    @Override
    public void updateState(Preference preference) {
        ((ListPreference) preference).setValue(String.valueOf(currentCap()));
        refreshSummary(preference);
    }

    @Override
    public CharSequence getSummary() {
        final int cap = currentCap();
        final String summary = cap == 0
                ? label(0).toString()
                : mContext.getString(R.string.battery_saver_cpu_cap_summary, label(cap));
        // ListPreference formats its summary with the selected entry, so a literal
        // "%" (from "70%") must be escaped.
        return summary.replace("%", "%%");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SystemProperties.set(PROPERTY, (String) newValue);
        refreshSummary(preference);
        return true;
    }

    private int currentCap() {
        final int cap = SystemProperties.getInt(PROPERTY, 0);
        for (int c : CAPS) {
            if (c == cap) {
                return cap;
            }
        }
        return 0;
    }

    private CharSequence label(int cap) {
        return cap == 0
                ? mContext.getString(R.string.battery_saver_cpu_cap_off)
                : Utils.formatPercentage(cap);
    }
}
