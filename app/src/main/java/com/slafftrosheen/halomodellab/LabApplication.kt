package com.slafftrosheen.halomodellab

import android.app.Application

/**
 * Owns the single AppContainer for the process.
 *
 * Nothing starts here except object construction: no service, no runner, no boot receiver.
 * In HML-L1 this is where the one-time interrupted-owner recovery runs, before any start
 * command is accepted.
 */
class LabApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
