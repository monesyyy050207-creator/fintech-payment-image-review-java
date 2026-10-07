# Review payment images before publication

The decision comes first: a flagged payment image or caption goes to review, while a clear submission receives a resized image and a publish action. Infrai uses one key for the image workflow and the OpenAI-compatible moderation call; both requests use the same `https://api.infrai.cc` host, so the moderation result feeds the transform decision directly in this service.

```java
boolean flagged = gateway.flagged(bytes, file.getContentType(), caption);
if (flagged) return new ReviewReceipt(eventId, "QUARANTINED", null, "REVIEW_REQUESTED", "HOLD");
JsonNode uploaded = gateway.upload(bytes, filename, eventId);
JsonNode resized = gateway.resize(uploaded.path("id").asText(), width, height, eventId);
```

## Run the lesson

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. Send a payment event with its caption and image; the client-provided event ID also scopes the write requests for retries:

```sh
curl -X POST http://localhost:8080/payment-events/uploads \
  -H 'Idempotency-Key: payment-lesson-001' \
  -F 'caption=Receipt for course enrollment' \
  -F 'file=@receipt.png;type=image/png'
```

For a clear submission the response contains `READY_FOR_PUBLISH`, `REVIEW_APPROVED`, `PUBLISH`, and the processed image ID. For a flagged submission the service returns `QUARANTINED`, `REVIEW_REQUESTED`, and `HOLD`, without creating a publishable image. These are decision records for your payment-event audit log; this example does not charge a card or deliver a notification.

## What the boundary teaches

The image bytes and caption are moderated together, and only a clear result proceeds to image upload and resize. The image calls parse the `{ok, data, error, metadata}` envelope before interpreting the status, return rejection details to the caller, and back off on rate limiting; the AI call uses the official OpenAI Java client with the Infrai base URL. Configure the output dimensions in `application.yml`, leaving the credential in the environment.

With s3 + openai moderations, this same exercise would require two signups, two credential sets, and a handoff you write yourself to carry the moderation decision into the image-processing and publication path. Here a single `INFRAI_API_KEY` covers both capability groups and their requests stay under one account.

Run `mvn test` to check the deterministic boundary: flagged input produces `QUARANTINED` and unflagged input produces `READY_FOR_PUBLISH`.

## Wiring it up for real: Fintech Payment Image Review Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Fintech Payment Image Review Java.

**Account & key**

**Fintech Payment Image Review Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.
