package it.vandillen.tracker.di

import javax.inject.Qualifier

class CoroutineScopes {

  @Retention(AnnotationRetention.RUNTIME) @Qualifier annotation class DefaultDispatcher

  @Retention(AnnotationRetention.RUNTIME) @Qualifier annotation class IoDispatcher

  @Retention(AnnotationRetention.RUNTIME) @Qualifier annotation class MainDispatcher
}
