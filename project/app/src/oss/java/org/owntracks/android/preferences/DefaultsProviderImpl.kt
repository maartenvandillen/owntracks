package it.vandillen.tracker.preferences

import kotlin.reflect.KProperty
import it.vandillen.tracker.preferences.types.ReverseGeocodeProvider
import it.vandillen.tracker.ui.map.MapLayerStyle

class DefaultsProviderImpl : DefaultsProvider {
  @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
  override fun <T> getDefaultValue(preferences: Preferences, property: KProperty<*>): T {
    return when (property) {
      Preferences::mapLayerStyle -> MapLayerStyle.OpenStreetMapNormal
      Preferences::reverseGeocodeProvider -> ReverseGeocodeProvider.NONE
      else -> super.getDefaultValue<T>(preferences, property)
    }
        as T
  }
}
