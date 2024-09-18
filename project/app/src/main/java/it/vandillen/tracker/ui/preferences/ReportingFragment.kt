package it.vandillen.tracker.ui.preferences

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import it.vandillen.tracker.R

@AndroidEntryPoint
class ReportingFragment : AbstractPreferenceFragment() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    setPreferencesFromResource(R.xml.preferences_reporting, rootKey)
  }
}
