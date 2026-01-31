import com.github.prakashgavel.myaichatplugin.api.GeminiApiRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GeminiApiClient(private val baseUrl: String = "https://gemini.googleapis.com/v1") {

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateContent(apiKey: String, model: String, contents: String): String {
        val endpoint = "$baseUrl/models/generateContent"
        val payload = GeminiApiRoutes.GenerateContentRequest(model = model, contents = contents)
        val body = json.encodeToString(payload)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Gemini API error: $${response.statusCode()} $${response.body()}")
        }

        val respObj = json.decodeFromString<GeminiApiRoutes.GenerateContentResponse>(response.body())
        return respObj.text
    }
}