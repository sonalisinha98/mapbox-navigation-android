package com.mapbox.navigation.core.directions.session

import android.location.Location
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.ifNonNull
import com.mapbox.navigation.base.route.Router
import com.mapbox.navigation.base.trip.model.RouteProgress
import java.util.concurrent.CopyOnWriteArrayList

// todo make internal
class MapboxDirectionsSession(
    private val router: Router
) : DirectionsSession {

    private val routesObservers = CopyOnWriteArrayList<RoutesObserver>()
    private var routeOptions: RouteOptions? = null

    override var routes: List<DirectionsRoute> = emptyList()
        set(value) {
            router.cancel()
            if (routes.isEmpty() && value.isEmpty()) {
                return
            }
            field = value
            if (routes.isNotEmpty()) {
                this.routeOptions = routes[0].routeOptions()
            }
            routesObservers.forEach { it.onRoutesChanged(value) }
        }

    override fun getRouteOptions(): RouteOptions? = routeOptions

    override fun cancel() {
        router.cancel()
    }

    override fun requestRoutes(
        routeOptions: RouteOptions,
        routesRequestCallback: RoutesRequestCallback?
    ) {
        routes = emptyList()
        router.getRoute(routeOptions, object : Router.Callback {
            override fun onResponse(routes: List<DirectionsRoute>) {
                this@MapboxDirectionsSession.routes = routes
                routesRequestCallback?.onRoutesReady(routes)
                // todo log in the future
            }

            override fun onFailure(throwable: Throwable) {
                routesRequestCallback?.onRoutesRequestFailure(throwable, routeOptions)
                // todo log in the future
            }

            override fun onCanceled() {
                routesRequestCallback?.onRoutesRequestCanceled(routeOptions)
                // todo log in the future
            }
        })
    }

    override fun buildAdjustedRouteOptions(
        routeOptions: RouteOptions,
        routeProgress: RouteProgress,
        location: Location
    ): RouteOptions {
        val optionsBuilder = routeOptions.toBuilder()
        val coordinates = routeOptions.coordinates()
        routeProgress.currentLegProgress()?.legIndex()?.let { index ->
            optionsBuilder.coordinates(
                coordinates.drop(index + 1).toMutableList().apply {
                    add(0, Point.fromLngLat(location.longitude, location.latitude))
                }
            )

            val bearings = mutableListOf<List<Double>?>()

            val originTolerance = routeOptions.bearingsList()?.getOrNull(0)?.getOrNull(1)
                ?: DEFAULT_REROUTE_BEARING_TOLERANCE
            val currentAngle = location.bearing.toDouble()

            bearings.add(listOf(currentAngle, originTolerance))
            val originalBearings = routeOptions.bearingsList()
            if (originalBearings != null) {
                bearings.addAll(originalBearings.subList(index + 1, coordinates.size))
            } else {
                while (bearings.size < coordinates.size) {
                    bearings.add(null)
                }
            }

            optionsBuilder.bearingsList(bearings)

            // todo implement options.radiuses
            // todo implement options.approaches
            // todo implement options.waypointIndices
            // todo implement options.waypointNames
            // todo implement options.waypointTargets
        }

        return optionsBuilder.build()
    }

    override fun requestFasterRoute(
        adjustedRouteOptions: RouteOptions,
        routesRequestCallback: RoutesRequestCallback
    ) {
        if (routes.isEmpty()) {
            routesRequestCallback.onRoutesRequestCanceled(adjustedRouteOptions)
            return
        }
        router.getRoute(adjustedRouteOptions, object : Router.Callback {
            override fun onResponse(routes: List<DirectionsRoute>) {
                routesRequestCallback.onRoutesReady(routes)
            }

            override fun onFailure(throwable: Throwable) {
                ifNonNull(routeOptions) { options ->
                    routesRequestCallback.onRoutesRequestFailure(throwable, options)
                }
            }

            override fun onCanceled() {
                ifNonNull(routeOptions) { options ->
                    routesRequestCallback.onRoutesRequestCanceled(options)
                }
            }
        })
    }

    override fun registerRoutesObserver(routesObserver: RoutesObserver) {
        routesObservers.add(routesObserver)
        if (routes.isNotEmpty()) {
            routesObserver.onRoutesChanged(routes)
        }
    }

    override fun unregisterRoutesObserver(routesObserver: RoutesObserver) {
        routesObservers.remove(routesObserver)
    }

    override fun unregisterAllRoutesObservers() {
        routesObservers.clear()
    }

    override fun shutDownSession() {
        cancel()
    }

    companion object {
        private const val DEFAULT_REROUTE_BEARING_TOLERANCE = 90.0
    }
}
