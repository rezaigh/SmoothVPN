package com.smoothvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothvpn.App
import com.smoothvpn.core.Profile
import com.smoothvpn.data.ProfileRepository
import com.smoothvpn.data.SubscriptionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiMessage(val text: String)

class MainViewModel : ViewModel() {

    private val repo: ProfileRepository = App.instance.repository
    private val settings = com.smoothvpn.data.SettingsStore(App.instance)

    data class Settings(
        val mux: Boolean, val bypassLan: Boolean,
        val blockAds: Boolean, val domesticDirect: Boolean
    )

    private val _settings = MutableStateFlow(readSettings())
    val settingsState: StateFlow<Settings> = _settings.asStateFlow()

    private fun readSettings() = Settings(
        settings.enableMux, settings.bypassLan, settings.blockAds, settings.domesticDirect
    )

    fun setMux(v: Boolean) { settings.enableMux = v; _settings.value = readSettings() }
    fun setBypassLan(v: Boolean) { settings.bypassLan = v; _settings.value = readSettings() }
    fun setBlockAds(v: Boolean) { settings.blockAds = v; _settings.value = readSettings() }
    fun setDomesticDirect(v: Boolean) { settings.domesticDirect = v; _settings.value = readSettings() }

    enum class SortMode { DEFAULT, PING }
    private val _sortMode = MutableStateFlow(
        if (settings.sortByPing) SortMode.PING else SortMode.DEFAULT
    )
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    fun toggleSort() {
        val next = if (_sortMode.value == SortMode.PING) SortMode.DEFAULT else SortMode.PING
        _sortMode.value = next
        settings.sortByPing = next == SortMode.PING   // persist across restarts
    }

    // selection is declared here so the profiles flow can pin it to the top
    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    val profiles: StateFlow<List<Profile>> =
        combine(repo.profiles, _sortMode, _selectedId) { list, mode, selId ->
            val sorted =
                if (mode == SortMode.PING)
                    list.sortedBy { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs }
                else list
            // float the selected server to the top so it's always first
            val idx = sorted.indexOfFirst { it.id == selId }
            if (idx > 0) {
                ArrayList<Profile>(sorted.size).apply {
                    add(sorted[idx])
                    addAll(sorted.filterIndexed { i, _ -> i != idx })
                }
            } else sorted
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> =
        repo.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun select(id: String) { _selectedId.value = id }
    fun consumeMessage() { _message.value = null }
    fun notify(text: String) { emit(text) }

    fun importFromClipboard(text: String) = viewModelScope.launch {
        if (text.isBlank()) { emit("Clipboard is empty"); return@launch }
        val count = repo.addProfilesFromText(text)
        emit(if (count > 0) "Imported $count server(s)" else "No valid links found")
    }

    fun addSubscription(name: String, url: String) = viewModelScope.launch {
        _busy.value = true
        repo.addSubscription(name.ifBlank { "Subscription" }, url)
        val result = repo.updateAllSubscriptions()
        _busy.value = false
        result.onSuccess { emit("Updated: $it servers") }
            .onFailure { emit("Update failed: ${it.message}") }
    }

    fun updateSubscriptions() = viewModelScope.launch {
        _busy.value = true
        val result = repo.updateAllSubscriptions()
        _busy.value = false
        result.onSuccess { emit("Updated: $it servers") }
            .onFailure { emit("Update failed: ${it.message}") }
    }

    fun deleteSubscription(sub: SubscriptionEntity) = viewModelScope.launch {
        repo.deleteSubscription(sub)
    }

    fun delete(profile: Profile) = viewModelScope.launch { repo.deleteProfile(profile) }

    fun pingAll() = viewModelScope.launch {
        _busy.value = true
        repo.testAllLatency(profiles.value)
        _busy.value = false
        emit("Latency test complete")
    }

    /** Pick the lowest-latency server automatically. */
    fun selectFastest() = viewModelScope.launch {
        val ranked = profiles.value.filter { it.latencyMs > 0 }.minByOrNull { it.latencyMs }
        if (ranked != null) { _selectedId.value = ranked.id; emit("Selected ${ranked.remark}") }
        else emit("Run a latency test first")
    }

    private fun emit(text: String) { _message.value = UiMessage(text) }
}
