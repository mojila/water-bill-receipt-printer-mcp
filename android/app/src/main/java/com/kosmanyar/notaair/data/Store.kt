package com.kosmanyar.notaair.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class StoredUnit(
    val id: String,
    val name: String,
    val usageM3: Int,
    val total: Long
)

data class Settings(
    val period: String,
    val month: String,
    val year: Int,
    val printerName: String,
    val garbageFee: Long
)

data class StoreData(
    val version: Int,
    val settings: Settings,
    val units: List<StoredUnit>
)

/**
 * JSON persistence for `units.json`, compatible with the file used by the MCP server
 * and the Windows desktop app. The active file lives in the app-private data directory;
 * callers can also point at an external file (for example one synced from the desktop app).
 */
object Store {

    const val DATA_FILE_NAME = "units.json"

    fun defaultSettings(): Settings {
        val now = LocalDate.now()
        return Settings(
            month = ReceiptCore.MONTHS[now.monthValue - 1],
            year = now.year,
            period = ReceiptCore.defaultPeriod(),
            printerName = ReceiptCore.DEFAULT_PRINTER_NAME,
            garbageFee = ReceiptCore.UANG_PLASTIK_SAMPAH
        )
    }

    fun defaultStore(): StoreData = StoreData(
        version = 1,
        settings = defaultSettings(),
        units = ReceiptCore.DEFAULT_UNITS.map {
            StoredUnit(ReceiptCore.slugify(it.name), it.name, it.usageM3, it.total)
        }
    )

    fun defaultFile(context: Context): File = File(context.filesDir, DATA_FILE_NAME)

    fun load(file: File): StoreData {
        if (!file.exists()) return defaultStore()
        return try {
            normalize(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (err: Exception) {
            defaultStore()
        }
    }

    fun save(file: File, store: StoreData): StoreData {
        val normalized = normalize(store)
        file.parentFile?.mkdirs()
        file.writeText(toJson(normalized).toString(2) + "\n", Charsets.UTF_8)
        return normalized
    }

    fun normalize(store: StoreData): StoreData {
        val fallback = defaultStore()
        val settings = store.settings
        val year = if (settings.year > 1900) settings.year else fallback.settings.year
        val month = settings.month.ifBlank { fallback.settings.month }
        val units = store.units.filter { it.name.isNotBlank() }.ifEmpty { fallback.units }
        return StoreData(
            version = 1,
            settings = Settings(
                month = month,
                year = year,
                // Month + year are the source of truth; the period label is always derived.
                period = ReceiptCore.makePeriod(month, year),
                printerName = settings.printerName.ifBlank { fallback.settings.printerName },
                garbageFee = if (settings.garbageFee >= 0) settings.garbageFee else fallback.settings.garbageFee
            ),
            units = units
        )
    }

    fun normalize(raw: JSONObject): StoreData {
        val fallback = defaultStore()
        val rawSettings = raw.optJSONObject("settings") ?: JSONObject()
        val rawUnits = raw.optJSONArray("units")

        val year = rawSettings.optInt("year", fallback.settings.year)
            .let { if (it > 1900) it else fallback.settings.year }
        val month = rawSettings.optString("month").ifBlank { fallback.settings.month }

        val units = ArrayList<StoredUnit>()
        if (rawUnits != null) {
            for (i in 0 until rawUnits.length()) {
                val item = rawUnits.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                units.add(
                    StoredUnit(
                        id = item.optString("id").ifBlank { ReceiptCore.slugify(name) }
                            .ifBlank { "unit-${i + 1}" },
                        name = name,
                        usageM3 = item.optInt("usageM3", 0),
                        total = item.optLong("total", 0L)
                    )
                )
            }
        }

        val settings = Settings(
            month = month,
            year = year,
            period = ReceiptCore.makePeriod(month, year),
            printerName = rawSettings.optString("printerName")
                .ifBlank { fallback.settings.printerName },
            garbageFee = if (rawSettings.has("garbageFee")) {
                rawSettings.optLong("garbageFee", fallback.settings.garbageFee)
            } else {
                fallback.settings.garbageFee
            }
        )

        return StoreData(
            version = 1,
            settings = settings,
            units = units.ifEmpty { fallback.units }
        )
    }

    fun toJson(store: StoreData): JSONObject {
        val settingsJson = JSONObject()
            .put("month", store.settings.month)
            .put("year", store.settings.year)
            .put("period", store.settings.period)
            .put("printerName", store.settings.printerName)
            .put("garbageFee", store.settings.garbageFee)

        val unitsJson = JSONArray()
        store.units.forEach { unit ->
            unitsJson.put(
                JSONObject()
                    .put("id", unit.id)
                    .put("name", unit.name)
                    .put("usageM3", unit.usageM3)
                    .put("total", unit.total)
            )
        }

        return JSONObject()
            .put("version", store.version)
            .put("settings", settingsJson)
            .put("units", unitsJson)
    }

    fun receipts(store: StoreData): List<ReceiptData> = store.units.map {
        ReceiptCore.computeReceipt(
            name = it.name,
            usageM3 = it.usageM3,
            total = it.total,
            period = store.settings.period,
            garbageFee = store.settings.garbageFee
        )
    }

    fun findReceipt(receipts: List<ReceiptData>, query: String): ReceiptData? {
        val q = query.lowercase().trim()
        return receipts.firstOrNull { it.name.lowercase() == q }
            ?: receipts.firstOrNull { it.id == q }
            ?: receipts.firstOrNull { it.name.lowercase().contains(q) }
    }
}
