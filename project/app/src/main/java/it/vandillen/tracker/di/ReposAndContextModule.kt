package it.vandillen.tracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.vandillen.tracker.data.repos.ContactsRepo
import it.vandillen.tracker.data.repos.MemoryContactsRepo
import it.vandillen.tracker.data.waypoints.RoomWaypointsRepo
import it.vandillen.tracker.data.waypoints.WaypointsRepo
import it.vandillen.tracker.preferences.PreferencesStore
import it.vandillen.tracker.preferences.SharedPreferencesStore

@InstallIn(SingletonComponent::class)
@Module
abstract class ReposAndContextModule {
  @Binds
  abstract fun bindSharedPreferencesStoreModule(
      sharedPreferencesStore: SharedPreferencesStore
  ): PreferencesStore

  @Binds abstract fun bindWaypointsRepo(waypointsRepo: RoomWaypointsRepo): WaypointsRepo

  @Binds abstract fun bindMemoryContactsRepo(memoryContactsRepo: MemoryContactsRepo): ContactsRepo
}
