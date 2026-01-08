package org.krripe.shadowkits.gui

import com.google.gson.reflect.TypeToken
import org.krripe.shadowkits.config.KitsConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object PrivewGUIConfig {

    fun resetCacheTemplate() { cachedTemplateFromOld = null }

    data class PerKitPreviewConfig(
        val title: String = "&6Kit Preview: %kit%",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillers: MutableList<Filler> = mutableListOf(
            Filler(
                name = " ",
                icon = "minecraft:black_stained_glass_pane",
                slots = listOf("0","1","2","3","4","5","6","7","8","9","17","18","26","27","35","36","44","45","46","47","48","50","51","52","53")
            )
        ),
        val closeButton: Button? = Button(slot = 49, icon = "minecraft:barrier", name = "&cClose"),
        val contentSlots: List<String> = listOf("10..16","19..25","28..34","37..43")
    )

    private data class OldButtonConfig(val slot: Int, val material: String, val name: String, val customModelData: Int? = null)
    private data class OldPreviewMenuConfig(
        val title: String = "&6Kit Preview",
        val size: Int = 54,
        val fillItem: String = "minecraft:black_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean = true,
        val fillerSlots: List<Int> = listOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53),
        val previewSlots: List<Int> = listOf(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43),
        val closeButton: OldButtonConfig = OldButtonConfig(49, "minecraft:barrier", "&cClose")
    )

    private val rootDir        = File("config/shadowkits")
    private val guiDir         = File(rootDir, "gui")
    private val previewsDir    = File(guiDir, "previews")
    private val backupsDir     = File(rootDir, "backups")
    private val oldPerKitDir   = File(rootDir, "previews")
    private val oldGlobalFile  = File(rootDir, "menu_preview.json")

    @Volatile private var oldBackedUpOnce = false
    @Volatile private var cachedTemplateFromOld: OldPreviewMenuConfig? = null

    fun getFor(kitName: String): PerKitPreviewConfig {
        previewsDir.mkdirs(); backupsDir.mkdirs()

        val newFile = File(previewsDir, "${kitName.lowercase()}_preview.json")
        if (newFile.exists()) return load(newFile, PerKitPreviewConfig())

        val oldPerKit = File(oldPerKitDir, "${kitName.lowercase()}_preview.json")
        if (oldPerKit.exists()) {
            val txt = runCatching { oldPerKit.readText() }.getOrNull()
            if (txt != null) {
                newFile.parentFile.mkdirs()
                newFile.writeText(txt)
                backupOnce(oldPerKit, "preview_${kitName.lowercase()}")
                return load(newFile, PerKitPreviewConfig())
            }
        }

        val fromOld = ensureTemplateFromOld()
        val migrated = migrateOldToNew(fromOld, kitName)
        newFile.writeText(KitsConfig.gson.toJson(migrated))
        return migrated
    }

    private fun ensureTemplateFromOld(): OldPreviewMenuConfig {
        cachedTemplateFromOld?.let { return it }
        val template = if (oldGlobalFile.exists()) {
            try {
                val type = object : TypeToken<OldPreviewMenuConfig>() {}.type
                val loaded: OldPreviewMenuConfig = KitsConfig.gson.fromJson(oldGlobalFile.readText(), type)
                backupOldGlobalOnce()
                loaded
            } catch (_: Exception) { OldPreviewMenuConfig() }
        } else OldPreviewMenuConfig()
        cachedTemplateFromOld = template
        return template
    }

    private fun backupOldGlobalOnce() {
        if (oldBackedUpOnce) return
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val bak = File(backupsDir, "menu_preview_$stamp.json")
            oldGlobalFile.renameTo(bak)
        }
        oldBackedUpOnce = true
    }

    private fun backupOnce(file: File, prefix: String) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val bak = File(backupsDir, "${prefix}_$stamp.json")
            bak.parentFile.mkdirs()
            file.renameTo(bak)
        }
    }

    private fun migrateOldToNew(old: OldPreviewMenuConfig, kitName: String): PerKitPreviewConfig {
        val filler = Filler(
            name = old.fillerName,
            icon = old.fillItem,
            slots = old.fillerSlots.map { it.toString() },
            modelData = old.fillerCustomModelData,
            glow = false,
            hideAdditionalTooltip = true,
            hideTooltip = false
        )
        val closeBtn = Button(
            slot = old.closeButton.slot,
            icon = old.closeButton.material,
            name = old.closeButton.name,
            modelData = old.closeButton.customModelData
        )
        return PerKitPreviewConfig(
            title = old.title.replace("%kit%", "%kit%"),
            size = old.size,
            fillersEnabled = old.fillersEnabled,
            fillers = mutableListOf(filler),
            closeButton = closeBtn,
            contentSlots = old.previewSlots.map { it.toString() }
        )
    }

    private inline fun <reified T> load(file: File, fallback: T): T =
        try { KitsConfig.gson.fromJson(file.readText(), object : TypeToken<T>() {}.type) ?: fallback }
        catch (_: Exception) { fallback }

    fun saveFor(kitName: String, cfg: PerKitPreviewConfig) {
        val f = File(previewsDir, "${kitName.lowercase()}_preview.json")
        f.parentFile.mkdirs()
        f.writeText(KitsConfig.gson.toJson(cfg))
    }
}