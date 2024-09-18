package it.vandillen.tracker.ui.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import it.vandillen.tracker.R

class LicenseFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_licenses, rootKey)
  }
}
