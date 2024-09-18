package it.vandillen.tracker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vandillen.tracker.gms.GMSRequirementsChecker
import it.vandillen.tracker.support.RequirementsChecker

@InstallIn(SingletonComponent::class)
@Module
class RequirementsCheckerModule {
  @Provides
  fun provideRequirementsChecker(@ApplicationContext context: Context): RequirementsChecker =
      GMSRequirementsChecker(context)
}
