package org.krripe.shadowkits.database

import org.krripe.shadowkits.config.KitsConfig

object KitsDatabaseManager {
    private var adapter: KitsDatabaseAdapter? = null
    private var databaseAvailable = false
    private var lastType: String? = null
    private var lastEnabled = false

    fun initialize(): Boolean {
        val dbCfg = KitsConfig.config.database
        lastType = dbCfg.type.lowercase()
        lastEnabled = dbCfg.enabled

        if (!lastEnabled) {
            println("[ShadowKits] Database disabled in config – using JSON storage")
            return false
        }

        adapter = when (lastType) {
            "mysql"  -> MySQLKitsAdapter(dbCfg)
            "sqlite" -> SQLiteKitsAdapter()
            else -> {
                println("[ShadowKits] ❌ Unsupported DB type: ${dbCfg.type}")
                null
            }
        }

        databaseAvailable = adapter?.connect() == true
        if (databaseAvailable) {
            println("[ShadowKits] ✅ Connected to ${dbCfg.type} database")
        } else {
            println("[ShadowKits] ❌ DB connection failed – falling back to JSON")
            adapter = null
        }
        return databaseAvailable
    }

    fun getAdapter(): KitsDatabaseAdapter? = if (databaseAvailable) adapter else null
    fun isUsingDatabase(): Boolean = databaseAvailable

    fun shutdown() {
        adapter?.disconnect()
        adapter = null
        databaseAvailable = false
        println("[ShadowKits] Database connection closed.")
    }
}
