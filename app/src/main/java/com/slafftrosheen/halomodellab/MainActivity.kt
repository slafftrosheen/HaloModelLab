package com.slafftrosheen.halomodellab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.slafftrosheen.halomodellab.ui.LabApp
import com.slafftrosheen.halomodellab.ui.theme.LabTheme

/**
 * The only Activity. It hosts navigation and observes state; it never runs queue work.
 *
 * Rotation and tab changes recreate this Activity but must not create another session,
 * because execution belongs to the service/coordinator from HML-L2 onward.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LabApplication).container
        setContent {
            LabTheme {
                LabApp(container = container)
            }
        }
    }
}
