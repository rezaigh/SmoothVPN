package com.smoothvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoothvpn.App
import com.smoothvpn.core.Profile
import com.smoothvpn.core.RoutingOptions
import com.smoothvpn.core.ShareLinkBuilder
import com.smoothvpn.core.XrayConfigBuilder
import com.smoothvpn.data.ProfileRepository
import com.smoothvpn.data.SettingsStore
import com.smoothvpn.data.SubscriptionEntity
import com.smoothvpn.net.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(val text: String)

class MainViewModel : ViewModel() {

    private val repo: ProfileRepository = App.instance.repository
    private val settings = SettingsStore(App.instance)

    // ---- settings -----------------------------------------------------------

    data class Settings(
        val mux: Boolean,
        val muxConcurrency: Int,
        val bypassLan: Boolean,
        val blockAds: Boolean,
        val domesticDirect: Boolean,
        val fragmentation: Boolean,
        val ipv6: Boolean,
        val perAppMode: String,
        val perAppCount: Int
    )

    private val _settings = MutableStateFlow(readSettings())
    val settingsState: StateFlow<Settings> = _settings.asStateFlow()

    private fun readSettings() = Settings(
        mux = settings.enableMux,
        muxConcurrency = settings.muxConcurrency,
        bypassLan = settings.bypassLan,
        blockAds = settings.blockAds,
        domesticDirect = settings.domesticDirect,
        fragmentation = settings.fragmentation,
        ipv6 = settings.ipv6,
        perAppMode = settings.perAppMode,
        perAppCount = settings.perAppPackages.size
    )

    private fun refresh() { _settings.value = readSettings() }

    fun setMux(v: Boolean) { settings.enableMux = v; refresh() }
    fun setMuxConcurrency(v: Int) { settings.muxConcurrency = v; refresh() }
    fun setBypassLan(v: Boolean) { settings.bypassLan = v; refresh() }
    fun setBlockAds(v: Boolean) { settings.blockAds = v; refresh() }
    fun setDomesticDirect(v: Boolean) { settings.domesticDirect = v; refresh() }
    fun setFragmentation(v: Boolean) { settings.fragmentation = v; refresh() }
    fun setIpv6(v: Boolean) { settings.ipv6 = v; refresh() }

    // ---- per-app split tunnel ----------------------------------------------

    private val _perApp = MutableStateFlow(settings.perAppPackages)
    val perAppPackages: StateFlow<Set<String>> = _perApp.asStateFlow()

    fun setPerAppMode(mode: String) { settings.perAppMode = mode; refresh() }

    fun togglePerApp(pkg: String, on: Boolean) {
        val next = settings.perAppPackages.toMutableSet()
        if (on) next.add(pkg) else next.remove(pkg)
        settings.perAppPackages = next
        _perApp.value = next
        refresh()
    }

    fun clearPerApp() {
        settings.perAppPackages = emptySet()
        _perApp.value = emptySet()
        refresh()
    }

    // ---- updates ------------------------------------------------------------

    private val _update = MutableStateFlow<UpdateChecker.Result?>(null)
    val updateState: StateFlow<UpdateChecker.Result?> = _update.asStateFlow()
    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking.asStateFlow()

    fun checkForUpdate() = viewModelScope.launch {
        _updateChecking.value = true
        val result = withContext(Dispatchers.IO) {
            runCatching { UpdateChecker.check(com.smoothvpn.BuildConfig.VERSION_NAME) }.getOrNull()
        }
        _updateChecking.value = false
        if (result == null) { emit("Couldn't reach GitHub — check your connection") }
        else {
            _update.value = result
            emit(if (result.updateAvailable) "Update available: ${result.latest}" else "You're on the latest version")
        }
    }

    // ---- config helpers (for the server-detail screen) ----------------------

    fun shareLink(p: Profile): String = ShareLinkBuilder.build(p)

    fun rawConfig(p: Profile): String =
        XrayConfigBuilder.build(p, RoutingOptions(
            enableMux = settings.enableMux,
            muxConcurrency = settings.muxConcurrency,
            bypassLan = settings.bypassLan,
            blockAds = settings.blockAds,
            domesticDirect = settings.domesticDirect,
            geoAssetsAvailable = false
        ))

    // ---- sort ---------------------------------------------------------------

    enum class SortMode { DEFAULT, PING }
    private val _sortMode = MutableStateFlow(
        if (settings.sortByPing) SortMode.PING else SortMode.DEFAULT
    )
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    fun toggleSort() {
        val next = if (_sortMode.value == SortMode.PING) SortMode.DEFAULT else SortMode.PING
        _sortMode.value = next
        settings.sortByPing = next == SortMode.PING
    }

    // ---- selection ----------------------------------------------------------

    private val _selectedId = MutableStateFlow<String?>(settings.lastProfileId)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    val profiles: StateFlow<List<Profile>> =
        combine(repo.profiles, _sortMode, _selectedId) { list, mode, selId ->
            val sorted =
                if (mode == SortMode.PING)
                    list.sortedBy { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs }
                else list
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

    fun select(id: String) { _selectedId.value = id; settings.lastProfileId = id }
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

    fun pingOne(profile: Profile) = viewModelScope.launch {
        val ms = repo.testLatency(profile)
        emit(if (ms >= 0) "${profile.remark}: ${ms} ms" else "${profile.remark}: unreachable")
    }

    /** Pick the lowest-latency server automatically. */
    fun selectFastest() = viewModelScope.launch {
        val ranked = profiles.value.filter { it.latencyMs > 0 }.minByOrNull { it.latencyMs }
        if (ranked != null) { select(ranked.id); emit("Selected ${ranked.remark}") }
        else emit("Run a latency test first")
    }

    private fun emit(text: String) { _message.value = UiMessage(text) }
}
