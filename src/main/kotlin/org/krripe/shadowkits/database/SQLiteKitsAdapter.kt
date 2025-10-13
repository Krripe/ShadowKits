package org.krripe.shadowkits.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class SQLiteKitsAdapter : KitsDatabaseAdapter {

    private var connection: Connection? = null

    override fun connect(): Boolean {
        return try {
            File("config/shadowkits").mkdirs()

            val url = "jdbc:sqlite:config/shadowkits/shadowkits.db"
            connection = DriverManager.getConnection(url)
            initTables()
            println("[ShadowKits] Connected to SQLite at $url")
            true
        } catch (e: SQLException) {
            println("[ShadowKits] ❌ SQLite connection error: ${e.message}")
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            connection?.close()
            true
        } catch (_: SQLException) {
            false
        }
    }

    private fun initTables() {
        connection!!.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS kit_cooldowns (
                  player_uuid TEXT NOT NULL,
                  kit_name    TEXT NOT NULL,
                  expires     INTEGER NOT NULL,
                  PRIMARY KEY (player_uuid, kit_name)
                );
                """
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS kit_claims (
                  player_uuid TEXT NOT NULL,
                  kit_name    TEXT NOT NULL,
                  claims      INTEGER NOT NULL,
                  PRIMARY KEY (player_uuid, kit_name)
                );
                """
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS first_join (
                    player_uuid TEXT NOT NULL,
                    done        INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid)
                );
                """
            )
        }
    }

    override fun tryConsumeKit(
        playerId: UUID,
        kitName: String,
        cooldownSeconds: Long,
        useClaims: Boolean,
        maxClaims: Int
    ): KitsDatabaseAdapter.ClaimDecision {
        reconnectIfClosed()
        val conn = connection ?: return KitsDatabaseAdapter.ClaimDecision.denied(
            KitsDatabaseAdapter.ClaimDecision.Reason.ERROR,
            "No SQLite connection"
        )

        val now = Instant.now().epochSecond
        val newExpires = now + cooldownSeconds.coerceAtLeast(0)

        try {
            val oldAutoCommit = conn.autoCommit
            conn.autoCommit = false

            var currentExpires = 0L
            conn.prepareStatement(
                "SELECT expires FROM kit_cooldowns WHERE player_uuid = ? AND kit_name = ?"
            ).use { ps ->
                ps.setString(1, playerId.toString())
                ps.setString(2, kitName.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) currentExpires = rs.getLong("expires")
                }
            }
            if (currentExpires > now) {
                conn.rollback()
                conn.autoCommit = oldAutoCommit
                return KitsDatabaseAdapter.ClaimDecision.denied(
                    KitsDatabaseAdapter.ClaimDecision.Reason.COOLDOWN,
                    "Cooldown active"
                )
            }

            if (useClaims) {
                var claims = 0
                conn.prepareStatement(
                    "SELECT claims FROM kit_claims WHERE player_uuid = ? AND kit_name = ?"
                ).use { ps ->
                    ps.setString(1, playerId.toString())
                    ps.setString(2, kitName.lowercase())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) claims = rs.getInt("claims")
                    }
                }
                if (claims >= maxClaims) {
                    conn.rollback()
                    conn.autoCommit = oldAutoCommit
                    return KitsDatabaseAdapter.ClaimDecision.denied(
                        KitsDatabaseAdapter.ClaimDecision.Reason.MAX_CLAIMS,
                        "Max claims reached"
                    )
                }

                conn.prepareStatement(
                    """
                INSERT INTO kit_claims (player_uuid, kit_name, claims)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, kit_name)
                DO UPDATE SET claims = excluded.claims;
                """.trimIndent()
                ).use { ps ->
                    ps.setString(1, playerId.toString())
                    ps.setString(2, kitName.lowercase())
                    ps.setInt(3, claims + 1)
                    ps.executeUpdate()
                }
            }

            conn.prepareStatement(
                """
            INSERT INTO kit_cooldowns (player_uuid, kit_name, expires)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, kit_name)
            DO UPDATE SET expires = excluded.expires;
            """.trimIndent()
            ).use { ps ->
                ps.setString(1, playerId.toString())
                ps.setString(2, kitName.lowercase())
                ps.setLong(3, newExpires)
                ps.executeUpdate()
            }

            conn.commit()
            conn.autoCommit = true
            return KitsDatabaseAdapter.ClaimDecision.allowed(newExpires)
        } catch (e: SQLException) {
            try { connection?.rollback() } catch (_: SQLException) {}
            return KitsDatabaseAdapter.ClaimDecision.denied(
                KitsDatabaseAdapter.ClaimDecision.Reason.ERROR,
                "SQLite error: ${e.message}"
            )
        }
    }

    override fun saveCooldown(
        playerId: UUID,
        kitName: String,
        expiresAt: Long
    ) {
        reconnectIfClosed()
        connection!!.prepareStatement(
            """
            INSERT INTO kit_cooldowns (player_uuid, kit_name, expires)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, kit_name)
            DO UPDATE SET expires = excluded.expires;
            """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.setLong  (3, expiresAt)
            ps.executeUpdate()
        }
    }

    override fun getCooldown(
        playerId: UUID,
        kitName: String
    ): Long {
        reconnectIfClosed()
        connection!!.prepareStatement(
            """
            SELECT expires FROM kit_cooldowns
            WHERE player_uuid = ? AND kit_name = ?;
            """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getLong("expires")
            }
        }
        return 0L
    }

    override fun clearCooldown(playerId: UUID, kitName: String) {
        reconnectIfClosed()
        connection!!.prepareStatement(
            "DELETE FROM kit_cooldowns WHERE player_uuid = ? AND kit_name = ?;"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeUpdate()
        }
    }

    override fun saveClaims(playerId: UUID, kitName: String, claims: Int) {
        reconnectIfClosed()
        connection!!.prepareStatement(
            """
            INSERT INTO kit_claims (player_uuid, kit_name, claims)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, kit_name)
            DO UPDATE SET claims = excluded.claims;
            """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.setInt   (3, claims)
            ps.executeUpdate()
        }
    }

    override fun getClaims(playerId: UUID, kitName: String): Int {
        reconnectIfClosed()
        connection!!.prepareStatement(
            """
            SELECT claims FROM kit_claims
            WHERE player_uuid = ? AND kit_name = ?;
            """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getInt("claims")
            }
        }
        return 0
    }

    override fun clearClaims(playerId: UUID, kitName: String) {
        reconnectIfClosed()
        connection!!.prepareStatement(
            "DELETE FROM kit_claims WHERE player_uuid = ? AND kit_name = ?;"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeUpdate()
        }
    }

    override fun allCooldownRows(): List<Triple<UUID, String, Long>> {
        reconnectIfClosed()
        val list = mutableListOf<Triple<UUID, String, Long>>()
        connection!!.prepareStatement("SELECT * FROM kit_cooldowns").use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val uuid  = UUID.fromString(rs.getString("player_uuid"))
                    val kit   = rs.getString("kit_name")
                    val exp   = rs.getLong("expires")
                    list += Triple(uuid, kit, exp)
                }
            }
        }
        return list
    }

    override fun allClaimRows(): List<Triple<UUID, String, Int>> {
        reconnectIfClosed()
        val list = mutableListOf<Triple<UUID, String, Int>>()
        connection!!.prepareStatement("SELECT * FROM kit_claims").use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val uuid = UUID.fromString(rs.getString("player_uuid"))
                    val kit  = rs.getString("kit_name")
                    val c    = rs.getInt("claims")
                    list += Triple(uuid, kit, c)
                }
            }
        }
        return list
    }

    override fun setFirstJoinDone(playerId: UUID, done: Boolean) {
        reconnectIfClosed()
        connection!!.prepareStatement(
            """
        INSERT INTO first_join (player_uuid, done)
        VALUES (?, ?)
        ON CONFLICT(player_uuid) DO UPDATE SET done = excluded.done;
        """.trimIndent()
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setInt(2, if (done) 1 else 0)
            ps.executeUpdate()
        }
    }

    override fun isFirstJoinDone(playerId: UUID): Boolean {
        reconnectIfClosed()
        connection!!.prepareStatement(
            "SELECT done FROM first_join WHERE player_uuid = ?"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt("done") != 0 else false
            }
        }
    }

    override fun allFirstJoinRows(): List<Pair<UUID, Boolean>> {
        reconnectIfClosed()
        val list = mutableListOf<Pair<UUID, Boolean>>()
        connection!!.prepareStatement("SELECT player_uuid, done FROM first_join").use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += UUID.fromString(rs.getString("player_uuid")) to (rs.getInt("done") != 0)
                }
            }
        }
        return list
    }

    private fun reconnectIfClosed() {
        if (connection == null || connection!!.isClosed) {
            connect()
        }
    }
}
