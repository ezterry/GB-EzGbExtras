/*
**
** Note portions of this file are copied from SpareParts Part
** of the Android Open Source Project
**
** Copyright 2011, Terrence Ezrol (ezGingerbread Project)
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

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.provider.Settings;
import android.preference.PreferenceActivity;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


import name.devnull.ezgb.extras.R;

public class CpuFreqActivity extends PreferenceActivity
                             implements Preference.OnPreferenceChangeListener
{
    private static final String TAG = "EzGbExtras";
    private static final String CPU0_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/";
    private static final int    NOLIMIT = -2;

    private CheckBoxPreference mEnableCPUFreq;
    private Preference mResetCPUFreq;
    private ListPreference mCPUMaxWake;
    private ListPreference mCPUMaxSleep;
    private ListPreference mCPUMin;
    private Preference mCPUBootInfo;
    private Preference mCPUSetBoot;
    private int[] mAvailableSpeeds;

    @Override
    public void onCreate(Bundle bndl) {
        super.onCreate(bndl);

        Log.i(TAG,"Create CPU Speed (CPUFreq) window");
        addPreferencesFromResource(R.xml.cpufreq_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        mEnableCPUFreq = (CheckBoxPreference) prefSet.findPreference("enable_cpufreq");
        mResetCPUFreq = (Preference) prefSet.findPreference("reset_cpufreq");
        mCPUMaxWake = (ListPreference) prefSet.findPreference("cpufreq_maxwake_list");
        mCPUMaxSleep = (ListPreference) prefSet.findPreference("cpufreq_maxsleep_list");
        mCPUMin = (ListPreference) prefSet.findPreference("cpufreq_min_list");
        mCPUBootInfo = (Preference) prefSet.findPreference("cpufreq_boot_info");
        mCPUSetBoot = (Preference) prefSet.findPreference("cpufreq_setboot");

        mCPUMaxWake.setOnPreferenceChangeListener(this);
        mCPUMaxSleep.setOnPreferenceChangeListener(this);
        mCPUMin.setOnPreferenceChangeListener(this);

        Log.i(TAG,"Read CPUFreq speeds");
        mAvailableSpeeds=readCpuSpeeds();

        //Verify screen state and return
        refreshStates();
    }

    private int[] readCpuSpeeds(){
        /* Read the avalible CPU speeds */
        try{
            FileInputStream in = null;
            int cnt=0,ch;
            int[] result;
            String vals[]=new String[50];

            in = new FileInputStream(CPU0_FREQ_PATH + "scaling_available_frequencies");
            vals[0]="";
            while(cnt<50){
                ch=in.read();
                if(ch==-1){
                    if(!vals[cnt].equals("")){
                        cnt++;
                    }
                    break;
                }
                if(ch >= 0x30 && ch <= 0x39){
                    vals[cnt]+=(char)ch;
                }
                else if(ch == 0x20 && (!vals[cnt].equals(""))){
                    cnt++;
                    if(cnt < 50)
                        vals[cnt]="";
                }
            }
            in.close();
            result=new int[cnt];
            for(int i=0; i<cnt; i++){
                result[i]=Integer.parseInt(vals[i]);
            }
            return result;
        }
        catch(IOException e){
            Log.e(TAG,"Error reading CPU Speeds: " + e.toString());
            return null;
        }
    }

    private void cpuWriteInt(String type,int value){
        /* Write an int to a CPU Freq file */
        try{
            FileOutputStream out = null;
            String s = ""+value;
            byte[] chrs=s.getBytes();

            out = new FileOutputStream(CPU0_FREQ_PATH + type);
            out.write(chrs,0,chrs.length);
            out.close();
        }
        catch(IOException e){
            Log.e(TAG,"Error writing " + type + ": " + e.toString());
        }
    }

    private String[] filteredList(int minspeed,int maxspeed){
        /*Create a filtered list*/
        int sz=0;
        String[] r;
        for(int i=0; i<mAvailableSpeeds.length; i++){
            if((minspeed == NOLIMIT || mAvailableSpeeds[i] >= minspeed) &&
               (maxspeed == NOLIMIT || mAvailableSpeeds[i] <= maxspeed)
            ){
                sz++;
            }
        }
        r=new String[sz];
        sz=0;
        for(int i=0; i<mAvailableSpeeds.length; i++){
            if((minspeed == NOLIMIT || mAvailableSpeeds[i] >= minspeed) &&
               (maxspeed == NOLIMIT || mAvailableSpeeds[i] <= maxspeed)
            ){
                r[sz]=""+mAvailableSpeeds[i];
                sz++;
            }
        }
        return r;
    }

    private static int findSpeedIndex(String[] lst,String val){
        int idx;

        for(idx=0;idx<lst.length;idx++){
            if(val.equals(lst[idx]))
                return idx;
        }
        return lst.length-1;
    }

    private void refreshStates(){
        /* Refresh the states of toggles and CPU speed data */
        Log.i(TAG,"(CPUFreq): refreshStates()");
        boolean isEnabled,isDefault;
        int cpuMin,cpuWakeMax,cpuSleepMax;
        String[] lst;
        Context ctx = getApplicationContext();

        cpuMin = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.CPUFREQ_PERSIST_MIN, -1);
        cpuWakeMax = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.CPUFREQ_PERSIST_WAKEMAX, -1);
        cpuSleepMax = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.CPUFREQ_PERSIST_SLEEPMAX, -1);

        if(cpuMin == -1 || cpuWakeMax == -1 || cpuSleepMax == -1){
            isDefault=true;
        } else {
            isDefault=false;
        }

        if(cpuMin == 0 || cpuWakeMax == 0 || cpuSleepMax == 0){
            isEnabled=false;
        } else {
            isEnabled=true;
        }

        //Update On Boot elements:
        mEnableCPUFreq.setChecked(isEnabled);
        String meta="";
        if(isDefault){
            meta=ctx.getString(R.string.cpufreq_boot_title_default);
        } else {
            meta=ctx.getString(R.string.cpufreq_boot_title_max) +
                    cpuWakeMax + "/" +
                    cpuSleepMax + "\n" +
                 ctx.getString(R.string.cpufreq_boot_title_min) +
                    cpuMin + "";
        }
        mCPUBootInfo.setSummary(meta);

        if(isEnabled){
            //now read the active settings
            cpuMin = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_MIN, 0);
            cpuWakeMax = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_WAKEMAX, 0);
            cpuSleepMax = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_SLEEPMAX, 0);

            mCPUMaxWake.setSummary(""+cpuWakeMax);
            mCPUMaxSleep.setSummary(""+cpuSleepMax);
            mCPUMin.setSummary(""+cpuMin);

            try{
                lst=filteredList(NOLIMIT,(cpuWakeMax>cpuSleepMax)?
                                          cpuSleepMax : cpuWakeMax);
                mCPUMin.setEntries(lst);
                mCPUMin.setEntryValues(lst);
                mCPUMin.setValueIndex(findSpeedIndex(lst,""+cpuMin));

                lst=filteredList(cpuMin,NOLIMIT);
                mCPUMaxSleep.setEntries(lst);
                mCPUMaxSleep.setEntryValues(lst);
                mCPUMaxSleep.setValueIndex(findSpeedIndex(lst,""+cpuSleepMax));
                mCPUMaxWake.setEntries(lst);
                mCPUMaxWake.setEntryValues(lst);
                mCPUMaxWake.setValueIndex(findSpeedIndex(lst,""+cpuWakeMax));
            } catch (ArrayIndexOutOfBoundsException e){
                Log.w(TAG,"Invalid cpu speed.. ignoring");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStates();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Context context = getApplicationContext();
        if (preference == mEnableCPUFreq) {
            if(!mEnableCPUFreq.isChecked()){
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_SLEEPMAX,0);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_WAKEMAX,0);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_MIN,0);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_ACTIVE_SLEEPMAX,0);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_ACTIVE_WAKEMAX,0);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_ACTIVE_MIN,0);
                refreshStates();
            }
            else{
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_SLEEPMAX,-1);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_WAKEMAX,-1);
                Settings.Secure.putInt(getContentResolver(),
                         Settings.Secure.CPUFREQ_PERSIST_MIN,-1);
                Toast toast = Toast.makeText(context,
                    context.getString(R.string.toast_reboot_required),
                    Toast.LENGTH_SHORT);
                toast.show();
                refreshStates();
            }
        }
        else if (preference == mResetCPUFreq){
            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_SLEEPMAX,-1);
            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_WAKEMAX,-1);
            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_MIN,-1);
            Toast toast = Toast.makeText(context,
                context.getString(R.string.toast_reboot_required),
                Toast.LENGTH_SHORT);
            toast.show();
            refreshStates();
        }
        else if (preference == mCPUSetBoot){
            int cpuSleepMax = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_SLEEPMAX, 0);
            int cpuWakeMax = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_WAKEMAX, 0);
            int cpuMin = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.CPUFREQ_ACTIVE_MIN, 0);

            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_SLEEPMAX,cpuSleepMax);
            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_WAKEMAX,cpuWakeMax);
            Settings.Secure.putInt(getContentResolver(),
                     Settings.Secure.CPUFREQ_PERSIST_MIN,cpuMin);
            refreshStates();
        }
        return false;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        try {
            if(preference == mCPUMaxWake){
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.CPUFREQ_ACTIVE_WAKEMAX,
                        Integer.parseInt(objValue.toString()));
                Log.i(TAG,"ScreenOn Max = " + objValue.toString());
                cpuWriteInt("scaling_max_freq",
                            Integer.parseInt(objValue.toString()));
                refreshStates();
            }
            else if(preference == mCPUMaxSleep){
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.CPUFREQ_ACTIVE_SLEEPMAX,
                        Integer.parseInt(objValue.toString()));
                refreshStates();
            }
            else if(preference == mCPUMin){
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.CPUFREQ_ACTIVE_MIN,
                        Integer.parseInt(objValue.toString()));
                Log.i(TAG,"ScreenOn Min = " + objValue.toString());
                cpuWriteInt("scaling_min_freq",
                            Integer.parseInt(objValue.toString()));
                refreshStates();
            }
        } catch (NumberFormatException e) {}
        return true;
    }
}
