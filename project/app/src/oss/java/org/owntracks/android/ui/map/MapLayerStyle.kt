package it.vandillen.tracker.ui.map

import it.vandillen.tracker.R
import it.vandillen.tracker.preferences.types.FromConfiguration

enum class MapLayerStyle {
  OpenStreetMapNormal,
  OpenStreetMapWikimedia;

  fun isSameProviderAs(@Suppress("UNUSED_PARAMETER") mapLayerStyle: MapLayerStyle): Boolean = true

  companion object {
    @JvmStatic
    @FromConfiguration
    fun getByValue(value: String): MapLayerStyle =
        MapLayerStyle.values().firstOrNull { it.name == value } ?: OpenStreetMapNormal
  }
}

val mapLayerSelectorButtonsToStyles =
    mapOf(
        R.id.fabMapLayerOpenStreetMap to MapLayerStyle.OpenStreetMapNormal,
        R.id.fabMapLayerOpenStreetMapWikimedia to MapLayerStyle.OpenStreetMapWikimedia)
