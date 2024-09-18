package it.vandillen.tracker.e2e

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.Duration.Companion.seconds
import mqtt.packets.mqtt.MQTTPublish
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import it.vandillen.tracker.R
import it.vandillen.tracker.model.Parser
import it.vandillen.tracker.model.messages.MessageLocation
import it.vandillen.tracker.services.BackgroundService
import it.vandillen.tracker.testutils.GPSMockDeviceLocation
import it.vandillen.tracker.testutils.MockDeviceLocation
import it.vandillen.tracker.testutils.TestWithAnActivity
import it.vandillen.tracker.testutils.TestWithAnMQTTBroker
import it.vandillen.tracker.testutils.TestWithAnMQTTBrokerImpl
import it.vandillen.tracker.testutils.grantMapActivityPermissions
import it.vandillen.tracker.testutils.matchers.withActionIconDrawable
import it.vandillen.tracker.testutils.setNotFirstStartPreferences
import it.vandillen.tracker.testutils.use
import it.vandillen.tracker.testutils.waitUntilActivityVisible
import it.vandillen.tracker.ui.clickOnAndWait
import it.vandillen.tracker.ui.map.MapActivity
import timber.log.Timber

@OptIn(ExperimentalUnsignedTypes::class)
@RunWith(AndroidJUnit4::class)
class IntentTests :
    TestWithAnActivity<MapActivity>(MapActivity::class.java, false),
    TestWithAnMQTTBroker by TestWithAnMQTTBrokerImpl(),
    MockDeviceLocation by GPSMockDeviceLocation() {

  @Test
  fun given_an_application_instance_when_sending_a_send_location_intent_then_a_location_message_is_published_with_the_user_trigger() {
    setupTestActivity()

    initializeMockLocationProvider(app)
    app.mockLocationIdlingResource.setIdleState(false)

    waitUntilActivityVisible<MapActivity>()
    clickOnAndWait(R.id.menu_monitoring)
    clickOnAndWait(R.id.fabMonitoringModeMove)
    setMockLocation(51.0, 0.0)

    app.mockLocationIdlingResource.use(15.seconds) { clickOnAndWait(R.id.fabMyLocation) }

    packetReceivedIdlingResource.latch("\"t\":\"u\"")
    ContextCompat.startForegroundService(
        app,
        Intent(app, BackgroundService::class.java).apply {
          action = "it.vandillen.tracker.SEND_LOCATION_USER"
        })
    packetReceivedIdlingResource.use(10.seconds) { Espresso.onIdle() }

    Assert.assertTrue(
        mqttPacketsReceived
            .also { Timber.v("MQTT Packets received: $it") }
            .filterIsInstance<MQTTPublish>()
            .map { Parser(null).fromJson((it.payload)!!.toByteArray()) }
            .also { Timber.w("packets: $it") }
            .any {
              it is MessageLocation &&
                  it.trigger == MessageLocation.ReportType.USER &&
                  it.latitude == 51.0
            })
  }

  @Test
  fun given_an_application_instance_when_sending_a_change_monitoring_intent_with_no_specific_mode_then_the_app_changes_monitoring_mode_to_the_next() {
    setupTestActivity()

    app.preferenceSetIdlingResource.setIdleState(false)
    ContextCompat.startForegroundService(
        app,
        Intent(app, BackgroundService::class.java).apply {
          action = "it.vandillen.tracker.CHANGE_MONITORING"
        })
    app.preferenceSetIdlingResource.use {
      onView(ViewMatchers.withId(R.id.menu_monitoring))
          .check(ViewAssertions.matches(withActionIconDrawable(R.drawable.ic_step_forward_2)))
    }
  }

  @Test
  fun given_an_application_instance_when_sending_a_change_monitoring_intent_with_quiet_mode_specified_then_the_app_changes_monitoring_mode_to_quiet() {
    setupTestActivity()
    app.preferenceSetIdlingResource.setIdleState(false)
    ContextCompat.startForegroundService(
        app,
        Intent(app, BackgroundService::class.java).apply {
          action = "it.vandillen.tracker.CHANGE_MONITORING"
          putExtra("monitoring", -1)
        })
    app.preferenceSetIdlingResource.use {
      onView(ViewMatchers.withId(R.id.menu_monitoring))
          .check(ViewAssertions.matches(withActionIconDrawable(R.drawable.ic_baseline_stop_36)))
    }
  }

  private fun setupTestActivity() {
    setNotFirstStartPreferences()
    launchActivity()
    grantMapActivityPermissions()
    configureMQTTConnectionToLocalWithGeneratedPassword()
    waitUntilActivityVisible<MapActivity>()
  }
}
