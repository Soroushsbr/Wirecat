package com.soroush.wirecat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WirecatDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true) // off by default in SQLite unless explicitly enabled
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_TYPE TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER NOT NULL,
                $COL_TOTAL_BYTES INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_APP_USAGE (
                $COL_APP_USAGE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_FK INTEGER NOT NULL,
                $COL_PACKAGE_NAME TEXT NOT NULL,
                $COL_APP_LABEL TEXT NOT NULL,
                $COL_BYTES INTEGER NOT NULL,
                FOREIGN KEY($COL_SESSION_FK) REFERENCES $TABLE_SESSIONS($COL_SESSION_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PACKETS (
                $COL_PACKET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKET_SESSION_FK INTEGER NOT NULL,
                $COL_PACKET_TYPE TEXT NOT NULL,
                $COL_PACKET_DIRECTION TEXT NOT NULL,
                $COL_PACKET_TIME INTEGER NOT NULL,
                $COL_PACKET_APP_LABEL TEXT,
                $COL_PACKET_SRC_ADDR TEXT,
                $COL_PACKET_DST_ADDR TEXT,
                $COL_PACKET_SRC_PORT INTEGER,
                $COL_PACKET_DST_PORT INTEGER,
                $COL_PACKET_SIZE INTEGER,
                $COL_PACKET_IP_VERSION INTEGER,
                FOREIGN KEY($COL_PACKET_SESSION_FK) REFERENCES $TABLE_SESSIONS($COL_SESSION_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_app_usage_session ON $TABLE_APP_USAGE($COL_SESSION_FK)")
        db.execSQL("CREATE INDEX idx_packets_session ON $TABLE_PACKETS($COL_PACKET_SESSION_FK)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        db.execSQL("DROP TABLE IF EXISTS $TABLE_PACKETS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_APP_USAGE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun saveSession(session: SessionEntity, apps: List<LiveAppUsage>, packets: List<PacketLogEntry> = emptyList()): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sessionValues = ContentValues().apply {
                put(COL_SESSION_TYPE, session.type.name)
                put(COL_START_TIME, session.startTimeMillis)
                put(COL_END_TIME, session.endTimeMillis)
                put(COL_TOTAL_BYTES, session.totalBytes)
            }
            val sessionId = db.insertOrThrow(TABLE_SESSIONS, null, sessionValues)

            for (app in apps) {
                val appValues = ContentValues().apply {
                    put(COL_SESSION_FK, sessionId)
                    put(COL_PACKAGE_NAME, app.packageName)
                    put(COL_APP_LABEL, app.appLabel)
                    put(COL_BYTES, app.bytes)
                }
                db.insertOrThrow(TABLE_APP_USAGE, null, appValues)
            }

            for (packet in packets) {
                val packetValues = ContentValues().apply {
                    put(COL_PACKET_SESSION_FK, sessionId)
                    put(COL_PACKET_TYPE, packet.type.name)
                    put(COL_PACKET_DIRECTION, packet.direction.name)
                    put(COL_PACKET_TIME, packet.timestampMillis)
                    put(COL_PACKET_APP_LABEL, packet.appLabel)
                    put(COL_PACKET_SRC_ADDR, packet.sourceAddress)
                    put(COL_PACKET_DST_ADDR, packet.destAddress)
                    put(COL_PACKET_SRC_PORT, packet.sourcePort)
                    put(COL_PACKET_DST_PORT, packet.destPort)
                    put(COL_PACKET_SIZE, packet.sizeBytes)
                    put(COL_PACKET_IP_VERSION, packet.ipVersion)
                }
                db.insertOrThrow(TABLE_PACKETS, null, packetValues)
            }

            db.setTransactionSuccessful()
            return sessionId
        } finally {
            db.endTransaction()
        }
    }

    fun getAllSessions(): List<SessionWithApps> {
        val db = readableDatabase
        val sessions = ArrayList<SessionEntity>()

        db.rawQuery(
            "SELECT $COL_SESSION_ID, $COL_SESSION_TYPE, $COL_START_TIME, $COL_END_TIME, $COL_TOTAL_BYTES " +
                "FROM $TABLE_SESSIONS ORDER BY $COL_START_TIME DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sessions.add(
                    SessionEntity(
                        id = cursor.getLong(0),
                        type = parseType(cursor.getString(1)),
                        startTimeMillis = cursor.getLong(2),
                        endTimeMillis = cursor.getLong(3),
                        totalBytes = cursor.getLong(4)
                    )
                )
            }
        }

        return sessions.map { SessionWithApps(it, getAppsForSession(db, it.id), getPacketCount(db, it.id)) }
    }

    fun getSession(sessionId: Long): SessionWithApps? {
        val db = readableDatabase
        var session: SessionEntity? = null

        db.rawQuery(
            "SELECT $COL_SESSION_ID, $COL_SESSION_TYPE, $COL_START_TIME, $COL_END_TIME, $COL_TOTAL_BYTES " +
                "FROM $TABLE_SESSIONS WHERE $COL_SESSION_ID = ?",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                session = SessionEntity(
                    id = cursor.getLong(0),
                    type = parseType(cursor.getString(1)),
                    startTimeMillis = cursor.getLong(2),
                    endTimeMillis = cursor.getLong(3),
                    totalBytes = cursor.getLong(4)
                )
            }
        }

        val found = session ?: return null
        return SessionWithApps(found, getAppsForSession(db, found.id), getPacketCount(db, found.id))
    }

    fun getPacketsForSession(sessionId: Long): List<PacketEntity> {
        val db = readableDatabase
        val packets = ArrayList<PacketEntity>()
        db.rawQuery(
            "SELECT $COL_PACKET_ID, $COL_PACKET_SESSION_FK, $COL_PACKET_TYPE, $COL_PACKET_DIRECTION, " +
                "$COL_PACKET_TIME, $COL_PACKET_APP_LABEL, $COL_PACKET_SRC_ADDR, $COL_PACKET_DST_ADDR, " +
                "$COL_PACKET_SRC_PORT, $COL_PACKET_DST_PORT, $COL_PACKET_SIZE, $COL_PACKET_IP_VERSION FROM $TABLE_PACKETS " +
                "WHERE $COL_PACKET_SESSION_FK = ? ORDER BY $COL_PACKET_TIME DESC",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                packets.add(
                    PacketEntity(
                        id = cursor.getLong(0),
                        sessionId = cursor.getLong(1),
                        type = try { PacketType.valueOf(cursor.getString(2)) } catch (e: Exception) { PacketType.OTHER },
                        direction = try { PacketDirection.valueOf(cursor.getString(3)) } catch (e: Exception) { PacketDirection.SENT },
                        timestampMillis = cursor.getLong(4),
                        appLabel = if (cursor.isNull(5)) null else cursor.getString(5),
                        sourceAddress = if (cursor.isNull(6)) null else cursor.getString(6),
                        destAddress = if (cursor.isNull(7)) null else cursor.getString(7),
                        sourcePort = if (cursor.isNull(8)) null else cursor.getInt(8),
                        destPort = if (cursor.isNull(9)) null else cursor.getInt(9),
                        sizeBytes = if (cursor.isNull(10)) null else cursor.getInt(10),
                        ipVersion = if (cursor.isNull(11)) null else cursor.getInt(11)
                    )
                )
            }
        }
        return packets
    }

    fun deleteSession(sessionId: Long) {
        writableDatabase.delete(TABLE_SESSIONS, "$COL_SESSION_ID = ?", arrayOf(sessionId.toString()))
    }

    fun deleteAllSessions() {
        val db = writableDatabase
        db.delete(TABLE_SESSIONS, null, null)
    }

    private fun getAppsForSession(db: SQLiteDatabase, sessionId: Long): List<AppUsageEntity> {
        val apps = ArrayList<AppUsageEntity>()
        db.rawQuery(
            "SELECT $COL_APP_USAGE_ID, $COL_SESSION_FK, $COL_PACKAGE_NAME, $COL_APP_LABEL, $COL_BYTES " +
                "FROM $TABLE_APP_USAGE WHERE $COL_SESSION_FK = ? ORDER BY $COL_BYTES DESC",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                apps.add(
                    AppUsageEntity(
                        id = cursor.getLong(0),
                        sessionId = cursor.getLong(1),
                        packageName = cursor.getString(2),
                        appLabel = cursor.getString(3),
                        bytes = cursor.getLong(4)
                    )
                )
            }
        }
        return apps
    }

    private fun getPacketCount(db: SQLiteDatabase, sessionId: Long): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_PACKETS WHERE $COL_PACKET_SESSION_FK = ?",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun parseType(raw: String?): SessionType =
        try { SessionType.valueOf(raw ?: "") } catch (e: Exception) { SessionType.MONITOR }

    companion object {
        private const val DB_NAME = "wirecat.db"
        private const val DB_VERSION = 4 // bumped: added ip_version column to packets

        private const val TABLE_SESSIONS = "sessions"
        private const val COL_SESSION_ID = "id"
        private const val COL_SESSION_TYPE = "type"
        private const val COL_START_TIME = "start_time"
        private const val COL_END_TIME = "end_time"
        private const val COL_TOTAL_BYTES = "total_bytes"

        private const val TABLE_APP_USAGE = "app_usage"
        private const val COL_APP_USAGE_ID = "id"
        private const val COL_SESSION_FK = "session_id"
        private const val COL_PACKAGE_NAME = "package_name"
        private const val COL_APP_LABEL = "app_label"
        private const val COL_BYTES = "bytes"

        private const val TABLE_PACKETS = "packets"
        private const val COL_PACKET_ID = "id"
        private const val COL_PACKET_SESSION_FK = "session_id"
        private const val COL_PACKET_TYPE = "type"
        private const val COL_PACKET_DIRECTION = "direction"
        private const val COL_PACKET_TIME = "timestamp"
        private const val COL_PACKET_APP_LABEL = "app_label"
        private const val COL_PACKET_SRC_ADDR = "src_addr"
        private const val COL_PACKET_DST_ADDR = "dst_addr"
        private const val COL_PACKET_SRC_PORT = "src_port"
        private const val COL_PACKET_DST_PORT = "dst_port"
        private const val COL_PACKET_SIZE = "size_bytes"
        private const val COL_PACKET_IP_VERSION = "ip_version"

        @Volatile private var INSTANCE: WirecatDbHelper? = null

        fun get(context: Context): WirecatDbHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WirecatDbHelper(context.applicationContext).also { INSTANCE = it }
            }
    }
}
