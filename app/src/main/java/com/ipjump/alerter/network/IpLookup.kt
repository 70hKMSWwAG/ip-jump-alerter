package com.ipjump.alerter.network

import com.google.gson.annotations.SerializedName
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class IpInfo(
    val ip: String,
    val country: String = "",
    val city: String = "",
    val region: String = ""
) {
    fun hasLocation(): Boolean {
        return city.isNotBlank() || region.isNotBlank() || country.isNotBlank()
    }

    fun locationLabel(): String {
        val parts = listOf(city, region, country).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "未知" else parts.joinToString(" · ")
    }
}

internal data class IpInfoIo(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null
)

internal data class IpApiCo(
    val ip: String? = null,
    val city: String? = null,
    @SerializedName("country_name") val countryName: String? = null,
    @SerializedName("region") val region: String? = null
)

internal interface PublicIpApi {
    @GET
    suspend fun getText(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun getIpInfo(@Url url: String): Response<IpInfoIo>

    @GET
    suspend fun getIpApi(@Url url: String): Response<IpApiCo>
}

object IpLookup {
    private const val COOLDOWN_MS = 10 * 60 * 1000L
    private const val IP_CACHE_TTL_MS = 20_000L
    private const val GEO_RETRY_MS = 30 * 60 * 1000L

    private val IPV4_REGEX =
        Regex("""^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$""")

