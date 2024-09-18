package it.vandillen.tracker.model

import java.time.Instant
import it.vandillen.tracker.model.messages.Clock

class FakeFixedClock(fakeTime: Instant = Instant.ofEpochMilli(25123)) : Clock {
  override val time: Instant = fakeTime
}
