package com.auradesk.guard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InterruptionRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "InterruptionRepository"
        private const val DB_NAME = "auradesk_interruptions.db"
        private const val DB_VERSION = 4
        private const val TABLE_NAME = "interruptions"

        @Volatile
        private var instance: InterruptionRepository? = null

        fun getInstance(context: Context): InterruptionRepository {
            return instance ?: synchronized(this) {
                instance ?: InterruptionRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val dbHelper = DatabaseHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _allInterruptions = MutableStateFlow<List<InterruptionEntity>>(emptyList())
    val allInterruptions: StateFlow<List<InterruptionEntity>> = _allInterruptions.asStateFlow()

    private val _activeCapsule = MutableStateFlow<InterruptionEntity?>(null)
    val activeCapsule: StateFlow<InterruptionEntity?> = _activeCapsule.asStateFlow()

    init {
        refreshData()
    }

    private fun refreshData() {
        scope.launch {
            val list = queryAll()
            _allInterruptions.value = list
            _activeCapsule.value = list.firstOrNull { it.status == "NEW" }
        }
    }

    suspend fun insert(entity: InterruptionEntity): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("person_name", entity.personName)
            put("task_summary", entity.taskSummary)
            put("ai_action_item", entity.aiActionItem)
            put("ai_deadline", entity.aiDeadline)
            put("ai_urgency_reason", entity.aiUrgencyReason)
            put("target_component", entity.targetComponent)
            put("raw_transcript", entity.rawTranscript)
            put("has_voice_transcript", if (entity.hasVoiceTranscript) 1 else 0)
            put("context_snippet", entity.contextSnippet)
            put("distance_zone", entity.distanceZone)
            put("duration_sec", entity.durationSec)
            put("timestamp", entity.timestamp)
            put("is_urgent", if (entity.isUrgent) 1 else 0)
            put("status", entity.status)
            put("jovi_synced", if (entity.joviSynced) 1 else 0)
            put("jovi_sync_timestamp", entity.joviSyncTimestamp)
        }
        val id = db.insert(TABLE_NAME, null, values)
        Log.i(TAG, "Inserted interruption capsule #$id: ${entity.personName} - ${entity.taskSummary} (AI Action: ${entity.aiActionItem})")
        refreshData()
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, "id = ?", arrayOf(id.toString()))
        Log.i(TAG, "Deleted interruption capsule #$id")
        refreshData()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, null, null)
        Log.i(TAG, "Cleared all interruption capsules")
        refreshData()
    }

    suspend fun markSavedToNotes(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", "SAVED_TO_NOTES")
            put("jovi_synced", 1)
            put("jovi_sync_timestamp", System.currentTimeMillis())
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
        Log.i(TAG, "Marked interruption capsule #$id as SAVED_TO_NOTES")
        refreshData()
    }

    suspend fun markJoviSynced(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", "SAVED_TO_NOTES")
            put("jovi_synced", 1)
            put("jovi_sync_timestamp", System.currentTimeMillis())
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
        Log.i(TAG, "Marked interruption capsule #$id as Jovi Synced")
        refreshData()
    }

    suspend fun dismiss(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", "DISMISSED")
        }
        db.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
        Log.i(TAG, "Dismissed interruption capsule #$id")
        refreshData()
    }

    suspend fun autoExpireOldEntries(maxAgeHours: Int = 1) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
        val deletedCount = db.delete(TABLE_NAME, "timestamp < ?", arrayOf(cutoffTime.toString()))
        if (deletedCount > 0) {
            Log.i(TAG, "Auto-expired $deletedCount old interruption capsules older than $maxAgeHours hour(s)")
            refreshData()
        }
    }

    private fun queryAll(): List<InterruptionEntity> {
        val list = mutableListOf<InterruptionEntity>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val nameIdx = c.getColumnIndexOrThrow("person_name")
            val taskIdx = c.getColumnIndexOrThrow("task_summary")
            val actionIdx = c.getColumnIndex("ai_action_item")
            val deadlineIdx = c.getColumnIndex("ai_deadline")
            val urgencyReasonIdx = c.getColumnIndex("ai_urgency_reason")
            val compIdx = c.getColumnIndex("target_component")
            val transcriptIdx = c.getColumnIndex("raw_transcript")
            val hasVoiceIdx = c.getColumnIndex("has_voice_transcript")
            val ctxIdx = c.getColumnIndexOrThrow("context_snippet")
            val distIdx = c.getColumnIndexOrThrow("distance_zone")
            val durIdx = c.getColumnIndexOrThrow("duration_sec")
            val timeIdx = c.getColumnIndexOrThrow("timestamp")
            val urgentIdx = c.getColumnIndexOrThrow("is_urgent")
            val statusIdx = c.getColumnIndexOrThrow("status")
            val joviIdx = c.getColumnIndex("jovi_synced")
            val joviTimeIdx = c.getColumnIndex("jovi_sync_timestamp")

            while (c.moveToNext()) {
                val transcript = if (transcriptIdx != -1) c.getString(transcriptIdx) ?: "" else ""
                val hasVoice = if (hasVoiceIdx != -1) c.getInt(hasVoiceIdx) == 1 else transcript.isNotBlank()
                val aiAction = if (actionIdx != -1) c.getString(actionIdx) ?: "" else ""
                val aiDeadline = if (deadlineIdx != -1) c.getString(deadlineIdx) ?: "" else ""
                val aiReason = if (urgencyReasonIdx != -1) c.getString(urgencyReasonIdx) ?: "" else ""
                val comp = if (compIdx != -1) c.getString(compIdx) ?: "" else ""
                val joviSynced = if (joviIdx != -1) c.getInt(joviIdx) == 1 else false
                val joviSyncTime = if (joviTimeIdx != -1) c.getLong(joviTimeIdx) else 0L

                list.add(
                    InterruptionEntity(
                        id = c.getLong(idIdx),
                        personName = c.getString(nameIdx),
                        taskSummary = c.getString(taskIdx),
                        aiActionItem = aiAction,
                        aiDeadline = aiDeadline,
                        aiUrgencyReason = aiReason,
                        targetComponent = comp,
                        rawTranscript = transcript,
                        hasVoiceTranscript = hasVoice,
                        contextSnippet = c.getString(ctxIdx),
                        distanceZone = c.getString(distIdx),
                        durationSec = c.getLong(durIdx),
                        timestamp = c.getLong(timeIdx),
                        isUrgent = c.getInt(urgentIdx) == 1,
                        status = c.getString(statusIdx),
                        joviSynced = joviSynced,
                        joviSyncTimestamp = joviSyncTime
                    )
                )
            }
        }
        return list
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    person_name TEXT NOT NULL,
                    task_summary TEXT NOT NULL,
                    ai_action_item TEXT NOT NULL DEFAULT '',
                    ai_deadline TEXT NOT NULL DEFAULT '',
                    ai_urgency_reason TEXT NOT NULL DEFAULT '',
                    target_component TEXT NOT NULL DEFAULT '',
                    raw_transcript TEXT NOT NULL DEFAULT '',
                    has_voice_transcript INTEGER NOT NULL DEFAULT 0,
                    context_snippet TEXT NOT NULL,
                    distance_zone TEXT NOT NULL,
                    duration_sec INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    is_urgent INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    jovi_synced INTEGER NOT NULL DEFAULT 0,
                    jovi_sync_timestamp INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            try {
                if (oldVersion < 2) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN raw_transcript TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN has_voice_transcript INTEGER NOT NULL DEFAULT 0")
                }
                if (oldVersion < 3) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ai_action_item TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ai_deadline TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN ai_urgency_reason TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN target_component TEXT NOT NULL DEFAULT ''")
                }
                if (oldVersion < 4) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN jovi_synced INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN jovi_sync_timestamp INTEGER NOT NULL DEFAULT 0")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upgrade table error, recreating table", e)
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
            }
        }
    }

}


