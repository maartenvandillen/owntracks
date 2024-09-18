package it.vandillen.tracker.ui.preferences

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import it.vandillen.tracker.R

@AndroidEntryPoint
class MapFragment @Inject constructor() : AbstractPreferenceFragment() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_map, rootKey)
  }
}
