package com.example.skillsync.data.cache

/**
 * Manager Viber Automation configuration and preferences.
 */
data class ViberConfig(
    val autoSendDemand: Boolean = true,
    val autoSendWeekly: Boolean = true,
    val autoSendNudges: Boolean = true,
    val dispatchMode: String = MODE_BOT_API,
    val viberBotToken: String = "",
    val webhookUrl: String = "",
    val reporteePhoneMap: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MODE_BOT_API = "VIBER_BOT_API"
        const val MODE_ACCESSIBILITY = "ACCESSIBILITY_AUTO_SEND"
        const val MODE_INTENT_NOTIFICATION = "INTENT_NOTIFICATION"
    }
}

/**
 * Thread-safe disk-backed persistence for Viber Automation settings.
 */
object ViberConfigStore {

    private const val KEY_PREFIX = "viber_config_"

    @Volatile
    private var cachedMap = mutableMapOf<String, ViberConfig>()

    fun load(managerEmail: String): ViberConfig {
        val key = KEY_PREFIX + managerEmail.trim().lowercase()
        cachedMap[key]?.let { return it }

        val stored = LocalCache.loadObject(key, ViberConfig::class.java)
        val config = stored ?: ViberConfig()
        cachedMap[key] = config
        return config
    }

    fun save(managerEmail: String, config: ViberConfig): Boolean {
        val key = KEY_PREFIX + managerEmail.trim().lowercase()
        cachedMap[key] = config
        return LocalCache.saveObject(key, config)
    }

    fun updatePhone(managerEmail: String, trainerEmail: String, phone: String) {
        val current = load(managerEmail)
        val updatedMap = current.reporteePhoneMap.toMutableMap()
        updatedMap[trainerEmail.trim().lowercase()] = phone.trim()
        save(managerEmail, current.copy(reporteePhoneMap = updatedMap))
    }
}
