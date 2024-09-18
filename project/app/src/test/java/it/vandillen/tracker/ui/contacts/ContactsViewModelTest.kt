package it.vandillen.tracker.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import it.vandillen.tracker.data.repos.ContactsRepo
import it.vandillen.tracker.geocoding.GeocoderProvider
import it.vandillen.tracker.model.Contact

class ContactsViewModelTest {
  private val testContacts =
      mutableMapOf(
          Pair("testContact1", Contact("testContact1")),
          Pair("testContact2", Contact("testContact2")),
          Pair("testContact3", Contact("testContact3")),
          Pair("testContact4", Contact("testContact4")),
          Pair("testContact5", Contact("testContact5")))

  private val mockContactsRepo: ContactsRepo = mock { on { all } doReturn testContacts }
  private val mockGeocoderProvider: GeocoderProvider = mock {}

  @Test
  fun `Contacts ViewModel outputs full list of contacts to view`() {
    val contactsViewModel = ContactsViewModel(mockContactsRepo, mockGeocoderProvider)
    assertEquals(5, contactsViewModel.contacts.keys.size)
  }
}
