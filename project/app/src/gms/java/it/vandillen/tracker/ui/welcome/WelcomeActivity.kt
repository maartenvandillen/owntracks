package it.vandillen.tracker.ui.welcome

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import it.vandillen.tracker.ui.welcome.fragments.PlayFragment

@AndroidEntryPoint
class WelcomeActivity : BaseWelcomeActivity() {
  @Inject lateinit var playFragment: PlayFragment

  override val fragmentList by lazy {
    listOf(
        introFragment,
        connectionSetupFragment,
        locationPermissionFragment,
        notificationPermissionFragment,
        playFragment,
        finishFragment)
  }

  @Deprecated("Deprecated in Java")
  @Suppress("DEPRECATION")
  public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == PlayFragment.PLAY_SERVICES_RESOLUTION_REQUEST) {
      playFragment.onPlayServicesResolutionResult()
    }
  }
}
