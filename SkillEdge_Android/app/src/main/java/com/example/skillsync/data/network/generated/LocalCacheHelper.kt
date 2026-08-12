package com.example.skillsync.data.network.generated

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class LocalCacheHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "skillsync_cache.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_CACHE = "api_cache"
        const val COLUMN_ENDPOINT = "endpoint"
        const val COLUMN_PAYLOAD_HASH = "payload_hash"
        const val COLUMN_JSON_RESPONSE = "json_response"
        const val COLUMN_TIMESTAMP = "timestamp"

        private const val TABLE_CREATE =
            "CREATE TABLE " + TABLE_CACHE + " (" +
                    COLUMN_ENDPOINT + " TEXT NOT NULL, " +
                    COLUMN_PAYLOAD_HASH + " TEXT NOT NULL, " +
                    COLUMN_JSON_RESPONSE + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_ENDPOINT + ", " + COLUMN_PAYLOAD_HASH + "));"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TABLE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CACHE)
        onCreate(db)
    }

    fun saveCache(endpoint: String, payloadHash: String, jsonResponse: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ENDPOINT, endpoint)
            put(COLUMN_PAYLOAD_HASH, payloadHash)
            put(COLUMN_JSON_RESPONSE, jsonResponse)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        
        // Insert with conflict replace to update existing cache
        val result = db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        if(result == -1L) {
             Log.e("LocalCacheHelper", "Failed to insert cache for $endpoint")
        }
        db.close()
    }

    fun getCache(endpoint: String, payloadHash: String): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_CACHE,
            arrayOf(COLUMN_JSON_RESPONSE),
            "$COLUMN_ENDPOINT = ? AND $COLUMN_PAYLOAD_HASH = ?",
            arrayOf(endpoint, payloadHash),
            null, null, null
        )
        
        var jsonResponse: String? = null
        if (cursor.moveToFirst()) {
            jsonResponse = cursor.getString(0)
        }
        cursor.close()
        db.close()
        return jsonResponse
    }

    fun clearCacheForEndpoint(endpoint: String) {
        val db = this.writableDatabase
        db.delete(TABLE_CACHE, "$COLUMN_ENDPOINT = ?", arrayOf(endpoint))
        db.close()
    }
    
    fun clearAllCache() {
        val db = this.writableDatabase
        db.delete(TABLE_CACHE, null, null)
        db.close()
    }
}
