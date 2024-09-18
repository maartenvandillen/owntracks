package it.vandillen.tracker.location

import it.vandillen.tracker.preferences.types.FromConfiguration

enum class LocatorPriority {
  HighAccuracy,
  BalancedPowerAccuracy,
  LowPower,
  NoPower;

  companion object {
    @JvmStatic
    @FromConfiguration
    fun getByValue(value: String?): LocatorPriority? =
        LocatorPriority.entries.firstOrNull { it.name == value }
  }
}
