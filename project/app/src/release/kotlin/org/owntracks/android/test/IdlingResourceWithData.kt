package it.vandillen.tracker.test

import androidx.test.espresso.IdlingResource
import it.vandillen.tracker.model.messages.MessageBase

/**
 * Idling resource that tracks data. Noop implementation
 *
 * @param T
 * @property resourceName
 * @constructor Create empty Idling resource with data
 */
class IdlingResourceWithData<T : MessageBase>(
    private val resourceName: String,
    @Suppress("unused_parameter") comparator: Comparator<in T>
) : IdlingResource {
  private var callback: IdlingResource.ResourceCallback? = null
  private val sent = mutableListOf<T>()
  private val received = mutableListOf<T>()

  override fun getName(): String = this.resourceName

  override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
    this.callback = callback
  }

  override fun isIdleNow(): Boolean = sent.isEmpty() && received.isEmpty()

  fun add(@Suppress("unused_parameter") thing: T) {
    // No-op
  }

  fun remove(@Suppress("unused_parameter") thing: T) {
    // No-op
  }
}
