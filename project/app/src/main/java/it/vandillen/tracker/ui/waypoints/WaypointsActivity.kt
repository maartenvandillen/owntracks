package it.vandillen.tracker.ui.waypoints

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.launch
import it.vandillen.tracker.R
import it.vandillen.tracker.data.waypoints.WaypointModel
import it.vandillen.tracker.databinding.UiWaypointsBinding
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.support.DrawerProvider
import it.vandillen.tracker.test.CountingIdlingResourceShim
import it.vandillen.tracker.test.SimpleIdlingResource
import it.vandillen.tracker.ui.NotificationsStash
import it.vandillen.tracker.ui.base.BaseRecyclerViewAdapterWithClickHandler
import it.vandillen.tracker.ui.base.ClickHasBeenHandled
import it.vandillen.tracker.ui.base.RecyclerViewLayoutCompleteListener
import it.vandillen.tracker.ui.mixins.NotificationsPermissionRequested
import it.vandillen.tracker.ui.preferences.load.LoadActivity
import it.vandillen.tracker.ui.waypoint.WaypointActivity
import timber.log.Timber

@AndroidEntryPoint
class WaypointsActivity :
    AppCompatActivity(),
    BaseRecyclerViewAdapterWithClickHandler.ClickListener<WaypointModel>,
    RecyclerViewLayoutCompleteListener.RecyclerViewIdlingCallback,
    NotificationsPermissionRequested by NotificationsPermissionRequested.Impl() {
  private var recyclerViewStartLayoutInstant: ComparableTimeMark? = null
  private var layoutCompleteListener: RecyclerViewLayoutCompleteListener? = null

  @Inject lateinit var notificationsStash: NotificationsStash

  @Inject lateinit var drawerProvider: it.vandillen.tracker.support.DrawerProvider

  @Inject lateinit var preferences: Preferences

  @Inject
  @Named("outgoingQueueIdlingResource")
  @get:VisibleForTesting
  lateinit var outgoingQueueIdlingResource: CountingIdlingResourceShim

  @Inject
  @Named("publishResponseMessageIdlingResource")
  @get:VisibleForTesting
  lateinit var publishResponseMessageIdlingResource: SimpleIdlingResource

  private val viewModel: WaypointsViewModel by viewModels()
  private lateinit var recyclerViewAdapter: WaypointsAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    recyclerViewAdapter = WaypointsAdapter(this)
    postNotificationsPermissionInit(this, preferences, notificationsStash)
    DataBindingUtil.setContentView<UiWaypointsBinding>(this, R.layout.ui_waypoints).apply {
      vm = viewModel
      lifecycleOwner = this@WaypointsActivity
      setSupportActionBar(appbar.toolbar)
      drawerProvider.attach(appbar.toolbar)
      waypointsRecyclerView.apply {
        layoutManager = LinearLayoutManager(this@WaypointsActivity)
        adapter = recyclerViewAdapter
        emptyView = placeholder
        viewTreeObserver.addOnGlobalLayoutListener {
          Timber.v("global layout changed")
          if (recyclerViewStartLayoutInstant != null) {
            this@WaypointsActivity.recyclerViewStartLayoutInstant?.run {
              Timber.d("Completed waypoints layout in ${this.elapsedNow()}")
            }
            this@WaypointsActivity.recyclerViewStartLayoutInstant = null
          }
          layoutCompleteListener?.run { onLayoutCompleted() }
        }
      }
    }
    lifecycleScope.launch {
      viewModel.waypointsList.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
        recyclerViewStartLayoutInstant = TimeSource.Monotonic.markNow()
        recyclerViewAdapter.setData(it)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    requestNotificationsPermission()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.activity_waypoints, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.add -> {
        startActivity(Intent(this, WaypointActivity::class.java))
        true
      }
      R.id.exportWaypointsService -> {
        viewModel.exportWaypoints()
        true
      }
      R.id.importWaypoints -> {
        startActivity(Intent(this, LoadActivity::class.java))
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onClick(thing: WaypointModel, view: View, longClick: Boolean): ClickHasBeenHandled {
    startActivity(Intent(this, WaypointActivity::class.java).putExtra("waypointId", thing.id))
    return true
  }

  override fun setRecyclerViewLayoutCompleteListener(listener: RecyclerViewLayoutCompleteListener) {
    this.layoutCompleteListener = listener
  }

  override fun removeRecyclerViewLayoutCompleteListener(
      listener: RecyclerViewLayoutCompleteListener
  ) {
    if (this.layoutCompleteListener == listener) {
      this.layoutCompleteListener = null
    }
  }

  override var isRecyclerViewLayoutCompleted: Boolean
    get() =
        (recyclerViewStartLayoutInstant == null).also {
          Timber.v("Being asked if I'm idle, saying $it")
        }
    set(value) {
      recyclerViewStartLayoutInstant = if (!value) TimeSource.Monotonic.markNow() else null
    }
}
