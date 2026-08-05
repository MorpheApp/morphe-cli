/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.Protocol
import java.net.Inet4Address

/**
 * Lazy initialized HttpClient for CLI commands. One client per process is fine for short-lived
 * `morhpe-cli ....` invocations. Engine remote sources (like GitHub and GitLab) require this to be passed in.
 *
 * We could later swap `by lazy` for `fun create()` if we ever want the CLI to share lifecycle with anything else.
 */
object CliHttpClient {
    val instance: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    // Prefer IPv4 so an advertised but unroutable IPv6 address does not
                    // burn the connect timeout before the working A record is tried.
                    dns { hostname ->
                        val all = Dns.SYSTEM.lookup(hostname)
                        all.filterIsInstance<Inet4Address>().ifEmpty { all }
                    }
                    // Pin HTTP/1.1 to avoid intermittent HTTP/2 stream resets against
                    // GitHub-backed download endpoints.
                    protocols(listOf(Protocol.HTTP_1_1))
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            // Idle/socket timeouts (not a total cap) so large .mpp downloads don't fail
            // for being big. Only genuine stalls or issues fail.
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
            // Retry/429 handling lives in HttpService (single layer), not a client plugin.
        }
    }
}
