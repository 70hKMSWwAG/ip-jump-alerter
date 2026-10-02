package com.ipjump.alerter.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

data class IpInfo(
    val ip: String,
    val country: String = "",
    val city: String = "",
    val region: String = ""
) {
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
    private val IPV4_REGEX =
        Regex("""^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$""")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
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

    suspend fun fetch(): IpInfo? {
        fetchIpInfoIo()?.let { return it }
        fetchIpApiCo()?.let { return it }
        fetchPlain("https://ifconfig.me/ip")?.let { return IpInfo(it) }
        fetchPlain("https://api.ipify.org")?.let { return IpInfo(it) }
        return null
    }

    private suspend fun fetchIpInfoIo(): IpInfo? {
        return runCatching {
            val res = api.getIpInfo("https://ipinfo.io/json")
            if (!res.isSuccessful) {
                res.errorBody()?.close()
                return null
            }
            val body = res.body() ?: return null
            val ip = body.ip?.trim().orEmpty()
            if (!looksLikeIp(ip)) null else IpInfo(
                ip = ip,
                country = body.country.orEmpty(),
                city = body.city.orEmpty(),
                region = body.region.orEmpty()
            )
        }.getOrNull()
    }

    private suspend fun fetchIpApiCo(): IpInfo? {
        return runCatching {
            val res = api.getIpApi("https://ipapi.co/json/")
            if (!res.isSuccessful) {
                res.errorBody()?.close()
                return null
            }
            val body = res.body() ?: return null
            val ip = body.ip?.trim().orEmpty()
            if (!looksLikeIp(ip)) null else IpInfo(
                ip = ip,
                country = body.countryName.orEmpty(),
                city = body.city.orEmpty(),
                region = body.region.orEmpty()
            )
        }.getOrNull()
    }

    private suspend fun fetchPlain(url: String): String? {
        return runCatching {
            val res = api.getText(url)
            res.body().use { body ->
                if (!res.isSuccessful) {
                    res.errorBody()?.close()
                    return null
                }
                val text = body?.string()?.trim().orEmpty()
                if (looksLikeIp(text)) text else null
            }
        }.getOrNull()
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
