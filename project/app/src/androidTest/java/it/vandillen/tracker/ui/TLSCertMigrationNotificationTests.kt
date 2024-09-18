package it.vandillen.tracker.ui

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import it.vandillen.tracker.R
import it.vandillen.tracker.testutils.TestWithAnActivity
import it.vandillen.tracker.testutils.getPreferences
import it.vandillen.tracker.testutils.grantMapActivityPermissions
import it.vandillen.tracker.testutils.setNotFirstStartPreferences
import it.vandillen.tracker.ui.map.MapActivity
import timber.log.Timber

@MediumTest
@RunWith(AndroidJUnit4::class)
class TLSCertMigrationNotificationTests :
    TestWithAnActivity<MapActivity>(MapActivity::class.java, false) {

  @Test
  fun whenMigrationALegacyTLSCAPreferenceThenTheNotificationIsShown() {
    InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences("it.vandillen.tracker.preferences.private", Context.MODE_PRIVATE)
        .edit()
        .putString("tlsCaCrt", "TestValue")
        .apply()
    setNotFirstStartPreferences()
    app.migratePreferences()
    launchActivity()
    grantMapActivityPermissions()

    val notificationManager =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    Assert.assertTrue(
        "Event notification is displayed",
        notificationManager.activeNotifications
            .map { it.notification }
            .also { notifications ->
              notifications
                  .map { it.extras.getString(Notification.EXTRA_TITLE) }
                  .run { Timber.i("Current Notifications: $this") }
            }
            .any { notification ->
              notification.extras.getString(Notification.EXTRA_TITLE) ==
                  app.getString(R.string.certificateMigrationRequiredNotificationTitle) &&
                  notification.extras.getString(Notification.EXTRA_BIG_TEXT) ==
                      app.getString(R.string.certificateMigrationRequiredNotificationText)
            })
  }

  @Test
  fun whenMigratingACurrentTLSCAPreferenceThenTheNotificationIsShown() {
    getPreferences().edit().putString("tlsCaCrt", "TestValue").apply()
    setNotFirstStartPreferences()
    app.migratePreferences()
    launchActivity()
    grantMapActivityPermissions()

    val notificationManager =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    Assert.assertTrue(
        "Event notification is displayed",
        notificationManager.activeNotifications
            .map { it.notification }
            .also { notifications ->
              notifications
                  .map { it.extras.getString(Notification.EXTRA_TITLE) }
                  .run { Timber.i("Current Notifications: $this") }
            }
            .any { notification ->
              notification.extras.getString(Notification.EXTRA_TITLE) ==
                  app.getString(R.string.certificateMigrationRequiredNotificationTitle) &&
                  notification.extras.getString(Notification.EXTRA_BIG_TEXT) ==
                      app.getString(R.string.certificateMigrationRequiredNotificationText)
            })
  }
}
