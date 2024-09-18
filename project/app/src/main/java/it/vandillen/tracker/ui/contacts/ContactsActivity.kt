package it.vandillen.tracker.ui.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.launch
import it.vandillen.tracker.R
import it.vandillen.tracker.data.repos.ContactsRepoChange
import it.vandillen.tracker.databinding.UiContactsBinding
import it.vandillen.tracker.model.Contact
import it.vandillen.tracker.support.DrawerProvider
import it.vandillen.tracker.test.CountingIdlingResourceShim
import it.vandillen.tracker.ui.map.MapActivity
import it.vandillen.tracker.ui.mixins.ServiceStarter
import timber.log.Timber

@AndroidEntryPoint
class ContactsActivity :
    AppCompatActivity(), AdapterClickListener<Contact>, ServiceStarter by ServiceStarter.Impl() {
  @Inject lateinit var drawerProvider: it.vandillen.tracker.support.DrawerProvider

  @Inject
  @Named("contactsActivityIdlingResource")
  @VisibleForTesting
  lateinit var contactsCountingIdlingResource: CountingIdlingResourceShim

  private val viewModel: ContactsViewModel by viewModels()
  private lateinit var contactsAdapter: ContactsAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    startService(this)
    super.onCreate(savedInstanceState)
    contactsAdapter = ContactsAdapter(this, viewModel.coroutineScope)
    val binding =
        DataBindingUtil.setContentView<UiContactsBinding>(this, R.layout.ui_contacts).apply {
          vm = viewModel
          appbar.toolbar.run {
            setSupportActionBar(this)
            drawerProvider.attach(this)
          }
          contactsRecyclerView.run {
            layoutManager = LinearLayoutManager(this@ContactsActivity)
            adapter = contactsAdapter
          }
        }

    contactsAdapter.setContactList(viewModel.contacts.values)

    // Trigger a geocode refresh on startup, because future refreshes will only be triggered on
    // update events
    viewModel.contacts.values.forEach(viewModel::refreshGeocode)

    // Observe changes to the contacts repo in our lifecycle and forward it onto the
    // [ContactsAdapter], optionally
    // updating the geocode for the contact.
    lifecycleScope.launch {
      viewModel.contactUpdatedEvent.collect {
        Timber.v("Received contactUpdatedEvent $it")
        when (it) {
          is ContactsRepoChange.ContactAdded -> {
            contactsAdapter.addContact(it.contact)
            viewModel.refreshGeocode(it.contact)
          }
          is ContactsRepoChange.ContactRemoved -> contactsAdapter.removeContact(it.contact)
          is ContactsRepoChange.ContactLocationUpdated -> {
            contactsAdapter.updateContact(it.contact)
            viewModel.refreshGeocode(it.contact)
          }
          is ContactsRepoChange.ContactCardUpdated -> contactsAdapter.updateContact(it.contact)
          is ContactsRepoChange.AllCleared -> contactsAdapter.clearAll()
        }
        binding.run {
          placeholder.visibility = if (viewModel.contacts.isEmpty()) View.VISIBLE else View.GONE
          contactsRecyclerView.visibility =
              if (viewModel.contacts.isEmpty()) View.GONE else View.VISIBLE
        }

        contactsCountingIdlingResource.run { if (!isIdleNow) decrement() }
      }
    }
  }

  override fun onClick(item: Contact, view: View, longClick: Boolean) {
    startActivity(
        Intent(this, MapActivity::class.java)
            .putExtra(
                "_args", Bundle().apply { putString(MapActivity.BUNDLE_KEY_CONTACT_ID, item.id) }))
  }
}
