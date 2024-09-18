package it.vandillen.tracker.ui.welcome.fragments

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import it.vandillen.tracker.ui.welcome.WelcomeViewModel

abstract class WelcomeFragment : Fragment() {
  val viewModel: WelcomeViewModel by activityViewModels()

  abstract fun shouldBeDisplayed(context: Context): Boolean
}
