package it.vandillen.tracker.ui.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.setPadding
import androidx.core.widget.ImageViewCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import it.vandillen.tracker.R
import it.vandillen.tracker.databinding.UiMapBinding
import it.vandillen.tracker.location.roundForDisplay
import it.vandillen.tracker.model.Contact
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.preferences.types.MonitoringMode
import it.vandillen.tracker.services.BackgroundService
import it.vandillen.tracker.services.BackgroundService.Companion.BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG
import it.vandillen.tracker.support.ContactImageBindingAdapter
import it.vandillen.tracker.support.DeviceMetricsProvider
import it.vandillen.tracker.support.RequirementsChecker
import it.vandillen.tracker.test.CountingIdlingResourceShim
import it.vandillen.tracker.test.SimpleIdlingResource
import it.vandillen.tracker.ui.NotificationsStash
import it.vandillen.tracker.ui.mixins.BackgroundLocationPermissionRequester
import it.vandillen.tracker.ui.mixins.LocationPermissionRequester
import it.vandillen.tracker.ui.mixins.NotificationPermissionRequester
import it.vandillen.tracker.ui.mixins.ServiceStarter
import it.vandillen.tracker.ui.mixins.WorkManagerInitExceptionNotifier
import it.vandillen.tracker.ui.welcome.WelcomeActivity
import it.vandillen.tracker.data.repos.EndpointStateRepo
import it.vandillen.tracker.net.firestore.FcmTokenManager
import timber.log.Timber

