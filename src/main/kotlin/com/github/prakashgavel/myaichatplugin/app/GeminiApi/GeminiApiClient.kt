// kotlin
import com.github.prakashgavel.myaichatplugin.api.GeminiApiRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GeminiApiClient(
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val apiVersion: String = "v1beta" // or "v1beta2"
) {
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateContent(apiKey: String, model: String, contents: String): String {
        // Endpoint: /v{version}/models/{model}:generateContent?key=API_KEY
        val endpoint = "$baseUrl/$apiVersion/models/$model:generateContent?key=$apiKey"

        val payload = GeminiApiRoutes.GenerateContentRequest(
            contents = listOf(
                GeminiApiRoutes.Content(
                    role = "user",
                    parts = listOf(GeminiApiRoutes.Part(text = contents))
                )
            )
        )
        val body = json.encodeToString(payload)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Gemini API error: ${response.statusCode()} ${response.body()}")
        }

        val respObj = json.decodeFromString<GeminiApiRoutes.GenerateContentResponse>(response.body())

        // Extract first candidate text
        val text = respObj.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: ""
        return text
    }
}