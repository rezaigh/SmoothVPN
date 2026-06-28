package com.smoothvpn.service

/**
 * Adapters so ConnectionSupervisor plugs into your real engine without changing
 * its code. You pass your existing functions in as lambdas — no need to match
 * any specific method names.
 *
 * Example wiring in XrayVpnService once connected:
 *
 *   val probe = LatencyProbe { id -> latencyTester.ping(id) }      // your tester
 *   val controller = DefaultActiveServerController(
 *       currentId   = { activeProfile?.id },
 *       candidates  = { profileRepo.idsInGroup(activeGroupId) },
 *       switch      = { id -> restartOutboundWith(id) },           // your switch path
 *   )
 *   ConnectionSupervisor(serviceScope, probe, controller).start()
 */
class LatencyProbe(
    private val pingFn: suspend (String) -> Long?,
) : ServerProbe {
    override suspend fun ping(profileId: String): Long? = pingFn(profileId)
}

class DefaultActiveServerController(
    private val currentId: () -> String?,
    private val candidates: () -> List<String>,
    private val switch: suspend (String) -> Unit,
) : ActiveServerController {
    override fun currentProfileId(): String? = currentId()
    override fun candidateProfileIds(): List<String> = candidates()
    override suspend fun switchTo(profileId: String) = switch(profileId)
}
