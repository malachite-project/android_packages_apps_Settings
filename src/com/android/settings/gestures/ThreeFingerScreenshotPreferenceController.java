/*
 * SPDX-FileCopyrightText: 2026 malachite-project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.gestures;

import static android.provider.Settings.System.THREE_FINGER_SCREENSHOT;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/** Switch for taking a screenshot by swiping down with three fingers. */
public class ThreeFingerScreenshotPreferenceController extends TogglePreferenceController {

    public ThreeFingerScreenshotPreferenceController(
            @NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(
                mContext.getContentResolver(), THREE_FINGER_SCREENSHOT, 0) != 0;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.System.putInt(
                mContext.getContentResolver(), THREE_FINGER_SCREENSHOT, isChecked ? 1 : 0);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_system;
    }
}
