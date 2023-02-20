package org.owntracks.android.support

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
open class OSSRequirementsChecker @Inject constructor(
    open val context: Context
) : RequirementsChecker {

    override fun hasLocationPermissions(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun isLocationServiceEnabled(): Boolean =
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?)?.run {
            LocationManagerCompat.isLocationEnabled(this)
        } ?: false

    override fun isPlayServicesCheckPassed(): Boolean = true
    override fun isNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context)
        .areNotificationsEnabled()
}
