package it.vandillen.tracker.ui.map

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertNotExist
import com.adevinta.android.barista.interaction.BaristaDialogInteractions.clickDialogNegativeButton
import org.junit.Test
import org.junit.runner.RunWith
import it.vandillen.tracker.R
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.testutils.TestWithAnActivity
import it.vandillen.tracker.testutils.disableDeviceLocation
import it.vandillen.tracker.testutils.enableDeviceLocation
import it.vandillen.tracker.testutils.grantMapActivityPermissions
import it.vandillen.tracker.testutils.setNotFirstStartPreferences

@LargeTest
@RunWith(AndroidJUnit4::class)
class OSSMapActivityTests : TestWithAnActivity<MapActivity>(MapActivity::class.java, false) {
  @Test
  fun welcomeActivityShouldNotRunWhenFirstStartPreferencesSet() {
    setNotFirstStartPreferences()
    launchActivity()
    grantMapActivityPermissions()
    assertDisplayed(R.id.osm_map_view)
  }

  @Test
  fun mapActivityShouldPromptForLocationServicesOnFirstTime() {
    try {
      disableDeviceLocation()
      setNotFirstStartPreferences()
      launchActivity()
      grantMapActivityPermissions()
      assertDisplayed(R.string.deviceLocationDisabledDialogTitle)
      clickDialogNegativeButton()
      assertDisplayed(R.id.osm_map_view)
    } finally {
      disableDeviceLocation()
    }
  }

  @Test
  fun mapActivityShouldNotPromptForLocationServicesIfPreviouslyDeclined() {
    try {
      disableDeviceLocation()
      setNotFirstStartPreferences()
      PreferenceManager.getDefaultSharedPreferences(
              InstrumentationRegistry.getInstrumentation().targetContext)
          .edit()
          .putBoolean(Preferences::userDeclinedEnableLocationServices.name, true)
          .apply()
      launchActivity()
      grantMapActivityPermissions()
      assertNotExist(R.string.deviceLocationDisabledDialogTitle)
      assertDisplayed(R.id.osm_map_view)
    } finally {
      enableDeviceLocation()
    }
  }
}
