package com.example.remote_control_app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class ConnectionPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "remote_control_prefs"
        private const val KEY_LAST_IP = "last_successful_ip"
        private const val KEY_LAST_PORT = "last_successful_port"
        private const val KEY_LAST_CONNECTION_TIME = "last_connection_time"
        private const val TAG = "ConnectionPreferences"
        
        // Cache validity duration (24 hours)
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L
    }
    
    fun saveSuccessfulConnection(ip: String, port: Int) {
        Log.d(TAG, "Saving successful connection: $ip:$port")
        prefs.edit().apply {
            putString(KEY_LAST_IP, ip)
            putInt(KEY_LAST_PORT, port)
            putLong(KEY_LAST_CONNECTION_TIME, System.currentTimeMillis())
            apply()
        }
    }
    
    fun getLastSuccessfulConnection(): ConnectionInfo? {
        val ip = prefs.getString(KEY_LAST_IP, null)
        val port = prefs.getInt(KEY_LAST_PORT, -1)
        val lastConnectionTime = prefs.getLong(KEY_LAST_CONNECTION_TIME, 0)
        
        if (ip == null || port == -1) {
            return null
        }
        
        // Check if cache is still valid
        val cacheAge = System.currentTimeMillis() - lastConnectionTime
        if (cacheAge > CACHE_VALIDITY_DURATION) {
            Log.d(TAG, "Cache expired for $ip:$port")
            return null
        }
        
        Log.d(TAG, "Retrieved cached connection: $ip:$port")
        return ConnectionInfo(ip, port, lastConnectionTime)
    }
    
    fun clearConnectionCache() {
        Log.d(TAG, "Clearing connection cache")
        prefs.edit().apply {
            remove(KEY_LAST_IP)
            remove(KEY_LAST_PORT)
            remove(KEY_LAST_CONNECTION_TIME)
            apply()
        }
    }
}

data class ConnectionInfo(
    val ip: String,
    val port: Int,
    val lastConnectionTime: Long
) 