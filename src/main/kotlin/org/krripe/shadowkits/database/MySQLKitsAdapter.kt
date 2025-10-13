package org.krripe.shadowkits.database

import org.krripe.shadowkits.config.KitsConfig
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class MySQLKitsAdapter(
    private val config: KitsConfig.DatabaseConfig
) : KitsDatabaseAdapter {

    private var connection: Connection? = null

    override fun connect(): Boolean {
        return try {
            val url = "jdbc:mysql://${config.mysql.host}:${config.mysql.port}/${config.mysql.database}?useSSL=false&allowPublicKeyRetrieval=true" +
                    "&autoReconnect=true&autoReconnectForPools=true"
            connection = DriverManager.getConnection(url, config.mysql.username, config.mysql.password)
            initialiseTables()
            println("[ShadowKits] ✅ Connected to MySQL @ $url")
            true
        } catch (ex: SQLException) {
            println("[ShadowKits] ❌ MySQL connection failed: ${ex.message}")
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

    private fun initialiseTables() {
        reconnectIfClosed()
        connection!!.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS kit_cooldowns (
                    player_uuid VARCHAR(36) NOT NULL,
                    kit_name    VARCHAR(255) NOT NULL,
                    expires_at  BIGINT       NOT NULL,
                    PRIMARY KEY (player_uuid, kit_name)
                )
                """
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS kit_claims (
                    player_uuid VARCHAR(36) NOT NULL,
                    kit_name    VARCHAR(255) NOT NULL,
                    claims      INT          NOT NULL,
                    PRIMARY KEY (player_uuid, kit_name)
                )
                """
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS first_join (
                    player_uuid VARCHAR(36) NOT NULL,
                    done        TINYINT(1)  NOT NULL,
                    PRIMARY KEY (player_uuid)
                )
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
            "No MySQL connection"
        )

        val now = Instant.now().epochSecond
        val newExp = now + cooldownSeconds.coerceAtLeast(0)

        val prevAuto = conn.autoCommit
        val prevIso  = conn.transactionIsolation
        try {
            conn.autoCommit = false
            conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED

            var currentExp: Long? = null
            conn.prepareStatement(
                "SELECT expires_at FROM kit_cooldowns WHERE player_uuid=? AND kit_name=? FOR UPDATE"
            ).use { ps ->
                ps.setString(1, playerId.toString())
                ps.setString(2, kitName.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) currentExp = rs.getLong(1)
                }
            }
            if (currentExp != null && currentExp > now) {
                conn.rollback()
                return KitsDatabaseAdapter.ClaimDecision.denied(
                    KitsDatabaseAdapter.ClaimDecision.Reason.COOLDOWN,
                    "Cooldown active"
                )
            }

            if (useClaims) {
                var curClaims = 0
                conn.prepareStatement(
                    "SELECT claims FROM kit_claims WHERE player_uuid=? AND kit_name=? FOR UPDATE"
                ).use { ps ->
                    ps.setString(1, playerId.toString())
                    ps.setString(2, kitName.lowercase())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) curClaims = rs.getInt(1)
                    }
                }
                if (curClaims >= maxClaims) {
                    conn.rollback()
                    return KitsDatabaseAdapter.ClaimDecision.denied(
                        KitsDatabaseAdapter.ClaimDecision.Reason.MAX_CLAIMS,
                        "Max claims reached"
                    )
                }

                conn.prepareStatement(
                    """
                INSERT INTO kit_claims (player_uuid, kit_name, claims)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE claims = claims + 1
                """.trimIndent()
                ).use { ps ->
                    ps.setString(1, playerId.toString())
                    ps.setString(2, kitName.lowercase())
                    ps.executeUpdate()
                }
            }

            conn.prepareStatement(
                """
            INSERT INTO kit_cooldowns (player_uuid, kit_name, expires_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)
            """.trimIndent()
            ).use { ps ->
                ps.setString(1, playerId.toString())
                ps.setString(2, kitName.lowercase())
                ps.setLong(3, newExp)
                ps.executeUpdate()
            }

            conn.commit()
            return KitsDatabaseAdapter.ClaimDecision.allowed(newExp)
        } catch (ex: SQLException) {
            try { conn.rollback() } catch (_: SQLException) {}
            println("[ShadowKits] ❌ tryConsumeKit error: ${ex.message}")
            return KitsDatabaseAdapter.ClaimDecision.denied(
                KitsDatabaseAdapter.ClaimDecision.Reason.ERROR,
                ex.message
            )
        } finally {
            conn.autoCommit = prevAuto
            conn.transactionIsolation = prevIso
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
        INSERT INTO kit_cooldowns (player_uuid, kit_name, expires_at)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE expires_at = ?
        """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.setLong  (3, expiresAt)
            ps.setLong  (4, expiresAt)
            ps.executeUpdate()
        }
    }

    override fun getCooldown(playerId: UUID, kitName: String): Long {
        if (connection == null || connection!!.isClosed) connect()
        connection!!.prepareStatement(
            "SELECT expires_at FROM kit_cooldowns WHERE player_uuid = ? AND kit_name = ?"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong("expires_at") else 0L
            }
        }
    }

    override fun clearCooldown(playerId: UUID, kitName: String) {
        if (connection == null || connection!!.isClosed) connect()
        connection!!.prepareStatement(
            "DELETE FROM kit_cooldowns WHERE player_uuid = ? AND kit_name = ?"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeUpdate()
        }
    }

    override fun saveClaims(playerId: UUID, kitName: String, claims: Int) {
        if (connection == null || connection!!.isClosed) connect()
        connection!!.prepareStatement(
            """
            INSERT INTO kit_claims (player_uuid, kit_name, claims) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE claims = ?
            """
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.setInt(3, claims)
            ps.setInt(4, claims)
            ps.executeUpdate()
        }
    }

    override fun getClaims(playerId: UUID, kitName: String): Int {
        if (connection == null || connection!!.isClosed) connect()
        connection!!.prepareStatement(
            "SELECT claims FROM kit_claims WHERE player_uuid = ? AND kit_name = ?"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt("claims") else 0
            }
        }
    }

    override fun clearClaims(playerId: UUID, kitName: String) {
        if (connection == null || connection!!.isClosed) connect()
        connection!!.prepareStatement(
            "DELETE FROM kit_claims WHERE player_uuid = ? AND kit_name = ?"
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setString(2, kitName.lowercase())
            ps.executeUpdate()
        }
    }

    override fun allCooldownRows(): List<Triple<UUID, String, Long>> {
        val list = mutableListOf<Triple<UUID, String, Long>>()
        reconnectIfClosed()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT player_uuid, kit_name, expires_at FROM kit_cooldowns").use { rs ->
                while (rs.next()) {
                    list += Triple(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("kit_name"),
                        rs.getLong("expires_at")
                    )
                }
            }
        }
        return list
    }

    override fun allClaimRows(): List<Triple<UUID, String, Int>> {
        val list = mutableListOf<Triple<UUID, String, Int>>()
        reconnectIfClosed()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT player_uuid, kit_name, claims FROM kit_claims").use { rs ->
                while (rs.next()) {
                    list += Triple(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("kit_name"),
                        rs.getInt("claims")
                    )
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
        ON DUPLICATE KEY UPDATE done = VALUES(done)
        """.trimIndent()
        ).use { ps ->
            ps.setString(1, playerId.toString())
            ps.setBoolean(2, done)
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
                return if (rs.next()) rs.getBoolean("done") else false
            }
        }
    }

    override fun allFirstJoinRows(): List<Pair<UUID, Boolean>> {
        reconnectIfClosed()
        val list = mutableListOf<Pair<UUID, Boolean>>()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT player_uuid, done FROM first_join").use { rs ->
                while (rs.next()) {
                    list += UUID.fromString(rs.getString("player_uuid")) to rs.getBoolean("done")
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
