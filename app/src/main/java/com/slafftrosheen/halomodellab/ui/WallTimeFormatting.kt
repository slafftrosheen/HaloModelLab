package com.slafftrosheen.halomodellab.ui

import java.text.DateFormat
import java.util.Date

/**
 * Wall-clock formatting for report dates only. Durations and timeouts never use wall time.
 */
fun formatWallTime(wallTimeMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(wallTimeMs))
