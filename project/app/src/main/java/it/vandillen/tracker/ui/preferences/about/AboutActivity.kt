package it.vandillen.tracker.ui.preferences.about

import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import it.vandillen.tracker.ui.preferences.PreferencesActivity

@AndroidEntryPoint
class AboutActivity : PreferencesActivity() {
  override val startFragment: Fragment
    get() = AboutFragment()
}
