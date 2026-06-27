package com.smoothvpn.data

import android.content.Context
import com.smoothvpn.core.Profile
import com.smoothvpn.core.SubscriptionParser
import com.smoothvpn.net.LatencyTester
import com.smoothvpn.net.SubscriptionUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ProfileRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val profileDao = db.profileDao()
    private val subDao = db.subscriptionDao()

    val profiles: Flow<List<Profile>> =
        profileDao.observeAll().map { list -> list.map { it.toProfile() } }

    val subscriptions: Flow<List<SubscriptionEntity>> = subDao.observeAll()

    // ---- profiles -----------------------------------------------------------

    suspend fun addProfile(p: Profile) = withContext(Dispatchers.IO) {
        profileDao.upsert(ProfileEntity.from(p))
    }

    suspend fun addProfilesFromText(text: String): Int = withContext(Dispatchers.IO) {
        val parsed = com.smoothvpn.core.OutboundParser.parseMany(text)
        profileDao.upsertAll(parsed.map { ProfileEntity.from(it) })
        parsed.size
    }

    suspend fun deleteProfile(p: Profile) = withContext(Dispatchers.IO) {
        profileDao.delete(ProfileEntity.from(p))
    }

    suspend fun getProfile(id: String): Profile? = withContext(Dispatchers.IO) {
        profileDao.get(id)?.toProfile()
    }

    // ---- subscriptions ------------------------------------------------------

    suspend fun addSubscription(name: String, url: String): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            subDao.upsert(SubscriptionEntity(id = id, name = name, url = url))
            id
        }

    suspend fun deleteSubscription(sub: SubscriptionEntity) = withContext(Dispatchers.IO) {
        profileDao.deleteBySubscription(sub.id)
        subDao.delete(sub)
    }

    /** Re-fetch every enabled subscription and replace its servers. */
    suspend fun updateAllSubscriptions(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var total = 0
            for (sub in subDao.getEnabled()) {
                val body = SubscriptionUpdater.fetch(sub.url)
                val profiles = SubscriptionParser.parse(body, sub.id)
                profileDao.deleteBySubscription(sub.id)
                profileDao.upsertAll(profiles.map { ProfileEntity.from(it) })
                subDao.touch(sub.id, System.currentTimeMillis())
                total += profiles.size
            }
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- latency ------------------------------------------------------------

    /** TCP-connect ping to a server; stores the result. */
    suspend fun testLatency(p: Profile): Int = withContext(Dispatchers.IO) {
        val ms = LatencyTester.tcpPing(p.address, p.port)
        profileDao.updateLatency(p.id, ms)
        ms
    }

    suspend fun testAllLatency(list: List<Profile>) = withContext(Dispatchers.IO) {
        list.forEach { testLatency(it) }
    }
}
