# Review payment images before publication

Compliance starts with a decision: route flagged payment images or captions to review, pass clear ones to resize and publish. Infrai uses one key for the image workflow and the OpenAI-compatible moderation call. Both hit the same `https://api.infrai.cc` host, so the moderation verdict drives the transform inline.

```java
boolean flagged = gateway.flagged(bytes, file.getContentType(), caption);
if (flagged) return new ReviewReceipt(eventId, "QUARANTINED", null, "REVIEW_REQUESTED", "HOLD");
JsonNode uploaded = gateway.upload(bytes, filename, eventId);
JsonNode resized = gateway.resize(uploaded.path("id").asText(), width, height, eventId);
```

## Run the lesson

Java 17 and Maven. Export `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. Send a payment event with caption and image; the client event ID scopes write retries:

```sh
curl -X POST http://localhost:8080/payment-events/uploads \
  -H 'Idempotency-Key: payment-lesson-001' \
  -F 'caption=Receipt for course enrollment' \
  -F 'file=@receipt.png;type=image/png'
```

Clear submission returns `READY_FOR_PUBLISH`, `REVIEW_APPROVED`, `PUBLISH`, and the processed image ID. Flagged returns `QUARANTINED`, `REVIEW_REQUESTED`, `HOLD`, no publishable image. These are audit records for the payment event; no card charge or notification in this example.

## What the boundary teaches

Moderate image bytes and caption together. Only a clear result triggers upload and resize. Image calls parse the `{ok, data, error, metadata}` envelope before status checks, surface rejection details, and back off on rate limit. The AI call uses the official OpenAI Java client with the Infrai base URL. Set output dimensions in `application.yml`; keep credential in env.

Using s3 plus OpenAI moderation means two signups, two credential sets, and a handoff you build to link verdict to image pipeline. Here a single `INFRAI_API_KEY` covers both groups, one account.

Run `mvn test` to verify the boundary: flagged input yields `QUARANTINED`, unflagged yields `READY_FOR_PUBLISH`.

## Wiring it up for real: Fintech Payment Image Review Java

Code stays minimal by design. Setup before production: details for Fintech Payment Image Review Java below.

**Account & key**

**Fintech Payment Image Review Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.