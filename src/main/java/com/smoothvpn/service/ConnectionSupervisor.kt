package com.smoothvpn.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Auto health-check + failover. This is SmoothVPN's signature feature: it keeps
 * the tunnel alive without the user touching anything. It periodically probes the
 * active server and, after repeated failures, silently switches to the fastest
 * healthy server in the same group.
 *
 * You implement the two interfaces against your existing engine (LatencyTester,
 * the active profile in XrayVpnService, and your ProfileRepository group lookup),
 * then start the supervisor once the tunnel is up.
 */
interface ServerProbe {
    /** Round-trip latency in ms, or null if the server is unreachable. */
    suspend fun ping(profileId: String): Long?
}

interface ActiveServerController {
    fun currentProfileId(): String?
    /** Other profiles in the active group, best-effort ordered. */
    fun candidateProfileIds(): List<String>
    /** Tear down the current outbound and bring up [profileId]. */
    suspend fun switchTo(profileId: String)
}

class ConnectionSupervisor(
    private val scope: CoroutineScope,
    private val probe: ServerProbe,
    private val controller: ActiveServerController,
    private val config: Config = Config(),
) {
    data class Config(
        val checkIntervalMs: Long = 15_000,
        val failuresBeforeSwitch: Int = 2,
        val probeTimeoutMs: Long = 4_000,
    )

    sealed interface State {
        data object Idle : State
        data class Healthy(val profileId: String, val latencyMs: Long) : State
        data class Degraded(val profileId: String, val consecutiveFailures: Int) : State
        data class SwitchedOver(val from: String?, val to: String, val reason: String) : State
        data object NoHealthyServer : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { run() }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        _state.value = State.Idle
    }

    private suspend fun run() {
        var failures = 0
        while (scope.isActive) {
            val current = controller.currentProfileId()
            if (current == null) { _state.value = State.Idle; delay(config.checkIntervalMs); continue }

            val latency = probe.ping(current)
            if (latency != null) {
                failures = 0
                _state.value = State.Healthy(current, latency)
            } else {
                failures++
                _state.value = State.Degraded(current, failures)
                if (failures >= config.failuresBeforeSwitch) {
                    if (failover(current)) failures = 0
                }
            }
            delay(config.checkIntervalMs)
        }
    }

    /** Returns true if a switch happened. */
    private suspend fun failover(failing: String): Boolean {
        val best = controller.candidateProfileIds()
            .filter { it != failing }
            .mapNotNull { id -> probe.ping(id)?.let { id to it } }
            .minByOrNull { it.second }

        if (best == null) { _state.value = State.NoHealthyServer; return false }

        controller.switchTo(best.first)
        _state.value = State.SwitchedOver(failing, best.first, "active server stopped responding")
        return true
    }
}