@AndroidEntryPoint
class MapActivity :
    AppCompatActivity(),
    View.OnClickListener,
    View.OnLongClickListener,
    WorkManagerInitExceptionNotifier by WorkManagerInitExceptionNotifier.Impl(),
    ServiceStarter by ServiceStarter.Impl(), Preferences.OnPreferenceChangeListener {

  private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager }
  private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
  private val viewModel: MapViewModel by viewModels()
  private val notificationPermissionRequester =
      NotificationPermissionRequester(
          this, ::notificationPermissionGranted, ::notificationPermissionDenied)
  private val locationPermissionRequester =
      LocationPermissionRequester(this, ::locationPermissionGranted, ::locationPermissionDenied)
  private val backgroundLocationPermissionRequester =
      BackgroundLocationPermissionRequester(
          this, ::backgroundLocationPermissionGranted, ::backgroundLocationPermissionDenied)
  private var service: BackgroundService? = null
  private var bottomSheetBehavior: BottomSheetBehavior<LinearLayoutCompat>? = null
  private var menu: Menu? = null
  private var sensorManager: SensorManager? = null
  private var orientationSensor: Sensor? = null
  private lateinit var binding: UiMapBinding

  // FCM refresh state
  private var isRefreshingFcm: Boolean = false

  // Cache last sent timestamp for periodic/icon refresh
  private var lastSentMillisCache: Long? = null

  private lateinit var locationServicesAlertDialog: AlertDialog

  @Inject lateinit var notificationsStash: NotificationsStash

  @Inject lateinit var contactImageBindingAdapter: ContactImageBindingAdapter

  @Inject
  @Named("outgoingQueueIdlingResource")
  @get:VisibleForTesting
  lateinit var outgoingQueueIdlingResource: CountingIdlingResourceShim

  @Inject
  @Named("publishResponseMessageIdlingResource")
  @get:VisibleForTesting
  lateinit var publishResponseMessageIdlingResource: SimpleIdlingResource

  @Inject
  @Named("importConfigurationIdlingResource")
  @get:VisibleForTesting
  lateinit var importConfigurationIdlingResource: SimpleIdlingResource

  @Inject lateinit var requirementsChecker: RequirementsChecker

  @Inject lateinit var preferences: Preferences

  @Inject lateinit var drawerProvider: it.vandillen.tracker.support.DrawerProvider

  @Inject lateinit var deviceMetricsProvider: DeviceMetricsProvider

  @Inject lateinit var endpointStateRepo: EndpointStateRepo

  private val serviceConnection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          Timber.d("MapActivity connected to service")
          this@MapActivity.service = (service as BackgroundService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          Timber.d("Service disconnected from MapActivity")
          service = null
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    EntryPointAccessors.fromActivity(this, MapActivityEntryPoint::class.java).let {
      supportFragmentManager.fragmentFactory = it.fragmentFactory
    }

    super.onCreate(savedInstanceState)

    if (!preferences.setupCompleted) {
      startActivity(Intent(this, WelcomeActivity::class.java))
      finish()
      return
    }

    binding =
        DataBindingUtil.setContentView<UiMapBinding>(this, R.layout.ui_map).apply {
          vm = viewModel
          lifecycleOwner = this@MapActivity
          appbar.toolbar.run {
            setSupportActionBar(this)
            drawerProvider.attach(this)
          }
          supportActionBar?.setDisplayShowTitleEnabled(false)
          bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
          contactPeek.contactRow.setOnClickListener(this@MapActivity)
          contactPeek.contactRow.setOnLongClickListener(this@MapActivity)

          // Make FCM row clickable to trigger refresh (only when invalid)
          statusFcmRow.setOnClickListener {
            if (!isRefreshingFcm && statusFcmRow.isEnabled) {
              isRefreshingFcm = true
              statusFcmProgress.visibility = View.VISIBLE
              statusFcmIcon.visibility = View.INVISIBLE
              // Trigger refresh via FcmTokenManager which will emit to EndpointStateRepo
              FcmTokenManager(applicationContext, lifecycleScope, endpointStateRepo)
                .refreshToken()
                .addOnCompleteListener { t ->
                  // Hide spinner regardless; success path will also be hidden on collector
                  statusFcmProgress.visibility = View.GONE
                  statusFcmIcon.visibility = View.VISIBLE
                  isRefreshingFcm = false
                }
            }
          }

          contactClearButton.setOnClickListener { viewModel.onClearContactClicked() }
          contactShareButton.setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent().apply {
                      action = Intent.ACTION_SEND
                      type = "text/plain"
                      putExtra(
                          Intent.EXTRA_TEXT,
                          viewModel.currentContact.value?.run {
                            getString(
                                R.string.shareContactBody,
                                this.displayName,
                                this.geocodedLocation,
                                this.latLng?.toDisplayString() ?: "",
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.ofEpochSecond(this.locationTimestamp)))
                          } ?: R.string.na)
                    },
                    "Share Location"))
          }

          contactNavigateButton.setOnClickListener { navigateToCurrentContact() }

          // Need to set the appbar layout behaviour to be non-drag, so that we can drag the map
          AppBarLayout.Behavior()
              .setDragCallback(
                  object : AppBarLayout.Behavior.DragCallback() {
                    override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                      return false
                    }
                  })

          fabMyLocation.apply {
            TooltipCompat.setTooltipText(this, getString(R.string.currentLocationButtonLabel))
            setOnClickListener {
              if (checkAndRequestLocationPermissions(true) ==
                  CheckPermissionsResult.HAS_PERMISSIONS) {
                checkAndRequestLocationServicesEnabled(true)
              }
              if (viewModel.myLocationStatus.value != MyLocationStatus.DISABLED) {
                viewModel.onMyLocationClicked()
              }
            }
          }

          fabMapLayers.apply {
            TooltipCompat.setTooltipText(this, getString(R.string.mapLayerDialogTitle))
            setOnClickListener {
              MapLayerBottomSheetDialog().show(supportFragmentManager, "layerBottomSheetDialog")
            }
          }

          val labels =
              listOf(
                      R.id.contactDetailsAccuracy,
                      R.id.contactDetailsAltitude,
                      R.id.contactDetailsBattery,
                      R.id.contactDetailsBearing,
                      R.id.contactDetailsSpeed,
                      R.id.contactDetailsDistance)
                  .map { bottomSheetLayout.findViewById<View>(it) }
                  .map { it.findViewById<AutoResizingTextViewWithListener>(R.id.label) }

          object : AutoResizingTextViewWithListener.OnTextSizeChangedListener {
                @SuppressLint("RestrictedApi")
                override fun onTextSizeChanged(view: View, newSize: Float) {
                  labels
                      .filter { it != view }
                      .filter { it.textSize > newSize || it.configurationChangedFlag }
                      .forEach {
                        it.setAutoSizeTextTypeUniformWithPresetSizes(
                            intArrayOf(newSize.toInt()), TypedValue.COMPLEX_UNIT_PX)
                        it.configurationChangedFlag = false
                      }
                }
              }
              .also { listener -> labels.forEach { it.withListener(listener) } }
        }

    setBottomSheetHidden()

    viewModel.currentContact.observe(this) { contact: Contact? ->
      contact?.let {
        binding.contactPeek.run {
          image.setImageResource(0) // Remove old image before async loading the new one
          lifecycleScope.launch {
            contactImageBindingAdapter.run { image.setImageBitmap(getBitmapFromCache(it)) }
          }
        }
      }
    }
    viewModel.bottomSheetHidden.observe(this) { o: Boolean? ->
      if (o == null || o) {
        setBottomSheetHidden()
      } else {
        setBottomSheetCollapsed()
      }
    }
    viewModel.currentLocation.observe(this) { location ->
      if (location == null) {
        disableLocationMenus()
        binding.statusGps.text = "GPS: NO FIX"
        binding.gpsIcon.setImageResource(R.drawable.baseline_clear_24)
        ImageViewCompat.setImageTintList(binding.gpsIcon, ColorStateList.valueOf(resources.getColor(R.color.log_error_tag_color, theme)))
      } else {
        enableLocationMenus()
        binding.vm?.run { updateActiveContactDistanceAndBearing(location) }
        val acc = try { String.format("%.1f", location.accuracy) } catch (e: Exception) { "-" }
        binding.statusGps.text = "GPS: Fix OK (${acc} m accuracy)"
        binding.gpsIcon.setImageResource(R.drawable.ic_baseline_done_24)
        ImageViewCompat.setImageTintList(binding.gpsIcon, ColorStateList.valueOf(resources.getColor(R.color.log_info_tag_color, theme)))
      }
    }
    viewModel.currentMonitoringMode.observe(this) { updateMonitoringModeMenu() }

    // Wire up status overlay values
    lifecycleScope.launch {
      // initial connection status
      launch {
        val conn = deviceMetricsProvider.connectionType
        val isOnline = !conn.equals("offline", ignoreCase = true)
        updateConnectionStatus(isOnline)
      }

      // device id
      binding.statusDeviceId.text = "Device: ${preferences.deviceId}"

      // tenant
      updateTenant()

      // Unique ID updates
      launch {
        endpointStateRepo.firestoreUniqueId.collectLatest { uid ->
          val valid = uid.isNotBlank() && !uid.contains("UNKNOWN", ignoreCase = true)
          binding.statusUniqueId.text = if (uid.isBlank()) "ID: -" else "ID: ${uid.take(24)}"
          binding.uniqueIdIcon.setImageResource(
            if (valid) R.drawable.ic_baseline_done_24 else R.drawable.baseline_clear_24
          )
          val tint = if (valid)
            resources.getColor(R.color.log_info_tag_color, theme)
          else
            resources.getColor(R.color.log_error_tag_color, theme)
          ImageViewCompat.setImageTintList(binding.uniqueIdIcon, ColorStateList.valueOf(tint))
        }
      }

      // FCM token updates
      launch {
        endpointStateRepo.firestoreFcmToken.collectLatest { token ->
          val valid = !token.contains("UNKNOWN", ignoreCase = true) && token.isNotBlank()
          binding.statusFcm.text = "FCM: ${token.take(24)}"
          binding.statusFcmIcon.setImageResource(
            if (valid) R.drawable.ic_baseline_done_24 else R.drawable.baseline_clear_24
          )
          val fcmTint = if (valid)
            resources.getColor(R.color.log_info_tag_color, theme)
          else
            resources.getColor(R.color.log_error_tag_color, theme)
          ImageViewCompat.setImageTintList(binding.statusFcmIcon, ColorStateList.valueOf(fcmTint))
          // Enable click only when token invalid
          binding.statusFcmRow.isEnabled = !valid
          binding.statusFcmRow.alpha = if (!valid) 1.0f else 0.6f
          // Hide progress and reset state when we receive an update
          binding.statusFcmProgress.visibility = View.GONE
          binding.statusFcmIcon.visibility = View.VISIBLE
          isRefreshingFcm = false
        }
      }

      // last sent timestamp updates with periodic refresh

      // Collect updates to last sent timestamp
      launch {
        endpointStateRepo.firestoreLastSentMillis.collectLatest { ts ->
          lastSentMillisCache = ts
          updateLastSentUi(ts)
        }
      }
      // Initialize UI if we have a cached value already (e.g., after rotation)
      updateLastSentUi(lastSentMillisCache)

      // Periodically refresh the icon based on current time and interval, even without new ts
      launch {
        while (true) {
          delay(2L * preferences.moveModeLocatorInterval * 1000L)
          updateLastSentUi(lastSentMillisCache)
        }
      }

      // Initial interval display
      binding.statusInterval.text = "Interval: ${preferences.moveModeLocatorInterval} s"
    }

    startService(this)

    // We've been started in the foreground, so cancel the background restriction notification
    NotificationManagerCompat.from(this).cancel(BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG, 0)

    notifyOnWorkManagerInitFailure(this)

    onBackPressedDispatcher.addCallback(this) {
      if (bottomSheetBehavior == null) {
        finish()
      } else {
        when (bottomSheetBehavior?.state) {
          BottomSheetBehavior.STATE_HIDDEN -> finish()
          BottomSheetBehavior.STATE_COLLAPSED -> {
            setBottomSheetHidden()
          }
          BottomSheetBehavior.STATE_DRAGGING -> {
            // Noop
          }
          BottomSheetBehavior.STATE_EXPANDED -> {
            setBottomSheetCollapsed()
          }
          BottomSheetBehavior.STATE_HALF_EXPANDED -> {
            setBottomSheetCollapsed()
          }
          BottomSheetBehavior.STATE_SETTLING -> {
            // Noop
          }
        }
      }
    }
  }

  private fun updateConnectionStatus(isOnline: Boolean) {
    val state = if (isOnline) "ONLINE" else "OFFLINE"
    binding.statusConnection.text = "Internet: $state"
    binding.statusConnectionIcon.setImageResource(
      if (isOnline) R.drawable.ic_baseline_done_24 else R.drawable.baseline_clear_24
    )
    val tint = if (isOnline)
      resources.getColor(R.color.log_info_tag_color, theme)
    else
      resources.getColor(R.color.log_error_tag_color, theme)
    ImageViewCompat.setImageTintList(binding.statusConnectionIcon, ColorStateList.valueOf(tint))
  }

  private fun updateTenant() {
    val tenant = "${preferences.tid?.uppercase() ?: ""}"
    val tenantValid = tenant.length == 2
    binding.tenantIcon.setImageResource(
      if (tenantValid) R.drawable.ic_baseline_done_24 else R.drawable.baseline_clear_24
    )
    val tenantTint = if (tenantValid)
      resources.getColor(R.color.log_info_tag_color, theme)
    else
      resources.getColor(R.color.log_error_tag_color, theme)
    ImageViewCompat.setImageTintList(binding.tenantIcon, ColorStateList.valueOf(tenantTint))
    binding.statusTenant.text = "Tenant: ${tenant}"
  }

  private fun updateLastSentUi(ts: Long?) {
    val text = if (ts == null) "Last sent: -" else {
      val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
      "Last sent: ${fmt.format(Instant.ofEpochMilli(ts))}"
    }
    binding.statusLastSent.text = text

    val iconRes: Int
    val tintColor: Int
    if (ts == null) {
      iconRes = R.drawable.baseline_clear_24
      tintColor = resources.getColor(R.color.log_error_tag_color, theme)
    } else {
      val now = System.currentTimeMillis()
      val thresholdMillis = 2L * preferences.moveModeLocatorInterval * 1000L
      val age = now - ts
      if (age in 0..thresholdMillis) {
        iconRes = R.drawable.ic_baseline_done_24
        tintColor = resources.getColor(R.color.log_info_tag_color, theme)
      } else {
        iconRes = R.drawable.ic_baseline_warning_24
        tintColor = resources.getColor(R.color.log_warning_tag_color, theme)
      }
    }
    binding.statusLastSentIcon.setImageResource(iconRes)
    ImageViewCompat.setImageTintList(binding.statusLastSentIcon, ColorStateList.valueOf(tintColor))
  }

  override fun onPreferenceChanged(properties: Set<String>) {
    // Update interval live when preference changes
    if (properties.contains(Preferences::moveModeLocatorInterval.name)) {
      runOnUiThread {
        binding.statusInterval.text = "Interval: ${preferences.moveModeLocatorInterval} s"
        // Also refresh lastSent icon state as threshold depends on interval
        updateLastSentUi(lastSentMillisCache)
      }
    }

    // Update device id in status overlay when changed
    if (properties.contains(Preferences::deviceId.name)) {
      runOnUiThread {
        binding.statusDeviceId.text = "Device: ${preferences.deviceId}"
      }
    }

    // Update tenant in status overlay when changed
    if (properties.contains(Preferences::tid.name)) {
      runOnUiThread { updateTenant() }
    }
  }

  private fun navigateToCurrentContact() {
    viewModel.currentContact.value?.latLng?.apply {
      try {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "google.navigation:q=${latitude.value.roundForDisplay()},${longitude.value.roundForDisplay()}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
      } catch (e: ActivityNotFoundException) {
        Snackbar.make(
                binding.mapCoordinatorLayout,
                getString(R.string.noNavigationApp),
                Snackbar.LENGTH_SHORT)
            .show()
      }
    }
        ?: run {
          Snackbar.make(
                  binding.mapCoordinatorLayout,
                  getString(R.string.contactLocationUnknown),
                  Snackbar.LENGTH_SHORT)
              .show()
        }
  }

  private val locationServicesLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // We have to check permissions again here, because it may have been revoked in the
        // period between asking for location services and returning here.
        Timber.d("Location services callback, result=$it")
        if (checkAndRequestLocationPermissions(false) == CheckPermissionsResult.HAS_PERMISSIONS) {
          viewModel.requestLocationUpdatesForBlueDot()
        }
      }

  /**
   * Performs a check that the device has location services enabled. This can be called either
   * because the user has explicitly done something that requires the location services (clicked on
   * the MyLocation FAB), or because some other action has happened and we need to re-check that the
   * location service is enabled (e.g. onResume, or location has become unavailable).
   *
   * This check may trigger a request and prompt the user to enable location services. This prompt
   * should be raised either if it's an explicit request, or if the user hasn't previously declined
   * to enable location services.
   *
   * @param explicitUserAction Indicates whether or not the user has triggered something explicitly
   *   causing a location services check
   * @return indication as to whether location services are enabled. This will return false
   *   immediately if a prompt to enable to raised, even if the user says "yes" to the prompt.
   */
  private fun checkAndRequestLocationServicesEnabled(explicitUserAction: Boolean): Boolean {
    Timber.d("Check and request location services")
    return if (!requirementsChecker.isLocationServiceEnabled()) {
      Timber.d("Location Services disabled")
      if ((explicitUserAction || !preferences.userDeclinedEnableLocationServices)) {
        Timber.d("Showing location services dialog")
        MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setIcon(R.drawable.ic_baseline_location_disabled_24)
            .setTitle(getString(R.string.deviceLocationDisabledDialogTitle))
            .setMessage(getString(R.string.deviceLocationDisabledDialogMessage))
            .setPositiveButton(
                getString(R.string.deviceLocationDisabledDialogPositiveButtonLabel)) { _, _ ->
                  locationServicesLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
              preferences.userDeclinedEnableLocationServices = true
            }
            .show()
      } else {
        Timber.d(
            "Not requesting location services. " +
                "Explicit=false, previously declined=${preferences.userDeclinedEnableLocationServices}")
      }
      false
    } else {
      Timber.d("Location services enabled")
      true
    }
  }

  /** User has granted notification permission. Notify everything that's in the NotificationStash */
  private fun notificationPermissionGranted() {
    Timber.d("Notification permission granted. Showing all notifications in stash")
    notificationsStash.showAll(NotificationManagerCompat.from(this))
  }

  /**
   * User has declined notification permissions. Log this in the preferences so we don't keep asking
   * them
   */
  private fun notificationPermissionDenied() {
    Timber.d("Notification permission denied")
    preferences.userDeclinedEnableNotificationPermissions = true
  }

  /**
   * User has granted permission. If It's location, ask the viewmodel to start requesting locations,
   * set the [MapViewModel.ViewMode] to [MapViewModel.ViewMode.Device] and tell the service to
   * reinitialize locations.
   */
  private fun locationPermissionGranted(code: Int) {
    Timber.d("Location Permission granted")
    if (code == EXPLICIT_LOCATION_PERMISSION_REQUEST) {
      Timber.d("Location Permission was explicitly asked for, check location services")
      checkAndRequestLocationServicesEnabled(true)
    }
    viewModel.requestLocationUpdatesForBlueDot()
    viewModel.onMyLocationClicked()
    viewModel.updateMyLocationStatus()
    service?.reInitializeLocationRequests()
  }

  /**
   * User has declined to enable location permissions. [Snackbar] the user with the option of trying
   * again (in case they didn't mean to).
   */
  private fun locationPermissionDenied(@Suppress("UNUSED_PARAMETER") code: Int) {
    Timber.d("Location Permission denied. Showing snackbar")
    preferences.userDeclinedEnableLocationPermissions = true
    Snackbar.make(
            binding.mapCoordinatorLayout,
            getString(R.string.locationPermissionNotGrantedNotification),
            Snackbar.LENGTH_LONG)
        .setAction(getString(R.string.fixProblemLabel)) {
          startActivity(
              Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
              })
        }
        .show()
  }

  /**
   * User has declined to enable background location permissions. Log this in the preferences so we
   * don't keep asking
   */
  private fun backgroundLocationPermissionGranted() {
    Timber.d("Background location permission granted")
    preferences.userDeclinedEnableBackgroundLocationPermissions = false
  }

  /**
   * User has declined to enable background location permissions. Log this in the preferences so we
   * don't keep asking.
   *
   * This may have been called by the user calling cancel on the material dialog in the onResume
   * flow so we need to check for location services next
   */
  private fun backgroundLocationPermissionDenied() {
    Timber.d("Background location permission denied")
    preferences.userDeclinedEnableBackgroundLocationPermissions = true
    if (checkAndRequestLocationServicesEnabled(false)) {
      viewModel.requestLocationUpdatesForBlueDot()
    }
  }

  enum class CheckPermissionsResult {
    HAS_PERMISSIONS,
    NO_PERMISSIONS_LAUNCHED_REQUEST,
    NO_PERMISSIONS_NOT_LAUNCHED_REQUEST
  }

  private fun checkAndRequestNotificationPermissions(): CheckPermissionsResult {
    Timber.d("Checking and requesting notification permissions")
    return if (!notificationPermissionRequester.hasPermission()) {
      Timber.d("No notification permission")
      if (!preferences.userDeclinedEnableNotificationPermissions) {
        Timber.d("Requesting notification permissions")
        notificationPermissionRequester.requestNotificationPermission()
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST
      } else {
        Timber.d(
            "Not request location permissions. " +
                "previouslyDeclined=${preferences.userDeclinedEnableNotificationPermissions}")
        CheckPermissionsResult.NO_PERMISSIONS_NOT_LAUNCHED_REQUEST
      }
    } else {
      CheckPermissionsResult.HAS_PERMISSIONS
    }
  }

  /**
   * Performs a check that the user has granted location permissions. This can be called either
   * because the user has explicitly done something that requires location permissions (clicked on
   * the MyLocation FAB), or because some other action has happened and we need to re-check location
   * permissions (e.g. user has enabled location services).
   *
   * This check may trigger a request and prompt the user to grant location permissions. This prompt
   * should be raised either if it's an explicit request for something that needs permissions, or if
   * the user hasn't previously denied location permission.
   *
   * @param explicitUserAction Indicates whether or not the user has triggered something explicitly
   *   causing a permissions check
   * @return indication as to whether location permissions have been granted. This will return false
   *   immediately if a prompt to enable to raised, even if the user says "yes" to the prompt.
   */
  private fun checkAndRequestLocationPermissions(
      explicitUserAction: Boolean
  ): CheckPermissionsResult {
    Timber.d("Checking and requesting location permissions")
    return if (!requirementsChecker.hasLocationPermissions()) {
      Timber.d("No location permission")
      // We don't have location permission
      if ((explicitUserAction || !preferences.userDeclinedEnableLocationPermissions)) {
        Timber.d("Requesting location permissions, explicit=$explicitUserAction")
        locationPermissionRequester.requestLocationPermissions(
            if (explicitUserAction) {
              EXPLICIT_LOCATION_PERMISSION_REQUEST
            } else {
              IMPLICIT_LOCATION_PERMISSION_REQUEST
            },
            this) {
              shouldShowRequestPermissionRationale(it)
            }
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST
      } else {
        Timber.d(
            "Not request location permissions. " +
                "Explicit action=false, previouslyDeclined=${preferences.userDeclinedEnableLocationPermissions}")
        CheckPermissionsResult.NO_PERMISSIONS_NOT_LAUNCHED_REQUEST
      }
    } else {
      CheckPermissionsResult.HAS_PERMISSIONS
    }
  }

  private fun checkAndRequestBackgroundLocationPermissions(): CheckPermissionsResult {
    Timber.d("Checking and requesting background location permissions")
    return if (!requirementsChecker.hasBackgroundLocationPermission()) {
      Timber.d("No background location permission")
      if (!preferences.userDeclinedEnableBackgroundLocationPermissions &&
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Timber.d("Requesting background location permissions")
        backgroundLocationPermissionRequester.requestLocationPermissions(this) { true }
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST
      } else {
        Timber.d("Not requesting background location permission")
        CheckPermissionsResult.NO_PERMISSIONS_NOT_LAUNCHED_REQUEST
      }
    } else {
      preferences.userDeclinedEnableBackgroundLocationPermissions = true
      CheckPermissionsResult.HAS_PERMISSIONS
    }
  }

  override fun onResume() {
    val mapFragment =
        supportFragmentManager.fragmentFactory.instantiate(
            this.classLoader, MapFragment::class.java.name)
    supportFragmentManager.commit(true) { replace(R.id.mapFragment, mapFragment, "map") }
    sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    sensorManager?.let {
      orientationSensor = it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      orientationSensor?.run { Timber.d("Got a rotation vector sensor") }
    }
    super.onResume()
    updateMonitoringModeMenu()
    viewModel.updateMyLocationStatus()

    if (checkAndRequestNotificationPermissions() ==
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST) {
      Timber.d("Launched notification permission request")
      return
    }
    if (checkAndRequestLocationPermissions(false) ==
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST) {
      Timber.d("Launched location permission request")
      return
    }
    if (checkAndRequestBackgroundLocationPermissions() ==
        CheckPermissionsResult.NO_PERMISSIONS_LAUNCHED_REQUEST) {
      Timber.d("Launched background location permission request")
      return
    }
    if (checkAndRequestLocationServicesEnabled(false)) {
      viewModel.requestLocationUpdatesForBlueDot()
    }
  }

  private fun handleIntentExtras(intent: Intent) {
    Timber.v("handleIntentExtras")
    val b = if (intent.hasExtra("_args")) intent.getBundleExtra("_args") else Bundle()
    if (b != null) {
      Timber.v("intent has extras from drawerProvider")
      val contactId = b.getString(BUNDLE_KEY_CONTACT_ID)
      if (contactId != null) {
        viewModel.setLiveContact(contactId)
      }
    }
  }

  public override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    service?.clearEventStackNotification()
    handleIntentExtras(intent)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.activity_map, menu)
    this.menu = menu
    updateMonitoringModeMenu()
    viewModel.updateMyLocationStatus()
    return true
  }

  private fun updateMonitoringModeMenu() {
    menu?.findItem(R.id.menu_monitoring)?.run {
      when (preferences.monitoring) {
        MonitoringMode.QUIET -> {
          setIcon(R.drawable.ic_baseline_stop_36)
          setTitle(R.string.monitoring_quiet)
        }
        MonitoringMode.MANUAL -> {
          setIcon(R.drawable.ic_baseline_pause_36)
          setTitle(R.string.monitoring_manual)
        }
        MonitoringMode.SIGNIFICANT -> {
          setIcon(R.drawable.ic_baseline_play_arrow_36)
          setTitle(R.string.monitoring_significant)
        }
        MonitoringMode.MOVE -> {
          setIcon(R.drawable.ic_step_forward_2)
          setTitle(R.string.monitoring_move)
        }
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.menu_report -> {
        viewModel.sendLocation()
        true
      }
      android.R.id.home -> {
        finish()
        true
      }
      R.id.menu_monitoring -> {
        MonitoringModeBottomSheetDialog().show(supportFragmentManager, "modeBottomSheetDialog")
        true
      }
      else -> false
    }
  }

  private fun disableLocationMenus() {
    binding.fabMyLocation.isEnabled = false
    menu?.run { findItem(R.id.menu_report).setEnabled(false).icon?.alpha = 128 }
  }

  private fun enableLocationMenus() {
    binding.fabMyLocation.isEnabled = true
    menu?.run { findItem(R.id.menu_report).setEnabled(true).icon?.alpha = 255 }
  }

  override fun onLongClick(view: View): Boolean {
    viewModel.onBottomSheetLongClick()
    return true
  }

  private fun setBottomSheetExpanded() {
    bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
    binding.mapFragment.setPaddingRelative(0, 0, 0, binding.bottomSheetLayout.height)
    orientationSensor?.let {
      sensorManager?.registerListener(viewModel.orientationSensorEventListener, it, SENSOR_DELAY_UI)
    }
  }

  // BOTTOM SHEET CALLBACKS
  override fun onClick(view: View) {
    setBottomSheetExpanded()
  }

  private fun setBottomSheetCollapsed() {
    bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
    binding.mapFragment.setPadding(0)
    sensorManager?.unregisterListener(viewModel.orientationSensorEventListener)
  }

  private fun setBottomSheetHidden() {
    bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
    binding.mapFragment.setPadding(0)
    menu?.run { close() }
    sensorManager?.unregisterListener(viewModel.orientationSensorEventListener)
  }

  override fun onStart() {
    super.onStart()

    // Register network callback to receive connectivity changes
    if (networkCallback == null) {
      networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
          runOnUiThread { updateConnectionStatus(true) }
        }
        override fun onLost(network: android.net.Network) {
          runOnUiThread { updateConnectionStatus(false) }
        }
        override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
          val hasInternet = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
          runOnUiThread { updateConnectionStatus(hasInternet) }
        }
      }
      connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
    }

    // Listen for preference changes to update interval live
    preferences.registerOnPreferenceChangedListener(this)
    // Refresh overlays that depend on Preferences in case they changed while stopped
    updateTenant()

    bindService(
        Intent(this, BackgroundService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
  }

  override fun onStop() {
    super.onStop()

    // Unregister callback to avoid leaks
    networkCallback?.let {
      try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
    }
    networkCallback = null

    // Stop listening for preference changes
    preferences.unregisterOnPreferenceChangedListener(this)
    
    unbindService(serviceConnection)
  }

  companion object {
    const val BUNDLE_KEY_CONTACT_ID = "BUNDLE_KEY_CONTACT_ID"
    const val IMPLICIT_LOCATION_PERMISSION_REQUEST = 1
    const val EXPLICIT_LOCATION_PERMISSION_REQUEST = 2

    @JvmStatic
    @BindingAdapter("locationIcon")
    fun FloatingActionButton.setIcon(status: MyLocationStatus) {
      val tint =
          when (status) {
            MyLocationStatus.FOLLOWING ->
                resources.getColor(R.color.fabMyLocationForegroundActiveTint, null)
            else -> resources.getColor(R.color.fabMyLocationForegroundInActiveTint, null)
          }
      when (status) {
        MyLocationStatus.DISABLED -> setImageResource(R.drawable.ic_baseline_location_disabled_24)
        MyLocationStatus.AVAILABLE -> setImageResource(R.drawable.ic_baseline_location_searching_24)
        MyLocationStatus.FOLLOWING -> setImageResource(R.drawable.ic_baseline_my_location_24)
      }
      ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
    }
  }
}
