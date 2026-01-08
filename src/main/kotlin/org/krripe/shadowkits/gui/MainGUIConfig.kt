package org.krripe.shadowkits.gui

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object MainGUIConfig {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val newConfigPath: Path = Paths.get("config", "shadowkits", "gui", "main_menu.json")
    private val oldMenuPath: Path   = Paths.get("config", "shadowkits", "menu.json")
    private val backupsDir: Path    = Paths.get("config", "shadowkits", "backups")

    private data class OldButtonConfig(
        val slot: Int,
        val material: String,
        val name: String,
        val customModelData: Int? = null
    )
    private data class OldMenuConfig(
        val title: String = "&6Select a Kit",
        val size: Int = 54,
        val pages: Int = 3,
        val fillItem: String = "minecraft:black_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean = true,
        val fillerSlots: List<Int> = listOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53),
        val nextPageButton: OldButtonConfig = OldButtonConfig(53, "minecraft:arrow", "&aNext Page"),
        val prevPageButton: OldButtonConfig = OldButtonConfig(45, "minecraft:arrow", "&aPrevious Page"),
        val closeButton:     OldButtonConfig = OldButtonConfig(49, "minecraft:barrier", "&cClose")
    )

    @Volatile
    private var cached: PagedWithContent? = null

    fun current(): PagedWithContent {
        cached?.let { return it }

        if (Files.exists(newConfigPath)) {
            cached = safeLoad(newConfigPath) ?: defaultFromOldInMemory()
            return cached!!
        }

        val migrated = defaultFromOldInMemory()
        write(newConfigPath, migrated)
        if (Files.exists(oldMenuPath)) {
            backupOld(oldMenuPath)
        }

        cached = migrated
        return migrated
    }

    fun reload(): PagedWithContent {
        cached = null
        return current()
    }

    private fun defaultFromOldInMemory(): PagedWithContent {
        val old = safeLoadOld(oldMenuPath)
        return if (old != null) {
            PagedWithContent(
                title = old.title,
                size = old.size,
                fillersEnabled = old.fillersEnabled,
                fillers = mutableListOf(
                    Filler(
                        name = old.fillerName,
                        icon = old.fillItem,
                        slots = old.fillerSlots.map { it.toString() },
                        modelData = old.fillerCustomModelData,
                        glow = false,
                        hideAdditionalTooltip = true,
                        hideTooltip = false
                    )
                ),
                nextPageButton = Button(
                    slot = old.nextPageButton.slot,
                    icon = old.nextPageButton.material,
                    name = old.nextPageButton.name,
                    modelData = old.nextPageButton.customModelData
                ),
                prevPageButton = Button(
                    slot = old.prevPageButton.slot,
                    icon = old.prevPageButton.material,
                    name = old.prevPageButton.name,
                    modelData = old.prevPageButton.customModelData
                ),
                closeButton = Button(
                    slot = old.closeButton.slot,
                    icon = old.closeButton.material,
                    name = old.closeButton.name,
                    modelData = old.closeButton.customModelData
                ),
                contentSlots = emptyList()
            )
        } else {
            PagedWithContent(
                title = "&6Select a Kit",
                size = 54,
                fillersEnabled = true,
                fillers = mutableListOf(
                    Filler(
                        name = " ",
                        icon = "minecraft:black_stained_glass_pane",
                        slots = listOf("0","1","2","3","4","5","6","7","8","9","17","18","26","27","35","36","44","45","46","47","48","50","51","52","53")
                    )
                ),
                nextPageButton = Button(53, "minecraft:arrow", "&aNext Page"),
                prevPageButton = Button(45, "minecraft:arrow", "&aPrevious Page"),
                closeButton    = Button(49, "minecraft:barrier", "&cClose"),
                contentSlots = emptyList()
            )
        }
    }

    private fun safeLoad(path: Path): PagedWithContent? = try {
        val txt = Files.readString(path)
        gson.fromJson<PagedWithContent>(txt, object : TypeToken<PagedWithContent>() {}.type)
    } catch (_: Exception) { null }

    private fun safeLoadOld(path: Path): OldMenuConfig? = try {
        if (!Files.exists(path)) return null
        val txt = Files.readString(path)
        gson.fromJson<OldMenuConfig>(txt, object : TypeToken<OldMenuConfig>() {}.type)
    } catch (_: Exception) { null }

    private fun write(path: Path, cfg: PagedWithContent) {
        Files.createDirectories(path.parent)
        Files.writeString(path, gson.toJson(cfg))
    }

    private fun backupOld(oldPath: Path) {
        try {
            Files.createDirectories(backupsDir)
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val targetDir = backupsDir.resolve(stamp)
            Files.createDirectories(targetDir)

            val target = targetDir.resolve(oldPath.fileName.toString())
            runCatching {
                Files.move(oldPath, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                runCatching {
                    Files.copy(oldPath, target, StandardCopyOption.REPLACE_EXISTING)
                    Files.deleteIfExists(oldPath)
                }
            }
        } catch (_: Exception) {
        }
    }
}