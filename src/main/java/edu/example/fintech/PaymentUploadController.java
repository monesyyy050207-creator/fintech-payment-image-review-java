package edu.example.fintech;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class PaymentUploadController {
    private final InfraiGateway gateway;
    private final int width;
    private final int height;

    public PaymentUploadController(InfraiGateway gateway,
            @Value("${review.image-width}") int width, @Value("${review.image-height}") int height) {
        this.gateway = gateway;
        this.width = width;
        this.height = height;
    }

    public record ReviewReceipt(String eventId, String state, String imageId,
                                String notification, String action) {}

    @PostMapping(path = "/payment-events/uploads", consumes = "multipart/form-data")
    public ReviewReceipt review(@RequestHeader("Idempotency-Key") String eventId,
            @RequestParam("file") MultipartFile file, @RequestParam("caption") String caption)
            throws IOException, InterruptedException {
        if (eventId.isBlank() || file.isEmpty() || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Provide an event ID, an image and a caption");
        }
        byte[] bytes = file.getBytes();
        // The submitted bytes and caption are screened together before a publishable derivative exists.
        boolean flagged = gateway.flagged(bytes, file.getContentType(), caption);
        String state = ReviewDecision.state(flagged);
        if (flagged) return new ReviewReceipt(eventId, state, null, "REVIEW_REQUESTED", "HOLD");

        JsonNode uploaded = gateway.upload(bytes, file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(), eventId);
        String imageId = uploaded.path("image_id").asText();
        if (imageId.isBlank()) throw new IOException("Upload response has no image ID");
        JsonNode resized = gateway.resize(imageId, width, height, eventId);
        String outputId = resized.path("image_id").asText(imageId);
        return new ReviewReceipt(eventId, state, outputId, "REVIEW_APPROVED", "PUBLISH");
    }

    @ExceptionHandler(InfraiGateway.Rejected.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiGateway.Rejected error) {
        int status = error.status >= 400 && error.status < 500 ? error.status : 502;
        return ResponseEntity.status(status).body(Map.of("code", error.code, "message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", error.getMessage()));
    }
}
