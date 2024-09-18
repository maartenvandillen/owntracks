package it.vandillen.tracker.di

import androidx.databinding.DataBindingComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import it.vandillen.tracker.support.ContactImageBindingAdapter

@EntryPoint
@BindingScoped
@InstallIn(CustomBindingComponent::class)
interface CustomBindingEntryPoint : DataBindingComponent {
  override fun getContactImageBindingAdapter(): ContactImageBindingAdapter
}
