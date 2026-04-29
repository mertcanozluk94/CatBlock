package com.catblock.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "catblock_prefs")

/**
 * Per-app rule: triggerMinutes = continuous-use threshold before cat appears.
 * breakMinutes = how long the user must wait before they can use the app again.
 */
data class AppRule(
    val packageName: String,
    val triggerMinutes: Int,
    val breakMinutes: Int
)

class SettingsRepository(private val context: Context) {

    private val rulesKey = stringPreferencesKey("app_rules_json")
    private val breakUntilKey = longPreferencesKey("break_until")
    private val activePackageKey = stringPreferencesKey("active_package")
    private val defaultBreakKey = intPreferencesKey("default_break_minutes")
    private val voiceEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("voice_enabled")
    private val onboardingDoneKey = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_done")

    val rulesFlow: Flow<List<AppRule>> = context.dataStore.data.map { prefs ->
        decodeRules(prefs[rulesKey] ?: "{}")
    }

    val defaultBreakFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[defaultBreakKey] ?: 2
    }

    val voiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[voiceEnabledKey] ?: false
    }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingDoneKey] ?: false
    }

    suspend fun isVoiceEnabled(): Boolean = voiceEnabledFlow.first()

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[voiceEnabledKey] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[onboardingDoneKey] = done }
    }

    suspend fun getRules(): List<AppRule> = rulesFlow.first()

    suspend fun upsertRule(rule: AppRule) {
        context.dataStore.edit { prefs ->
            val current = decodeRules(prefs[rulesKey] ?: "{}").toMutableList()
            val idx = current.indexOfFirst { it.packageName == rule.packageName }
            if (idx >= 0) current[idx] = rule else current.add(rule)
            prefs[rulesKey] = encodeRules(current)
        }
    }

    suspend fun removeRule(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = decodeRules(prefs[rulesKey] ?: "{}").filter { it.packageName != packageName }
            prefs[rulesKey] = encodeRules(current)
        }
    }

    suspend fun setDefaultBreak(minutes: Int) {
        context.dataStore.edit { it[defaultBreakKey] = minutes }
    }

    suspend fun startBreak(packageName: String, durationMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[breakUntilKey] = System.currentTimeMillis() + durationMillis
            prefs[activePackageKey] = packageName
        }
    }

    suspend fun clearBreak() {
        context.dataStore.edit { prefs ->
            prefs.remove(breakUntilKey)
            prefs.remove(activePackageKey)
        }
    }

    suspend fun getBreakUntil(): Long = context.dataStore.data.first()[breakUntilKey] ?: 0L
    suspend fun getActiveBreakPackage(): String? = context.dataStore.data.first()[activePackageKey]

    private fun encodeRules(rules: List<AppRule>): String {
        val json = JSONObject()
        rules.forEach {
            val r = JSONObject()
            r.put("trigger", it.triggerMinutes)
            r.put("break", it.breakMinutes)
            json.put(it.packageName, r)
        }
        return json.toString()
    }

    private fun decodeRules(s: String): List<AppRule> {
        val out = mutableListOf<AppRule>()
        val json = JSONObject(s)
        json.keys().forEach { pkg ->
            val r = json.getJSONObject(pkg)
            out.add(AppRule(pkg, r.getInt("trigger"), r.getInt("break")))
        }
        return out
    }
}
