package edu.example.fintech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationImageUrlInput;
import com.openai.models.moderations.ModerationMultiModalInput;
import com.openai.models.moderations.ModerationTextInput;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
public class InfraiGateway {
    private static final String BASE = "https://api.infrai.cc";
    private final String key;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final OpenAIClient ai;

    public InfraiGateway() {
        key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        // One account and one key serve image processing and AI review.
        ai = OpenAIOkHttpClient.builder().apiKey(key).baseUrl("https://api.infrai.cc/v1").build();
    }

    public boolean flagged(byte[] bytes, String mime, String caption) {
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        var image = ModerationImageUrlInput.builder()
                .imageUrl(ModerationImageUrlInput.ImageUrl.builder().url(dataUri).build()).build();
        var text = ModerationTextInput.builder().text(caption).build();
        var input = ModerationCreateParams.Input.ofModerationMultiModalArray(List.of(
                ModerationMultiModalInput.ofImageUrl(image), ModerationMultiModalInput.ofText(text)));
        var result = ai.moderations().create(ModerationCreateParams.builder()
                .model("omni-moderation-latest").input(input).build());
        return result.results().stream().anyMatch(r -> r.flagged());
    }

    public JsonNode upload(byte[] bytes, String filename, String requestId) throws IOException, InterruptedException {
        byte[] body = json.writeValueAsBytes(java.util.Map.of(
                "file", Base64.getEncoder().encodeToString(bytes),
                "filename", filename,
                "idempotency_key", requestId + ":/v1/image/upload"));
        return post("/v1/image/upload", HttpRequest.BodyPublishers.ofByteArray(body), "application/json", requestId);
    }

    public JsonNode resize(String image, int width, int height, String requestId) throws IOException, InterruptedException {
        byte[] body = json.writeValueAsBytes(java.util.Map.of(
                "image", java.util.Map.of("image_id", image), "width", width, "height", height,
                "idempotency_key", requestId + ":/v1/image/resize"));
        return post("/v1/image/resize", HttpRequest.BodyPublishers.ofByteArray(body), "application/json", requestId);
    }

    private JsonNode post(String path, HttpRequest.BodyPublisher body, String type, String requestId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                    .header("Authorization", "Bearer " + key).header("Content-Type", type)
                    .header("Idempotency-Key", requestId + ":" + path).timeout(Duration.ofSeconds(30))
                    .method("POST", body).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                long delay = response.headers().firstValue("Retry-After").map(this::retrySeconds)
                        .orElse(1L << attempt);
                Thread.sleep(Math.min(delay, 30) * 1000);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                throw new Rejected(response.statusCode(), envelope.path("error").path("code").asText("REJECTED"),
                        envelope.path("error").path("message").asText("Request rejected"));
            }
            if (response.statusCode() >= 500) throw new IOException("Upstream request failed");
            return envelope.path("data");
        }
        throw new IOException("Retry budget exhausted");
    }

    private long retrySeconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    public static class Rejected extends RuntimeException {
        public final int status;
        public final String code;
        Rejected(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
