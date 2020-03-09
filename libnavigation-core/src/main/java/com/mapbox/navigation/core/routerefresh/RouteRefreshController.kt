package com.mapbox.navigation.core.routerefresh

import android.util.Log
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.trip.session.TripSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal class RouteRefreshController(
    private val tripSession: TripSession,
    private val routeRefreshApi: RouteRefreshApi
) {
    var accessToken: String = ""
    var intervalSeconds: Long = TimeUnit.MINUTES.toMillis(1)

    fun refreshRoute(scope: CoroutineScope, function: (RouteRefresh) -> Unit): Job {
        return scope.launch {
            while (true) {
                delay(TimeUnit.SECONDS.toMillis(intervalSeconds))

                val routeRefreshRequest = buildRequest()
                if (routeRefreshRequest != null) {
                    val routeAnnotations = callRefreshRoute(routeRefreshRequest)
                    if (routeAnnotations != null) {
                        val routeRefresh = RouteRefresh(routeRefreshRequest.originalRoute, routeAnnotations)
                        function.invoke(routeRefresh)
                    }
                }
            }
        }
    }

    private fun buildRequest(): RouteRefreshRequest? {
        val requestAccessToken = accessToken.ifEmpty { null } ?: run {
            log("location_debug Invalid access token")
            return null
        }
        val requestDirectionsRoute = tripSession.route ?: run {
            log("location_debug You must set a direction route")
            return null
        }
        val requestUuid = requestDirectionsRoute.routeOptions()?.requestUuid()?.ifEmpty { null } ?: run {
            log("location_debug Route is not active")
            return null
        }

        val legIndex = tripSession.getRouteProgress()?.currentLegProgress()?.legIndex() ?: 0
        return RouteRefreshRequest(requestAccessToken, requestDirectionsRoute, requestUuid, legIndex)
    }

    private suspend fun callRefreshRoute(routeRefreshRequest: RouteRefreshRequest): DirectionsRoute? {
        return try {
            routeRefreshApi.refresh(routeRefreshRequest)
        } catch (throwable: Throwable) {
            logError("callRefreshRoute", throwable)
            null
        }
    }

    companion object {
        fun log(message: String) {
            println("${this::class.java.simpleName}: $message")
            Log.i(this::class.java.simpleName, message)
        }

        fun logError(message: String, throwable: Throwable) {
            throwable.printStackTrace()
            Log.e(this::class.java.simpleName, message, throwable)
        }
    }
}
