package libv2ray

/**
 * DEMO-ONLY stub of AndroidLibXrayLite's `libv2ray` package (v38 CoreController API).
 *
 * Compiled ONLY into the `mock` product flavor so the app builds and the whole UI
 * runs without the native Xray-core engine. It does no networking. The `full`
 * flavor excludes this file and links the real libv2ray.aar, which gomobile
 * generates with these exact same names and signatures.
 */

interface CoreCallbackHandler {
    fun startup(): Long
    fun shutdown(): Long
    fun onEmitStatus(l: Long, s: String?): Long
}

class CoreController internal constructor() {
    var isRunning: Boolean = false
        private set

    fun startLoop(configContent: String?, tunFd: Int) { isRunning = true }
    fun stopLoop() { isRunning = false }
    fun measureDelay(url: String?): Long = -1
    fun queryStats(tag: String?, direct: String?): Long = 0
}

object Libv2ray {
    fun newCoreController(handler: CoreCallbackHandler): CoreController = CoreController()
    fun initCoreEnv(envPath: String?, key: String?) { /* no-op in demo */ }
    fun checkVersionX(): String = "mock"
}
