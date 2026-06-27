package com.smoothvpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smoothvpn.core.Network
import com.smoothvpn.core.Profile
import com.smoothvpn.core.Protocol
import com.smoothvpn.core.Security

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val remark: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String,
    val method: String,
    val alterId: Int,
    val encryption: String,
    val flow: String,
    val network: String,
    val security: String,
    val sni: String,
    val host: String,
    val path: String,
    val alpn: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val headerType: String,
    val subscriptionId: String?,
    val latencyMs: Int,
    val sortOrder: Int = 0
) {
    fun toProfile() = Profile(
        id, remark, Protocol.valueOf(protocol), address, port, userId, password,
        method, alterId, encryption, flow, Network.valueOf(network),
        Security.valueOf(security), sni, host, path, alpn, fingerprint,
        publicKey, shortId, spiderX, headerType, subscriptionId, latencyMs
    )

    companion object {
        fun from(p: Profile, sortOrder: Int = 0) = ProfileEntity(
            p.id, p.remark, p.protocol.name, p.address, p.port, p.userId, p.password,
            p.method, p.alterId, p.encryption, p.flow, p.network.name,
            p.security.name, p.sni, p.host, p.path, p.alpn, p.fingerprint,
            p.publicKey, p.shortId, p.spiderX, p.headerType, p.subscriptionId,
            p.latencyMs, sortOrder
        )
    }
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long = 0L,
    val enabled: Boolean = true
)
