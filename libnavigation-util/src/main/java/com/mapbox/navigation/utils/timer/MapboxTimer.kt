package com.mapbox.navigation.utils.timer

import com.mapbox.navigation.utils.thread.ThreadController
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Schedules a delay of [routeRefreshInterval] milliseconds and then restarts.
 *
 * @param restartAfter Time delay until the timer should restart.
 * @param executeLambda lambda function that is to be executed after [routeRefreshInterval] milliseconds.
 */
class MapboxTimer {
    private val jobControl = ThreadController.getMainScopeAndRootJob()

    var routeRefreshInterval = TimeUnit.MINUTES.toMillis(1)

    fun startRouteRefresh(executeLambda: () -> Unit): Job {
        return jobControl.scope.launch {
            while (isActive) {
                delay(routeRefreshInterval)
                executeLambda()
            }
        }
    }

    fun stopJobs() {
        jobControl.job.cancelChildren()
    }
}
