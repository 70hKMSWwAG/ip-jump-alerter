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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
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
            if (!res.isSuccessful) return null
            val body = res.body() ?: return null
            val ip = body.ip?.trim().orEmpty()
            if (ip.isEmpty()) null else IpInfo(
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
            if (!res.isSuccessful) return null
            val body = res.body() ?: return null
            val ip = body.ip?.trim().orEmpty()
            if (ip.isEmpty()) null else IpInfo(
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
            if (!res.isSuccessful) return null
            val text = res.body()?.string()?.trim().orEmpty()
            if (looksLikeIp(text)) text else null
        }.getOrNull()
    }

    fun looksLikeIp(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty() || v.length > 45) return false
        val ipv4 = Regex("""^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$""")
        if (ipv4.matches(v)) return true
        return v.contains(":") && v.all { it.isLetterOrDigit() || it == ':' || it == '.' }
    }
}
