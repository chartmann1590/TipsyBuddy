package com.tipsybuddy.app.data.feedback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.feedbackDataStore by preferencesDataStore(name = "feedback_bug_reports")

/**
 * Local tracking of submitted GitHub issue reports (DataStore Preferences).
 * Stores a JSON list under key `bug_reports_list`. Corrupt JSON never crashes —
 * it is treated as an empty list.
 */
class BugReportRepo(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val bugReports: Flow<List<BugReport>> =
        context.feedbackDataStore.data.map { prefs ->
            val raw = prefs[KEY_REPORTS].orEmpty()
            if (raw.isBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString(ListSerializer(BugReport.serializer()), raw)
                        .sortedByDescending { it.number }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun getBugReportsList(): List<BugReport> =
        try {
            bugReports.first()
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun saveBugReport(report: BugReport) {
        val current = getBugReportsList().toMutableList()
        val idx = current.indexOfFirst { it.number == report.number }
        if (idx >= 0) current[idx] = report else current.add(report)
        updateBugReports(current)
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        val sorted = reports.sortedByDescending { it.number }
        val encoded = json.encodeToString(ListSerializer(BugReport.serializer()), sorted)
        context.feedbackDataStore.edit { prefs ->
            prefs[KEY_REPORTS] = encoded
        }
    }

    companion object {
        private val KEY_REPORTS = stringPreferencesKey("bug_reports_list")
    }
}
