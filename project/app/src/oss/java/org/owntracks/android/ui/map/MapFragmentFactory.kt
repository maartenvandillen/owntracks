package it.vandillen.tracker.ui.map

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import javax.inject.Inject
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.support.ContactImageBindingAdapter
import it.vandillen.tracker.ui.map.osm.OSMMapFragment

/**
 * An implementation of an [FragmentFactory] that always returns an [OSMMapFragment]
 *
 * @property contactImageBindingAdapter A binding adapter that can render contact images in views
 */
class MapFragmentFactory
@Inject
constructor(
    private val preferences: Preferences,
    private val contactImageBindingAdapter: ContactImageBindingAdapter
) : FragmentFactory() {
  override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
    return if (MapFragment::class.java.isAssignableFrom(classLoader.loadClass(className))) {
      OSMMapFragment(preferences, contactImageBindingAdapter)
    } else {
      super.instantiate(classLoader, className)
    }
  }
}
