package it.vandillen.tracker.di

import dagger.hilt.DefineComponent

@DefineComponent.Builder
interface CustomBindingComponentBuilder {
  fun build(): CustomBindingComponent
}
