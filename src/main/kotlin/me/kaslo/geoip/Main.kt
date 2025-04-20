package me.kaslo.geoip

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.maxmind.geoip2.DatabaseReader
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import org.slf4j.LoggerFactory

// Output data structure
data class GeoResult(
    val ip: String,
    val country: String?,
    val city: String?,
    val lat: Double?,
    val lon: Double?
)

// Error response structure
data class ErrorResponse(
    val error: String,
    val details: String? = null
)

// Config structure
data class AppConfig(
    // It says JSON but don't let that confuse you
    // It's still YAML
    @JsonProperty("api_key_hashes")
    val apiKeys: List<String> = emptyList(),

    @JsonProperty("admin_key_hash")
    val adminKey: String? = null,

    @JsonProperty("log_invalid_tokens")
    val logInvalidTokens: Boolean = false,

    // This is var for a reason, you'll see why when we start the server
    @JsonProperty("port")
    var port: Int = 8080
)

// Create our mapper and logger
val yamlMapper = ObjectMapper(YAMLFactory())
val logger = LoggerFactory.getLogger("GeoIP")

// Global for the config state
@Volatile
var appConfig = AppConfig()

// Loads config from a file
fun loadConfigFromYaml(file: File): AppConfig {
    return file.inputStream().use {
        yamlMapper.readValue(it)
    }
}

// Helper to hash the received API key
fun hashApiKey(key: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun main() {
    // Open our database
    val db = DatabaseReader.Builder(File("GeoLite2-City.mmdb")).build()

    // Set up our config
    val configFile = File("config.yaml")
    appConfig = loadConfigFromYaml(configFile)
    // For whatever reason, if the port isn't present in config.yaml, it defaults to 0 despite the default being defined
    // We need to fix that, otherwise we get a random port
    if (appConfig.port == 0) {
        appConfig.port = 8080
        logger.info("Port not defined, using port 8080")
    }

    embeddedServer(Netty, port = appConfig.port) {
        // Use Jackson for serialization
        install(ContentNegotiation) {
            jackson()
        }

        routing {
            get("/geo") {
                // Get Authorization header
                val authHeader = call.request.headers["Authorization"]

                // Check if it's a Bearer token
                val token = authHeader
                    ?.takeIf { it.startsWith("Bearer ") }
                    ?.removePrefix("Bearer ")
                    ?.trim()

                // If token is null or not allowed, deny access
                if (token == null || hashApiKey(token) !in appConfig.apiKeys) {
                    if (appConfig.logInvalidTokens) {
                        logger.warn("Bad token $token from ${call.request.origin.remoteHost}")
                    } else {
                        logger.warn("Bad token from ${call.request.origin.remoteHost}")
                    }
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Missing or invalid API token")
                    )
                    return@get
                }

                // Get IP to look up (from query param or client address)
                val ipParam = call.request.queryParameters["ip"] ?: call.request.origin.remoteHost
                // The DB wants a proper inet object, so convert it
                val inet = try {
                    InetAddress.getByName(ipParam)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            "Invalid IP address",
                            e.message
                        )
                    )
                    return@get
                }

                // Try to make the lookup
                val result = try {
                    db.city(inet)
                } catch (e: Exception) {
                    logger.error("Geo lookup failed!", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            "Geo lookup failed",
                            e.message
                        )
                    )
                    return@get
                }

                // Log all loookups
                logger.info("Lookup from ${call.request.origin.remoteHost} for IP $ipParam")
                call.respond(
                    GeoResult(
                        ip = ipParam,
                        country = result.country.name,
                        city = result.city.name,
                        lat = result.location.latitude,
                        lon = result.location.longitude
                    )
                )
            }

            post("/reload") {
                // Get auth header
                val authHeader = call.request.headers["Authorization"]

                // Check if it's a Bearer token
                val token = authHeader
                    ?.takeIf { it.startsWith("Bearer ") }
                    ?.removePrefix("Bearer ")
                    ?.trim()

                // If token is null or not allowed, deny access
                if (token == null || hashApiKey(token) != appConfig.adminKey) {
                    if (appConfig.logInvalidTokens) {
                        logger.warn("Bad admin token $token from ${call.request.origin.remoteHost}")
                    } else {
                        logger.warn("Bad admin token from ${call.request.origin.remoteHost}")
                    }
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Missing or invalid admin token")
                    )
                    return@post
                }

                // Try to reload config file
                try {
                    appConfig = loadConfigFromYaml(configFile)
                    logger.info("Reload request from ${call.request.origin.remoteHost}")
                    // Respond with number of keys loaded
                    call.respond(
                        mapOf("keys_loaded" to appConfig.apiKeys.size)
                    )
                } catch (e: Exception) {
                    logger.error("Failed to reload keys!", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            "Failed to reload keys",
                            e.message
                        )
                    )
                }
            }

            // All other routes 404
            route("{...}") {
                handle {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            "Route not found",
                            call.request.uri
                        )
                    )
                }
            }
        }
    }.start(wait = true)
}
