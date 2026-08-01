package com.qaforge.infrastructure.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.domain.port.out.OpenApiSpecPort;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Implements {@link OpenApiSpecPort} by fetching an OpenAPI (Swagger) JSON document — e.g.
 * Springdoc's {@code /v3/api-docs} — and locating the operation whose {@code operationId}
 * matches. Fetched specs are cached in-memory per URL for the lifetime of the JVM, since a
 * single analyze run may ask for several operations from the same spec.
 */
@Component
public class OpenApiSpecClient implements OpenApiSpecPort {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSpecClient.class);
    private static final String[] HTTP_METHODS = {"get", "post", "put", "patch", "delete", "options", "head"};

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> specCache = new ConcurrentHashMap<>();

    public OpenApiSpecClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Optional<String> fetchOperation(String openApiSpecUrl, String operationId) {
        JsonNode spec = specCache.computeIfAbsent(openApiSpecUrl, this::fetchSpec);
        if (spec == null || spec.isMissingNode()) {
            return Optional.empty();
        }

        JsonNode paths = spec.path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            for (String method : HTTP_METHODS) {
                JsonNode operation = pathEntry.getValue().path(method);
                if (!operation.isMissingNode() && operationId.equals(operation.path("operationId").asText(null))) {
                    return Optional.of(buildOperationSummary(method, pathEntry.getKey(), operation));
                }
            }
        }
        return Optional.empty();
    }

    private JsonNode fetchSpec(String url) {
        try {
            return restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Failed to fetch OpenAPI spec from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String buildOperationSummary(String method, String path, JsonNode operation) {
        try {
            return objectMapper.writeValueAsString(Map.of("method", method.toUpperCase(), "path", path, "operation", operation));
        } catch (Exception e) {
            return operation.toString();
        }
    }
}
