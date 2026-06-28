package com.smoothvpn.ui.home

enum class ConnState { Disconnected, Connecting, Connected, Error }

data class HomeUiState(
    val state: ConnState = ConnState.Disconnected,
    val serverName: String = "Auto-select fastest",
    val serverGroup: String? = null,
    val pingMs: Long? = null,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    /** plain-language note from ConnectionDiagnostics when state == Error */
    val message: String? = null,
)
