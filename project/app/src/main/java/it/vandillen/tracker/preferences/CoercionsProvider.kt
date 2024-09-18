package it.vandillen.tracker.preferences

import kotlin.reflect.KProperty

interface CoercionsProvider {
  fun <T> getCoercion(property: KProperty<*>, value: T, preferences: Preferences): T
}
