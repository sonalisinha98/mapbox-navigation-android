package com.mapbox.navigation.core.routerefresh

import android.util.Log
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.LegAnnotation
import com.mapbox.api.directionsrefresh.v1.MapboxDirectionsRefresh
import com.mapbox.api.directionsrefresh.v1.models.DirectionsRefreshResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class RouteRefreshRequest(
    val accessToken: String,
    val originalRoute: DirectionsRoute,
    val requestUuid: String,
    val legIndex: Int
)

class RouteRefreshApi {

    suspend fun refresh(
        routeRefreshRequest: RouteRefreshRequest
    ): DirectionsRoute? = suspendCoroutine { continuation ->

        val refreshCallBuilder = MapboxDirectionsRefresh.builder()
            .accessToken(routeRefreshRequest.accessToken)
            .requestId(routeRefreshRequest.requestUuid)
            .legIndex(routeRefreshRequest.legIndex)
        routeRefreshRequest.originalRoute.routeIndex()?.toInt()?.let { routeIndex ->
            refreshCallBuilder.routeIndex(routeIndex)
        }
        val refreshCall = refreshCallBuilder.build()

        refreshCall.enqueueCall(object : Callback<DirectionsRefreshResponse> {
            override fun onResponse(call: Call<DirectionsRefreshResponse>, response: Response<DirectionsRefreshResponse>) {
                val routeAnnotations = response.body()?.route()
                if (routeAnnotations != null) {
                    val refreshedDirectionsRoute = mapToDirectionsRoute(routeRefreshRequest, routeAnnotations)
                    continuation.resume(refreshedDirectionsRoute)
                } else {
                    continuation.resumeWithException(RuntimeException("Failed to read refresh response: ${response.raw()}"))
                }
            }

            override fun onFailure(call: Call<DirectionsRefreshResponse>, t: Throwable) {
                continuation.resumeWithException(t)
            }
        })
    }

    private fun mapToDirectionsRoute(routeRefreshRequest: RouteRefreshRequest, routeAnnotations: DirectionsRoute): DirectionsRoute {
        val originalRoute = routeRefreshRequest.originalRoute
        val currentLegIndex = routeRefreshRequest.legIndex
        val refreshedRouteLegs = originalRoute.legs()?.let { oldRouteLegsList ->
            val legs = oldRouteLegsList.toMutableList()
            for (i in currentLegIndex until legs.size) {
                routeAnnotations.legs()?.let { annotationHolderRouteLegsList ->
                    val updatedAnnotation = annotationHolderRouteLegsList[i - currentLegIndex].annotation()
                    val transformedAnnotation = transformedAnnotation(updatedAnnotation)
                    legs[i] = legs[i].toBuilder().annotation(transformedAnnotation).build()
                }
            }
            legs.toList()
        }
        return originalRoute.toBuilder().legs(refreshedRouteLegs).build()
    }

    var counter = 0

    private fun transformedAnnotation(updatedAnnotation: LegAnnotation?): LegAnnotation? {
        val transformed: (String) -> String = if (counter++ % 2 == 0) {
             { value ->
                when (value) {
                    "low" -> "moderate"
                    "moderate" -> "heavy"
                    "heavy" -> "heavy"
                    else -> "heavy"
                }
            }
        } else {
            { value ->
                when (value) {
                    "moderate" -> "low"
                    "heavy" -> "moderate"
                    else -> "low"
                }
            }
        }
        val transformedCongestion = updatedAnnotation?.congestion()?.map(transformed)
        return updatedAnnotation?.toBuilder()?.congestion(transformedCongestion)?.build()
    }
}
