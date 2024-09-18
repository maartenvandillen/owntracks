package it.vandillen.tracker.testutils.idlingresources

import androidx.test.espresso.IdlingResource

interface DispatcherWithIdlingResource {
  val idlingResource: IdlingResource
}
