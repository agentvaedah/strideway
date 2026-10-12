package id.web.agentvaedah.strideway.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val StridewayJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

object HttpClientFactory {
    const val USER_AGENT = "Strideway/0.1 (id.web.agentvaedah.strideway)"

    fun create(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(StridewayJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
            requestTimeoutMillis = 120_000
        }
        defaultRequest { headers.append(HttpHeaders.UserAgent, USER_AGENT) }
    }
}
