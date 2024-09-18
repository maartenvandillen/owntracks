package it.vandillen.tracker.ui.map

import androidx.databinding.ViewDataBinding
import it.vandillen.tracker.R
import it.vandillen.tracker.preferences.types.FromConfiguration
import it.vandillen.tracker.ui.map.osm.OSMMapFragment

enum class MapLayerStyle {
  GoogleMapDefault,
  GoogleMapHybrid,
  GoogleMapSatellite,
  GoogleMapTerrain,
  OpenStreetMapNormal,
  OpenStreetMapWikimedia;

  fun isSameProviderAs(mapLayerStyle: MapLayerStyle): Boolean {
    return setOf("GoogleMap", "OpenStreetMap").any {
      name.startsWith(it) && mapLayerStyle.name.startsWith(it)
    }
  }

  fun getFragmentClass(): Class<out MapFragment<out ViewDataBinding>> {
    return when (this) {
      GoogleMapDefault,
      GoogleMapHybrid,
      GoogleMapSatellite,
      GoogleMapTerrain -> GoogleMapFragment::class.java
      OpenStreetMapNormal,
      OpenStreetMapWikimedia -> OSMMapFragment::class.java
    }
  }

  companion object {
    @JvmStatic
    @FromConfiguration
    fun getByValue(value: String): MapLayerStyle =
        entries.firstOrNull { it.name == value } ?: GoogleMapDefault
  }
}

val mapLayerSelectorButtonsToStyles =
    mapOf(
        R.id.fabMapLayerGoogleNormal to MapLayerStyle.GoogleMapDefault,
        R.id.fabMapLayerGoogleHybrid to MapLayerStyle.GoogleMapHybrid,
        R.id.fabMapLayerGoogleTerrain to MapLayerStyle.GoogleMapTerrain,
        R.id.fabMapLayerOpenStreetMap to MapLayerStyle.OpenStreetMapNormal,
        R.id.fabMapLayerOpenStreetMapWikimedia to MapLayerStyle.OpenStreetMapWikimedia)
