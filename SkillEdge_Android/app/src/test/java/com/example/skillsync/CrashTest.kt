package com.example.skillsync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStreamReader

class CrashTest {
    @Test
    fun testDashboardCrash() {
        val url = URL("https://skilledge-backend-fpcl.onrender.com/api/data/unified-manager-intelligence?email=aishwar_v@koenig-solutions.com")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        try {
            val reader = InputStreamReader(conn.inputStream)
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(reader, type)
            
            val ops = (data["trainers_operational"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
            println("Operational count: ${ops.size}")
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
