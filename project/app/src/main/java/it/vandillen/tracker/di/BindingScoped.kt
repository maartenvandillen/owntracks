package it.vandillen.tracker.di

import javax.inject.Scope
import kotlin.annotation.AnnotationRetention.BINARY

@Scope @Retention(BINARY) annotation class BindingScoped
