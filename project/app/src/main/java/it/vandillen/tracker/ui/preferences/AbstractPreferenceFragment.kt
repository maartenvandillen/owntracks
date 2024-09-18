package it.vandillen.tracker.ui.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import javax.inject.Inject
import it.vandillen.tracker.R
import it.vandillen.tracker.preferences.PreferenceDataStoreShim
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.preferences.types.ConnectionMode

abstract class AbstractPreferenceFragment : PreferenceFragmentCompat() {
  @Inject lateinit var preferences: Preferences

  @Inject lateinit var preferenceDataStore: PreferenceDataStoreShim

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    preferenceManager.preferenceDataStore = preferenceDataStore
  }

  protected val connectionMode: String
    get() =
        when (preferences.mode) {
          ConnectionMode.HTTP -> getString(R.string.mode_http_private_label)
          ConnectionMode.FIRESTORE -> getString(R.string.mode_firestore_private_label)
          ConnectionMode.MQTT -> getString(R.string.mode_mqtt_private_label)
        }
}
