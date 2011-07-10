/*
**
** Note portions of this file are copied from SpareParts Part
** of the Android Open Source Project
**
** Copyright 2011, Terrence Ezrol (ezGingerbread Project)
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package name.devnull.ezgb.extras.activities;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.IWindowManager;

import name.devnull.ezgb.extras.R;

import java.util.List;

public class EzGbExtras extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "EzGbExtras";

    private static final String WINDOW_ANIMATIONS_PREF = "window_animations";
    private static final String TRANSITION_ANIMATIONS_PREF = "transition_animations";
    private static final String HAPTIC_FEEDBACK_PREF = "haptic_feedback";
    private static final String END_BUTTON_PREF = "end_button";
    private static final String KEY_COMPATIBILITY_MODE = "compatibility_mode";

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mWindowAnimationsPref;
    private ListPreference mTransitionAnimationsPref;
    private CheckBoxPreference mFancyImeAnimationsPref;
    private CheckBoxPreference mHapticFeedbackPref;
    private ListPreference mEndButtonPref;
    private CheckBoxPreference mCompatibilityMode;

    private IWindowManager mWindowManager;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.i(TAG,"Creating EzGb Eztras Window");
        addPreferencesFromResource(R.xml.ezgb_extras);

        PreferenceScreen prefSet = getPreferenceScreen();
        
        mWindowAnimationsPref = (ListPreference) prefSet.findPreference(WINDOW_ANIMATIONS_PREF);
        mWindowAnimationsPref.setOnPreferenceChangeListener(this);
        mTransitionAnimationsPref = (ListPreference) prefSet.findPreference(TRANSITION_ANIMATIONS_PREF);
        mTransitionAnimationsPref.setOnPreferenceChangeListener(this);
        mHapticFeedbackPref = (CheckBoxPreference) prefSet.findPreference(HAPTIC_FEEDBACK_PREF);
        mEndButtonPref = (ListPreference) prefSet.findPreference(END_BUTTON_PREF);
        mEndButtonPref.setOnPreferenceChangeListener(this);
        mCompatibilityMode = (CheckBoxPreference) findPreference(KEY_COMPATIBILITY_MODE);
        mCompatibilityMode.setPersistent(false);
        mCompatibilityMode.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.COMPATIBILITY_MODE, 1) != 0);

        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    private void updateToggles() {
        mHapticFeedbackPref.setChecked(Settings.System.getInt(
                getContentResolver(), 
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mWindowAnimationsPref) {
            writeAnimationPreference(0, objValue);
        } else if (preference == mTransitionAnimationsPref) {
            writeAnimationPreference(1, objValue);
        } else if (preference == mEndButtonPref) {
            writeEndButtonPreference(objValue);
        }
        // always let the preference setting proceed.
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mCompatibilityMode) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.COMPATIBILITY_MODE,
                    mCompatibilityMode.isChecked() ? 1 : 0);
            return true;
        }
        return false;
    }

    public void writeAnimationPreference(int which, Object objValue) {
        try {
            float val = Float.parseFloat(objValue.toString());
            mWindowManager.setAnimationScale(which, val);
        } catch (NumberFormatException e) {
        } catch (RemoteException e) {
        }
    }

    public void writeEndButtonPreference(Object objValue) {
        try {
            int val = Integer.parseInt(objValue.toString());
            Settings.System.putInt(getContentResolver(),
                    Settings.System.END_BUTTON_BEHAVIOR, val);
        } catch (NumberFormatException e) {
        }
    }
    
    int floatToIndex(float val, int resid) {
        String[] indices = getResources().getStringArray(resid);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }

    public void readAnimationPreference(int which, ListPreference pref) {
        try {
            float scale = mWindowManager.getAnimationScale(which);
            pref.setValueIndex(floatToIndex(scale,
                    R.array.entryvalues_animations));
        } catch (RemoteException e) {
        }
    }

    public void readEndButtonPreference(ListPreference pref) {
        try {
            pref.setValueIndex(Settings.System.getInt(getContentResolver(),
                    Settings.System.END_BUTTON_BEHAVIOR));
        } catch (SettingNotFoundException e) {
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (HAPTIC_FEEDBACK_PREF.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    mHapticFeedbackPref.isChecked() ? 1 : 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i(TAG,"Resuming: Get Animation State");
        readAnimationPreference(0, mWindowAnimationsPref);
        readAnimationPreference(1, mTransitionAnimationsPref);
        Log.i(TAG,"Resuming: Get End Button State");
        readEndButtonPreference(mEndButtonPref);
        Log.i(TAG,"Resuming: Get Toggle states");
        updateToggles();
    }
}
