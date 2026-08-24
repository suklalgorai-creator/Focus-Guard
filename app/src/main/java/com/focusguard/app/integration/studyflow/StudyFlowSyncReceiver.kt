package com.focusguard.app.integration.studyflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.app.persistence.FocusGuardPrefs

class StudyFlowSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != StudyFlowDaySnapshot.ACTION_SYNC_STUDYFLOW_DAY) return

        val snapshot = StudyFlowDaySnapshot.fromIntent(intent)
        if (snapshot == null) {
            Log.w(TAG, "StudyFlow sync ignored: payload missing or invalid")
            return
        }

        val prefs = FocusGuardPrefs(context)
        prefs.storeStudyFlowDaySnapshot(snapshot)

        Log.d(
            TAG,
            "StudyFlow sync saved for ${snapshot.dateKey} with ${snapshot.pendingItems.size} items"
        )
    }

    companion object {
        private const val TAG = "StudyFlowSyncReceiver"
    }
}
