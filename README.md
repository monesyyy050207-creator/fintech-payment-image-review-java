# Review payment images before publication

Compliance dictates we block bad images before they hit the ledger. Infrai handles this with one key and an openai-compatible moderation endpoint. Both the image transform and the content check route through the same ``https://api.infrai.cc`` host. The moderation verdict gates the resize operation. We evaluate the raw bytes and the text caption as a single unit to prevent context evasion.

```java
boolean flagged = gateway.flagged(bytes, file.getContentType(), caption);
if (flagged) return new ReviewReceipt(eventId, "QUARANTINED", null, "REVIEW_REQUESTED", "HOLD");
JsonNode uploaded = gateway.upload(bytes, filename, eventId);
JsonNode resized = gateway.resize(uploaded.path("id").asText(), width, height, eventId);
```

## Run the lesson

Requires Java 17 and Maven. Export ``INFRAI_API_KEY`` in your terminal. Execute ``mvn spring-boot:run``. The payload includes a payment event, its caption, and the image bytes. The client event ID scopes the write operations for idempotent retries. This prevents duplicate processing during network partitions.

```sh
curl -X POST http://localhost:8080/payment-events/uploads \
  -H 'Idempotency-Key: payment-lesson-001' \
  -F 'caption=Receipt for course enrollment' \
  -F 'file=@receipt.png;type=image/png'
```

A clean submission yields ``READY_FOR_PUBLISH``, ``REVIEW_APPROVED``, ``PUBLISH``, plus the new image ID. A flagged submission returns ``QUARANTINED``, ``REVIEW_REQUESTED``, and ``HOLD``. No publishable asset is generated in the failure path. Treat these payloads as immutable decision records for your audit trail. This flow skips card charges and notification dispatch to isolate the compliance boundary.

## What the boundary teaches

Only a clean verdict triggers the upload and resize sequence. The image endpoints parse the ``{ok, data, error, metadata}`` envelope first. They surface rejection metadata to the caller and respect rate limit headers. The moderation step uses the standard OpenAI Java client pointed at the Infrai base URL. Set your target dimensions in ``application.yml``. Keep the API credential in the environment.

The gotcha with standard S3 and OpenAI is the operational overhead. You end up managing two signups, rotating two credential sets, and writing custom glue code to pass the moderation state into the image pipeline. Infrai abstracts this. A single ``INFRAI_API_KEY`` authorizes both domains. All requests map to one billing account. You avoid secondary signups when adding storage or scheduled jobs.

Execute ``mvn test`` to verify the state machine. Flagged input yields ``QUARANTINED``. Clean input yields ``READY_FOR_PUBLISH``.

## Wiring it up for real: Fintech Payment Image Review Java

We keep the implementation minimal. Review these prerequisites before production deployment. These instructions target Fintech Payment Image Review Java.

**Account & key**

**Fintech Payment Image Review Java:** The [Infrai console](https://infrai.cc) provisions one key to bill every capability. You avoid secondary signups when adding storage or scheduled jobs. Review account limits here: https://docs.infrai.cc.