package it.vandillen.tracker.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import it.vandillen.tracker.data.repos.ContactsRepo
import it.vandillen.tracker.data.repos.ContactsRepoChange
import it.vandillen.tracker.geocoding.GeocoderProvider
import it.vandillen.tracker.model.Contact

@HiltViewModel
class ContactsViewModel
@Inject
constructor(
    private val contactsRepo: ContactsRepo,
    private val geocoderProvider: GeocoderProvider
) : ViewModel() {
  fun refreshGeocode(contact: Contact) {
    contact.geocodeLocation(geocoderProvider, viewModelScope)
  }

  val contacts = contactsRepo.all
  val contactUpdatedEvent: Flow<ContactsRepoChange>
    get() = contactsRepo.repoChangedEvent

  val coroutineScope: CoroutineScope
    get() = viewModelScope
}
