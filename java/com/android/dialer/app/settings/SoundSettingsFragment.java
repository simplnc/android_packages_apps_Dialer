/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2023-2024 The LineageOS Project
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
 * limitations under the License
 */

package com.android.dialer.app.settings;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.dialer.R;
import com.android.dialer.callrecord.impl.CallRecorderService;
import com.android.dialer.util.SettingsUtil;

import java.util.List;

public class SoundSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener {

  private static final int NO_DTMF_TONE = 0;
  private static final int PLAY_DTMF_TONE = 1;

  private static final int NO_VIBRATION_FOR_CALLS = 0;
  private static final int DO_VIBRATION_FOR_CALLS = 1;

  private static final int DTMF_TONE_TYPE_NORMAL = 0;

  private static final int MSG_UPDATE_RINGTONE_SUMMARY = 1;

  private DefaultRingtonePreference ringtonePreference;
  private final Handler ringtoneLookupComplete =
      new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
          switch (msg.what) {
            case MSG_UPDATE_RINGTONE_SUMMARY:
              ringtonePreference.setSummary((CharSequence) msg.obj);
              break;
          }
        }
      };
  private final Runnable ringtoneLookupRunnable = () -> updateRingtonePreferenceSummary();

  private SwitchPreferenceCompat vibrateWhenRinging;
  private SwitchPreferenceCompat playDtmfTone;
  private ListPreference dtmfToneLength;
  private SwitchPreferenceCompat enableDndInCall;

  private NotificationManager notificationManager;

  @Override
  public Context getContext() {
    return getActivity();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setStorageDeviceProtected();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    getPreferenceManager().setStorageDeviceProtected();
    addPreferencesFromResource(R.xml.sound_settings);

    Context context = getActivity();

    ringtonePreference = findPreference(context.getString(R.string.ringtone_preference_key));
    vibrateWhenRinging = findPreference(context.getString(R.string.vibrate_on_preference_key));
    playDtmfTone = findPreference(context.getString(R.string.play_dtmf_preference_key));
    dtmfToneLength = findPreference(context.getString(R.string.dtmf_tone_length_preference_key));
    enableDndInCall = findPreference("incall_enable_dnd");

    if (hasVibrator()) {
      vibrateWhenRinging.setOnPreferenceChangeListener(this);
    } else {
      PreferenceScreen ps = getPreferenceScreen();
      Preference inCallVibrateOutgoing = findPreference(
              context.getString(R.string.incall_vibrate_outgoing_key));
      Preference inCallVibrateCallWaiting = findPreference(
              context.getString(R.string.incall_vibrate_call_waiting_key));
      Preference inCallVibrateHangup = findPreference(
              context.getString(R.string.incall_vibrate_hangup_key));
      Preference inCallVibrate45Secs = findPreference(
              context.getString(R.string.incall_vibrate_45_key));
      ps.removePreference(vibrateWhenRinging);
      ps.removePreference(inCallVibrateOutgoing);
      ps.removePreference(inCallVibrateCallWaiting);
      ps.removePreference(inCallVibrateHangup);
      ps.removePreference(inCallVibrate45Secs);
      vibrateWhenRinging = null;
    }

    playDtmfTone.setOnPreferenceChangeListener(this);
    playDtmfTone.setChecked(shouldPlayDtmfTone());

    enableDndInCall.setOnPreferenceChangeListener(this);

    TelephonyManager telephonyManager =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    if (telephonyManager.canChangeDtmfToneLength()
            && (telephonyManager.isWorldPhone() || !shouldHideCarrierSettings())) {
      dtmfToneLength.setOnPreferenceChangeListener(this);
      dtmfToneLength.setValueIndex(
              Settings.System.getInt(
                      context.getContentResolver(),
                      Settings.System.DTMF_TONE_TYPE_WHEN_DIALING,
                      DTMF_TONE_TYPE_NORMAL));
    } else {
      getPreferenceScreen().removePreference(dtmfToneLength);
      dtmfToneLength = null;
    }
    if (!CallRecorderService.isEnabled(getActivity())) {
      getPreferenceScreen().removePreference(
              findPreference(context.getString(R.string.call_recording_category_key)));
    } else {
      injectPerSimRecordingPreferences();
      injectSafetyBeepPreference();
      addConsentHandlers();
      addExclusionManagementShortcut();
    }
    notificationManager = context.getSystemService(NotificationManager.class);
  }

  private void injectSafetyBeepPreference() {
    PreferenceCategory cat = findPreference(getString(R.string.call_recording_category_key));
    if (cat == null) return;
    SwitchPreferenceCompat beep = new SwitchPreferenceCompat(getContext());
    beep.setKey(getString(R.string.call_recording_beep_key));
    beep.setTitle(R.string.call_recording_beep_title);
    beep.setDefaultValue(false);
    cat.addPreference(beep);
  }

  private void injectPerSimRecordingPreferences() {
    PreferenceCategory cat = findPreference(getString(R.string.call_recording_category_key));
    if (cat == null) return;
    SubscriptionManager sm = getContext().getSystemService(SubscriptionManager.class);
    List<SubscriptionInfo> infos = sm != null ? sm.getActiveSubscriptionInfoList() : null;
    if (infos == null || infos.isEmpty()) return;
    for (SubscriptionInfo info : infos) {
      String display = info.getDisplayName() != null ? info.getDisplayName().toString() :
              (info.getCarrierName() != null ? info.getCarrierName().toString() : "SIM");
      int subId = info.getSubscriptionId();
      SwitchPreferenceCompat perSim = new SwitchPreferenceCompat(getContext());
      perSim.setKey(perSimKey(subId));
      perSim.setTitle(getString(R.string.auto_record_calls_on_sim_title, display));
      perSim.setDefaultValue(true);
      cat.addPreference(perSim);
    }
  }

  public static String perSimKey(int subId) {
    return "call_recording_auto_sim_" + subId;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!Settings.System.canWrite(getContext())) {
      getActivity().onBackPressed();
      return;
    }

    if (vibrateWhenRinging != null) {
      vibrateWhenRinging.setChecked(shouldVibrateWhenRinging());
    }

    new Thread(ringtoneLookupRunnable).start();
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object objValue) {
    if (!Settings.System.canWrite(getContext())) {
      Toast.makeText(
              getContext(),
              getResources().getString(R.string.toast_cannot_write_system_settings),
              Toast.LENGTH_SHORT)
          .show();
      return true;
    }
    if (preference == vibrateWhenRinging) {
      boolean doVibrate = (Boolean) objValue;
      Settings.System.putInt(
          getActivity().getContentResolver(),
          Settings.System.VIBRATE_WHEN_RINGING,
          doVibrate ? DO_VIBRATION_FOR_CALLS : NO_VIBRATION_FOR_CALLS);
    } else if (preference == dtmfToneLength) {
      int index = dtmfToneLength.findIndexOfValue((String) objValue);
      Settings.System.putInt(
          getActivity().getContentResolver(), Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, index);
    } else if (preference == enableDndInCall) {
      boolean newValue = (Boolean) objValue;
      if (newValue && !notificationManager.isNotificationPolicyAccessGranted()) {
        new AlertDialog.Builder(getContext())
            .setMessage(R.string.incall_dnd_dialog_message)
            .setPositiveButton(R.string.allow, (dialog, which) -> {
              dialog.dismiss();
              Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
              startActivity(intent);
            })
            .setNegativeButton(R.string.deny, (dialog, which) -> dialog.dismiss())
            .show();
      }
    }
    return true;
  }

  private boolean shouldVibrateWhenRinging() {
    return Settings.System.getInt(
            getActivity().getContentResolver(),
            Settings.System.VIBRATE_WHEN_RINGING,
            NO_VIBRATION_FOR_CALLS) != NO_VIBRATION_FOR_CALLS;
  }

  private boolean shouldPlayDtmfTone() {
    return Settings.System.getInt(
            getActivity().getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING,
            PLAY_DTMF_TONE) != NO_DTMF_TONE;
  }

  private boolean hasVibrator() {
    Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    return vibrator != null && vibrator.hasVibrator();
  }

  private boolean shouldHideCarrierSettings() {
    CarrierConfigManager carrierConfig =
            (CarrierConfigManager) getActivity().getSystemService(Context.CARRIER_CONFIG_SERVICE);
    return carrierConfig.getConfig().getBoolean(
            CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL);
  }

  private void addConsentHandlers() {
    // Global auto-record toggle consent
    SwitchPreferenceCompat global = findPreference(getString(R.string.call_recording_auto_key));
    if (global != null) {
      global.setOnPreferenceChangeListener((pref, newValue) -> {
        if ((Boolean) newValue && !wasConsentShown()) {
          showConsentDialog(() -> {
            markConsentShown();
            global.setChecked(true);
          });
          return false;
        }
        return true;
      });
    }
    // Per-SIM consent
    SubscriptionManager sm = getContext().getSystemService(SubscriptionManager.class);
    List<SubscriptionInfo> infos = sm != null ? sm.getActiveSubscriptionInfoList() : null;
    if (infos != null) {
      for (SubscriptionInfo info : infos) {
        SwitchPreferenceCompat perSim = findPreference(perSimKey(info.getSubscriptionId()));
        if (perSim != null) {
          perSim.setOnPreferenceChangeListener((pref, newValue) -> {
            if ((Boolean) newValue && !wasConsentShown()) {
              showConsentDialog(() -> {
                markConsentShown();
                perSim.setChecked(true);
              });
              return false;
            }
            return true;
          });
        }
      }
    }
  }

  private void addExclusionManagementShortcut() {
    PreferenceCategory cat = findPreference(getString(R.string.call_recording_category_key));
    if (cat == null) return;
    Preference manage = new Preference(getContext());
    manage.setTitle(R.string.manage_recording_exclusions_title);
    manage.setSummary(getString(R.string.manage_recording_exclusions_summary, getExcludedCount()));
    manage.setOnPreferenceClickListener(p -> {
      // Placeholder: launch a future activity/fragment to manage exclusions
      Toast.makeText(getContext(), "Exclusion manager coming soon", Toast.LENGTH_SHORT).show();
      return true;
    });
    cat.addPreference(manage);
  }

  private boolean wasConsentShown() {
    return getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
            .getBoolean("call_recording_consent_shown", false);
  }

  private void markConsentShown() {
    getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("call_recording_consent_shown", true).apply();
  }

  private void showConsentDialog(Runnable onAccept) {
    new AlertDialog.Builder(getContext())
            .setTitle(R.string.recording_consent_title)
            .setMessage(R.string.recording_consent_message)
            .setPositiveButton(R.string.got_it, (d, w) -> {
              d.dismiss();
              onAccept.run();
            })
            .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
            .show();
  }

  private int getExcludedCount() {
    // Placeholder: store count under shared prefs key until manager is implemented
    return getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
            .getInt("call_recording_excluded_count", 0);
  }
}
