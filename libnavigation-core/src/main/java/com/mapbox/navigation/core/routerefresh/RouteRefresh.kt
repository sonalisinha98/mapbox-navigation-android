package com.mapbox.navigation.core.routerefresh

import com.mapbox.api.directions.v5.models.DirectionsRoute

data class RouteRefresh(
    val originalRoute: DirectionsRoute,
    val refreshedRoute: DirectionsRoute
)
