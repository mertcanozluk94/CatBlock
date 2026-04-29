package com.catblock.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catblock.app.data.AppCatalog
import com.catblock.app.data.AppRule
import com.catblock.app.data.InstalledApp
import com.catblock.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatBlockViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app.applicationContext)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _rules = MutableStateFlow<List<AppRule>>(emptyList())
    val rules: StateFlow<List<AppRule>> = _rules.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                AppCatalog.listLaunchableApps(getApplication())
            }
        }
        viewModelScope.launch {
            repo.rulesFlow.collect { _rules.value = it }
        }
        viewModelScope.launch {
            repo.voiceEnabledFlow.collect { _voiceEnabled.value = it }
        }
        viewModelScope.launch {
            repo.onboardingDoneFlow.collect { _onboardingDone.value = it }
        }
    }

    fun upsert(rule: AppRule) {
        viewModelScope.launch { repo.upsertRule(rule) }
    }

    fun remove(packageName: String) {
        viewModelScope.launch { repo.removeRule(packageName) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setVoiceEnabled(enabled) }
    }

    fun setOnboardingDone(done: Boolean) {
        viewModelScope.launch { repo.setOnboardingDone(done) }
    }
}
