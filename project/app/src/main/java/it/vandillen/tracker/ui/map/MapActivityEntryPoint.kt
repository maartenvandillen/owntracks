package it.vandillen.tracker.ui.map

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
interface MapActivityEntryPoint {
  val fragmentFactory: MapFragmentFactory
}
