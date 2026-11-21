package com.dsh.batchrenamer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UndoSnapshot(
    val treeUri: String,
    val records: List<RenameRecord>,
    val timestamp: Long,
)

/**
 * Remembers the last completed batch so the user can undo it even after
 * restarting the app. Only the most recent batch is kept.
 */
class UndoStore(context: Context) {

    private val prefs = context.getSharedPreferences("undo_store", Context.MODE_PRIVATE)

    fun save(treeUri: String, records: List<RenameRecord>) {
        if (records.isEmpty()) {
            clear()
            return
        }
        val array = JSONArray()
        for (record in records) {
            array.put(
                JSONObject()
                    .put("from", record.from)
                    .put("to", record.to)
            )
        }
        prefs.edit()
            .putString(KEY_URI, treeUri)
            .putString(KEY_RECORDS, array.toString())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun load(): UndoSnapshot? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        val raw = prefs.getString(KEY_RECORDS, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null

        val records = ArrayList<RenameRecord>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val from = obj.optString("from")
            val to = obj.optString("to")
            if (from.isNotEmpty() && to.isNotEmpty()) {
                records += RenameRecord(from, to)
            }
        }
        if (records.isEmpty()) return null

        return UndoSnapshot(
            treeUri = uri,
            records = records,
            timestamp = prefs.getLong(KEY_TIME, 0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_URI = "tree_uri"
        const val KEY_RECORDS = "records"
        const val KEY_TIME = "timestamp"
    }
}
