package br.usp.ime.thiagoko.loginuspnet;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

// PreferenceActivity without using fragments is deprecated for API 11+
@SuppressWarnings("deprecation")
public class LoginUSPnet extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private EditTextPreference mUsername = null;
	private SharedPreferences mPreferences = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.preferences);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mUsername = (EditTextPreference) getPreferenceScreen()
				.findPreference(this.getString(R.string.pref_username));

	}

	@Override
	protected void onResume() {
		super.onResume();

		mUsername.setSummary(mPreferences.getString(this.getString(R.string.pref_username), ""));

		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

	}

	@Override
	protected void onPause() {
		super.onPause();

		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

		if (key.equals(this.getString(R.string.pref_username))) {
			mUsername.setSummary(
					sharedPreferences.getString(this.getString(R.string.pref_username), ""));	
		}
	}

}
