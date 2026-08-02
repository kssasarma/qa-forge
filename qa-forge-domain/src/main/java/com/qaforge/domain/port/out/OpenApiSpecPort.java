package com.qaforge.domain.port.out;

import java.util.Optional;

/**
 * Resolves a single OpenAPI operation from a spec URL, for {@code RestAssuredGenerationAgent}
 * (PRD §9.2.6 requires "the relevant OpenAPI operation JSON for openApiOperationId").
 *
 * <p>Documented extension beyond PRD §9.1.2's literal port list, for the same reason as
 * {@code TestRegistryPort}'s run-history methods: the behavior is required by an agent named
 * in the spec, and belongs behind a port rather than as a direct infrastructure dependency in
 * the application layer.
 */
public interface OpenApiSpecPort {
    Optional<String> fetchOperation(String openApiSpecUrl, String operationId);
}
