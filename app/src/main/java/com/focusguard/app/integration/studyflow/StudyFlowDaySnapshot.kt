package com.focusguard.app.integration.studyflow

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class StudyFlowPendingItem(
    val id: String,
    val title: String,
    val subject: String?,
    val detail: String?,
    val kind: String,
    val etaMinutes: Int?,
    val priority: String?
)

data class StudyFlowDaySnapshot(
    val dateKey: String,
    val examId: String?,
    val headline: String?,
    val focusPrompt: String?,
    val pendingItems: List<StudyFlowPendingItem>,
    val syncedAtEpochMs: Long
) {

    fun isFreshFor(todayKey: String): Boolean = dateKey == todayKey

    fun toJsonString(): String {
        val root = JSONObject()
            .put("dateKey", dateKey)
            .put("examId", examId ?: JSONObject.NULL)
            .put("headline", headline ?: JSONObject.NULL)
            .put("focusPrompt", focusPrompt ?: JSONObject.NULL)
            .put("syncedAtEpochMs", syncedAtEpochMs)

        val items = JSONArray()
        pendingItems.forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("subject", item.subject ?: JSONObject.NULL)
                    .put("detail", item.detail ?: JSONObject.NULL)
                    .put("kind", item.kind)
                    .put("etaMinutes", item.etaMinutes ?: JSONObject.NULL)
                    .put("priority", item.priority ?: JSONObject.NULL)
            )
        }
        root.put("pendingItems", items)
        return root.toString()
    }

    companion object {
        const val ACTION_SYNC_STUDYFLOW_DAY = "com.focusguard.app.action.SYNC_STUDYFLOW_DAY"
        const val EXTRA_SNAPSHOT_JSON = "com.focusguard.app.extra.STUDYFLOW_SNAPSHOT_JSON"

        fun fromIntent(intent: Intent): StudyFlowDaySnapshot? {
            return fromJsonString(intent.getStringExtra(EXTRA_SNAPSHOT_JSON))
        }

        fun fromJsonString(raw: String?): StudyFlowDaySnapshot? {
            if (raw.isNullOrBlank()) return null

            return runCatching {
                val root = JSONObject(raw)
                val dateKey = root.optString("dateKey").trim()
                if (dateKey.isEmpty()) {
                    throw IllegalArgumentException("dateKey missing")
                }

                val pendingItems = buildList {
                    val array = root.optJSONArray("pendingItems") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val title = item.optString("title").trim()
                        if (title.isEmpty()) continue

                        add(
                            StudyFlowPendingItem(
                                id = item.optString("id").ifBlank { "item_$index" },
                                title = title,
                                subject = item.optNullableString("subject"),
                                detail = item.optNullableString("detail"),
                                kind = item.optString("kind").ifBlank { "task" },
                                etaMinutes = item.optNullableInt("etaMinutes"),
                                priority = item.optNullableString("priority")
                            )
                        )
                    }
                }

                StudyFlowDaySnapshot(
                    dateKey = dateKey,
                    examId = root.optNullableString("examId"),
                    headline = root.optNullableString("headline"),
                    focusPrompt = root.optNullableString("focusPrompt"),
                    pendingItems = pendingItems,
                    syncedAtEpochMs = root.optLong("syncedAtEpochMs").takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                )
            }.getOrNull()
        }

        private fun JSONObject.optNullableString(key: String): String? {
            val value = optString(key).trim()
            return value.ifEmpty { null }
        }

        private fun JSONObject.optNullableInt(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            return optInt(key).takeIf { it > 0 }
        }
    }
}
