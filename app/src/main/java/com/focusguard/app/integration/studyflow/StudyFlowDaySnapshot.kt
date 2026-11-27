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

        private const val MAX_RAW_JSON_LENGTH = 32_768
        private const val MAX_PENDING_ITEMS = 25
        private const val MAX_FIELD_LENGTH = 160
        private const val MIN_VALID_EPOCH_MS = 1_577_836_800_000L // 2020-01-01
        private const val MAX_FUTURE_SKEW_MS = 300_000L
        private val DATE_KEY_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun fromIntent(intent: Intent): StudyFlowDaySnapshot? {
            if (intent.action != ACTION_SYNC_STUDYFLOW_DAY) return null
            val raw = intent.getStringExtra(EXTRA_SNAPSHOT_JSON) ?: return null
            if (raw.length > MAX_RAW_JSON_LENGTH) return null
            return fromJsonString(raw)
        }

        fun fromJsonString(raw: String?): StudyFlowDaySnapshot? {
            if (raw.isNullOrBlank() || raw.length > MAX_RAW_JSON_LENGTH) return null

            return runCatching {
                val root = JSONObject(raw)
                val dateKey = root.optString("dateKey").sanitize(10)
                if (!DATE_KEY_REGEX.matches(dateKey)) {
                    throw IllegalArgumentException("dateKey invalid")
                }

                val pendingItems = buildList {
                    val array = root.optJSONArray("pendingItems") ?: JSONArray()
                    val count = minOf(array.length(), MAX_PENDING_ITEMS)
                    for (index in 0 until count) {
                        val item = array.optJSONObject(index) ?: continue
                        val title = item.optString("title").sanitize(MAX_FIELD_LENGTH)
                        if (title.isEmpty()) continue

                        add(
                            StudyFlowPendingItem(
                                id = item.optString("id").sanitize(64).ifBlank { "item_$index" },
                                title = title,
                                subject = item.optNullableString("subject", 64),
                                detail = item.optNullableString("detail", MAX_FIELD_LENGTH),
                                kind = item.optString("kind").sanitize(32).ifBlank { "task" },
                                etaMinutes = item.optNullableInt("etaMinutes"),
                                priority = item.optNullableString("priority", 32)
                            )
                        )
                    }
                }

                StudyFlowDaySnapshot(
                    dateKey = dateKey,
                    examId = root.optNullableString("examId", 32),
                    headline = root.optNullableString("headline", MAX_FIELD_LENGTH),
                    focusPrompt = root.optNullableString("focusPrompt", MAX_FIELD_LENGTH),
                    pendingItems = pendingItems,
                    syncedAtEpochMs = run {
                        val now = System.currentTimeMillis()
                        root.optLong("syncedAtEpochMs")
                            .takeIf { it in MIN_VALID_EPOCH_MS..(now + MAX_FUTURE_SKEW_MS) }
                            ?: now
                    }
                )
            }.getOrNull()
        }

        private fun String.sanitize(maxLen: Int): String {
            return replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
                .trim()
                .take(maxLen)
        }

        private fun JSONObject.optNullableString(key: String, maxLen: Int = MAX_FIELD_LENGTH): String? {
            if (!has(key) || isNull(key)) return null
            val value = optString(key).sanitize(maxLen)
            return value.ifEmpty { null }
        }

        private fun JSONObject.optNullableInt(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            return optInt(key).takeIf { it in 1..1440 }
        }
    }
}
