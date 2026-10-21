package com.slafftrosheen.halomodellab

import android.app.Application

/**
 * Owns the single AppContainer for the process.
 *
 * Construction also schedules the one-time interrupted-owner recovery on an IO dispatcher.
 * Nothing else starts here: no service, no runner, no boot receiver.
 */
class LabApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
