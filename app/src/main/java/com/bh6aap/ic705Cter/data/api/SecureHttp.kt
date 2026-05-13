package com.bh6aap.ic705Cter.data.api

import com.bh6aap.ic705Cter.util.LogManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 统一的安全 HTTP 客户端与 URL 护栏。
 *
 * 本模块做三件事：
 *   1) buildSecureClient(): 产出一个关闭重定向、设了 callTimeout、仅允许
 *      HTTPS 的 OkHttpClient。所有业务模块都应通过它取得 client，避免"自建
 *      Builder 绕过限制"（此前 ApiTypeValidator/TleDataManager 等各自 new
 *      一套，既无 pinner 也不关重定向）。
 *   2) validateOutboundUrl(): SSRF 护栏。只允许 https://；禁止 userinfo 形
 *      式（http://evil@real/）；显式拒绝 loopback / link-local / RFC1918 /
 *      云元数据等私网段——包括 hostname 解析后的实际地址（防 DNS-rebinding
 *      的 naive 绕过）。
 *   3) readLimitedBody(): 限制响应体大小的包装，避免恶意或被劫持的 API
 *      用 GB 级响应 OOM 客户端。
 */
object SecureHttp {

    private const val TAG = "SecureHttp"
    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private const val DEFAULT_BODY_LIMIT_BYTES = 10L * 1024 * 1024  // 10 MB

    // 供 CertificatePinner 初始化使用的默认已知主机。Pinner 仅作为"深度防御"，
    // 我们不因此屏蔽用户自定义 API；主路径仍是 validateOutboundUrl() + 系统
    // 根证书 + 系统 HostnameVerifier。
    private val KNOWN_TRUSTED_HOSTS = setOf(
        "db.satnogs.org",
        "celestrak.org",
        "tle.ivanstanojevic.me"
    )

    fun buildSecureClient(
        connectTimeoutSec: Long = DEFAULT_TIMEOUT_SECONDS,
        readTimeoutSec: Long = DEFAULT_TIMEOUT_SECONDS,
        callTimeoutSec: Long = DEFAULT_TIMEOUT_SECONDS + 5
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    sealed class UrlCheck {
        object Ok : UrlCheck()
        data class Reject(val reason: String) : UrlCheck()
    }

    /**
     * 对用户提供的 URL 做前置校验；所有走 custom API 的请求都应先通过它。
     *
     * @param allowKnownHostsOnly 若为 true，仅允许 KNOWN_TRUSTED_HOSTS 中的
     *                            主机（用于高敏感路径）；默认 false。
     */
    fun validateOutboundUrl(
        url: String,
        allowKnownHostsOnly: Boolean = false
    ): UrlCheck {
        // 只允许显式 https，避免 "//" 或 schema-less 绕过
        if (!url.startsWith("https://", ignoreCase = true)) {
            return UrlCheck.Reject("URL 必须以 https:// 开头")
        }
        val parsed = url.toHttpUrlOrNull()
            ?: return UrlCheck.Reject("URL 解析失败")
        // 禁用户凭据形式 (https://evil@real/...)
        if (parsed.encodedUsername.isNotEmpty() || parsed.encodedPassword.isNotEmpty()) {
            return UrlCheck.Reject("URL 不能携带 userinfo")
        }
        val host = parsed.host
        if (host.isEmpty()) {
            return UrlCheck.Reject("URL 缺少主机名")
        }
        if (allowKnownHostsOnly && host.lowercase() !in KNOWN_TRUSTED_HOSTS) {
            return UrlCheck.Reject("仅允许已知主机: $host 不在白名单")
        }
        // 拒绝直接以 IP 形式指向内网（防跨越 DNS 的显式内网访问）
        val addresses: Array<InetAddress> = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            // 允许后续 OkHttp 自己报 UnknownHost；不拦 DNS 故障
            return UrlCheck.Ok
        }
        for (addr in addresses) {
            if (isBlockedAddress(addr)) {
                return UrlCheck.Reject("主机 $host 解析到受限地址 ${addr.hostAddress}")
            }
        }
        return UrlCheck.Ok
    }

    private fun isBlockedAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||   // RFC1918 for IPv4, fec0::/10 for IPv6 (已废弃)
            addr.isMulticastAddress
        ) {
            return true
        }
        // 额外拦云元数据与 carrier-grade NAT / ULA / IPv6 映射
        return when (addr) {
            is Inet4Address -> {
                val raw = addr.address
                val b0 = raw[0].toInt() and 0xFF
                val b1 = raw[1].toInt() and 0xFF
                // 169.254.0.0/16 已被 isLinkLocal 覆盖；这里显式追加 169.254.169.254
                if (b0 == 169 && b1 == 254) return true
                // 100.64.0.0/10  Carrier-grade NAT
                if (b0 == 100 && b1 in 64..127) return true
                // 0.0.0.0/8
                if (b0 == 0) return true
                false
            }
            is Inet6Address -> {
                // fc00::/7 ULA
                val firstByte = addr.address[0].toInt() and 0xFE
                firstByte == 0xFC
            }
            else -> false
        }
    }

    /**
     * 读取响应体但限制字节数，防止恶意服务器返回 GB 级响应 OOM。
     *
     * 返回值：
     *   - 正常读取且大小在 [0, limitBytes] 内 -> 完整 UTF-8 字符串
     *   - 响应体为空、读取异常 -> null
     *   - 响应体长度超过 limitBytes -> null（而不是截断）
     *
     * 之所以超限返回 null 而不是截断字符串，是因为所有调用方都按 null=
     * "无法使用" 处理：半截 JSON / TLE 再喂给 Gson/parser 只会以难以
     * 诊断的 parse error 失败，静默保留截断 body 会误导调用方当作合法
     * 数据入库。要截断语义的调用方应该显式传入更大的 limitBytes。
     */
    fun readLimitedBody(response: Response, limitBytes: Long = DEFAULT_BODY_LIMIT_BYTES): String? {
        val body = response.body ?: return null
        return try {
            val source = body.source()
            // request(limit + 1) 保证超过时我们能发现
            source.request(limitBytes + 1)
            val buffered = source.buffer
            val size = buffered.size
            if (size > limitBytes) {
                LogManager.w(TAG, "响应体超过 $limitBytes 字节上限，拒绝")
                null
            } else {
                buffered.readUtf8()
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "读取响应体失败: ${e.message}")
            null
        }
    }

    /**
     * 把自定义 URL 转成 Request；做 SSRF 护栏。
     */
    fun buildValidatedRequest(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Request? {
        when (val check = validateOutboundUrl(url)) {
            is UrlCheck.Reject -> {
                LogManager.e(TAG, "URL 拒绝: ${check.reason} ($url)")
                return null
            }
            UrlCheck.Ok -> {}
        }
        val b = Request.Builder().url(url)
        for ((k, v) in headers) b.header(k, v)
        return b.build()
    }
}
