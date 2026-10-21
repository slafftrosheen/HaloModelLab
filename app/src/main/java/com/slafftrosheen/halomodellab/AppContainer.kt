package com.slafftrosheen.halomodellab

import com.slafftrosheen.halomodellab.catalog.CatalogRepository

/**
 * Small manual dependency container (constructor injection only, no service locator).
 *
 * HML-L0 holds only the in-memory catalog. Database, session repository and exporter are
 * added by later packages and must be constructed here so screens and the service share
 * exactly one instance.
 */
class AppContainer {

    val catalogRepository: CatalogRepository = CatalogRepository()

    companion object {
        /** Report protocol version for future exports. */
        const val PROTOCOL_VERSION: Int = 1

        /** Export schemaVersion for future archives. */
        const val EXPORT_SCHEMA_VERSION: Int = 1

        /** Honest v0 bridge state; see SPEC §11. */
        const val BRIDGE_STATUS: String = "NOT_IMPLEMENTED"
    }
}