    private val plainUrls = arrayOf(
        "https://api.ipify.org",
        "https://icanhazip.com",
        "https://ifconfig.me/ip"
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 2
        })
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "IpJumpAlerter/1.0")
                    .header("Accept", "application/json, text/plain")
                    .build()
            )
        }
        .build()

    private val api: PublicIpApi = Retrofit.Builder()
        .baseUrl("https://ipinfo.io/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PublicIpApi::class.java)

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cached: IpInfo? = null

    @Volatile
    private var cachedAt = 0L

    @Volatile
    private var lastGeoAt = 0L

    @Volatile
    private var lastGeoIp = ""

    suspend fun fetch(previousIp: String = "", refreshLocation: Boolean = false): IpInfo? {
        val lookedUp = lookupIp() ?: return null
        remember(lookedUp)
        if (lookedUp.hasLocation()) return lookedUp
        cached?.takeIf { it.ip == lookedUp.ip && it.hasLocation() }?.let { return it }
        val sameIp = previousIp.isNotBlank() && lookedUp.ip == previousIp
        val recentlyTriedGeo = lastGeoIp == lookedUp.ip &&
            System.currentTimeMillis() - lastGeoAt < GEO_RETRY_MS
        if (sameIp && (!refreshLocation || recentlyTriedGeo)) {
            return cached?.takeIf { it.ip == lookedUp.ip } ?: lookedUp
        }
        lastGeoIp = lookedUp.ip
        lastGeoAt = System.currentTimeMillis()
        val geo = lookupGeo()
        val info = when {
            geo != null && geo.ip == lookedUp.ip -> geo
            geo != null && looksLikeIp(geo.ip) -> geo
            else -> lookedUp
        }
        remember(info)
        return info
    }

    private suspend fun lookupIp(): IpInfo? {
        for (url in plainUrls) {
            fetchPlain(url)?.let {
                promote(url)
                return IpInfo(it)
            }
        }
        fetchIpInfoIo()?.let { return it }
        fetchIpApiCo()?.let { return it }
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAt < IP_CACHE_TTL_MS) {
            return fresh
        }
        return null
    }

    private fun remember(info: IpInfo) {
        val current = cached
        cached = if (current != null && current.ip == info.ip && !info.hasLocation() && current.hasLocation()) {
            current
        } else {
            info
        }
        cachedAt = System.currentTimeMillis()
    }

    private suspend fun lookupGeo(): IpInfo? {
        fetchIpInfoIo()?.let { return it }
        fetchIpApiCo()?.let { return it }
        return null
    }

    private suspend fun fetchIpInfoIo(): IpInfo? {
        val url = "https://ipinfo.io/json"
        return call(url) {
            val res = api.getIpInfo(url)
            if (!res.isSuccessful) {
                res.errorBody()?.close()
                return@call null
            }
            val body = res.body() ?: return@call null
            val ip = body.ip?.trim().orEmpty()
            if (!looksLikeIp(ip)) null else IpInfo(
                ip = ip,
                country = body.country.orEmpty(),
                city = body.city.orEmpty(),
                region = body.region.orEmpty()
            )
        }
    }

    private suspend fun fetchIpApiCo(): IpInfo? {
        val url = "https://ipapi.co/json/"
        return call(url) {
            val res = api.getIpApi(url)
            if (!res.isSuccessful) {
                res.errorBody()?.close()
                return@call null
            }
            val body = res.body() ?: return@call null
            val ip = body.ip?.trim().orEmpty()
            if (!looksLikeIp(ip)) null else IpInfo(
                ip = ip,
                country = body.countryName.orEmpty(),
                city = body.city.orEmpty(),
                region = body.region.orEmpty()
            )
        }
    }

    private suspend fun fetchPlain(url: String): String? {
        return call(url) {
            val res = api.getText(url)
            res.body().use { body ->
                if (!res.isSuccessful) {
                    res.errorBody()?.close()
                    return@call null
                }
                val text = body?.string()?.trim().orEmpty()
                if (looksLikeIp(text)) text else null
            }
        }
    }

    private suspend fun <T> call(url: String, block: suspend () -> T?): T? {
        if (isCooling(url)) return null
        val result = runCatching { block() }.getOrNull()
        if (result == null) {
            markCooling(url)
        } else {
            cooldownUntil.remove(hostOf(url))
        }
        return result
    }

    private fun isCooling(url: String): Boolean {
        val until = cooldownUntil[hostOf(url)] ?: return false
        if (until > System.currentTimeMillis()) return true
        cooldownUntil.remove(hostOf(url))
        return false
    }

    private fun markCooling(url: String) {
        cooldownUntil[hostOf(url)] = System.currentTimeMillis() + COOLDOWN_MS
    }

    @Synchronized
    private fun promote(url: String) {
        val index = plainUrls.indexOf(url)
        if (index <= 0) return
        val hit = plainUrls[index]
        for (i in index downTo 1) {
            plainUrls[i] = plainUrls[i - 1]
        }
        plainUrls[0] = hit
    }

    private fun hostOf(url: String): String {
        return url.toHttpUrlOrNull()?.host ?: url
    }

    fun looksLikeIp(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty() || v.length > 45) return false
        if (IPV4_REGEX.matches(v)) return true
        return looksLikeIpv6(v)
    }

    private fun looksLikeIpv6(value: String): Boolean {
        val v = value.substringBefore('%')
        if (!v.contains(':') || v.contains(":::")) return false
        val doubleColon = v.contains("::")
        if (doubleColon && v.indexOf("::") != v.lastIndexOf("::")) return false
        val sides = if (doubleColon) v.split("::", limit = 2) else listOf(v)
        val groups = sides.flatMap { side ->
            if (side.isEmpty()) emptyList() else side.split(':')
        }
        if (groups.size > 8) return false
        if (!doubleColon && groups.size != 8 && (groups.size != 7 || groups.lastOrNull()?.contains('.') != true)) {
            return false
        }
        var ipv4Tail = false
        for ((index, group) in groups.withIndex()) {
            if (group.contains('.')) {
                if (index != groups.lastIndex) return false
                if (!IPV4_REGEX.matches(group)) return false
                ipv4Tail = true
            } else {
                if (group.isEmpty() || group.length > 4) return false
                if (group.any { !it.isDigit() && it !in 'a'..'f' && it !in 'A'..'F' }) return false
            }
        }
        val effective = groups.size + if (ipv4Tail) 1 else 0
        return if (doubleColon) effective <= 8 else effective == 8 || (ipv4Tail && groups.size == 7)
    }
}
