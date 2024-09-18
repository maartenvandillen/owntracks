package it.vandillen.tracker.ui.waypoints

import androidx.databinding.ViewDataBinding
import it.vandillen.tracker.BR
import it.vandillen.tracker.R
import it.vandillen.tracker.data.waypoints.WaypointModel
import it.vandillen.tracker.ui.base.BaseRecyclerViewAdapterWithClickHandler
import it.vandillen.tracker.ui.base.BaseRecyclerViewHolder

class WaypointsAdapter(clickListener: ClickListener<WaypointModel>) :
    BaseRecyclerViewAdapterWithClickHandler<
        WaypointModel, WaypointsAdapter.WaypointModelViewHolder>(
        clickListener, ::WaypointModelViewHolder, R.layout.ui_row_waypoint) {

  class WaypointModelViewHolder(binding: ViewDataBinding) :
      BaseRecyclerViewHolder<WaypointModel>(binding, BR.waypoint)
}
