package com.smoothvpn.work

/**
 * Adapter for the subscription auto-update worker. Wire it once at app start
 * (e.g. in your Application.onCreate):
 *
 *   SubscriptionRefresherProvider.instance = DefaultSubscriptionRefresher {
 *       subscriptionUpdater.refreshAll()   // your existing refresh, returns true on success
 *   }
 *   SubscriptionUpdateScheduler.schedule(this, everyHours = 12)
 */
class DefaultSubscriptionRefresher(
    private val refresh: suspend () -> Boolean,
) : SubscriptionRefresher {
    override suspend fun refreshAll(): Boolean = refresh()
}
