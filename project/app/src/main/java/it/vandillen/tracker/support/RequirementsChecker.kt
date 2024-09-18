package it.vandillen.tracker.support

interface RequirementsChecker {
  fun hasLocationPermissions(): Boolean

  fun hasBackgroundLocationPermission(): Boolean

  fun isLocationServiceEnabled(): Boolean

  fun isPlayServicesCheckPassed(): Boolean

  fun hasNotificationPermissions(): Boolean
}
