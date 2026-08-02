package com.qaforge.domain.model;

import java.util.List;

/** Output of any generation agent (Playwright, RestAssured, DB validation). */
public record GeneratedTest(
    String scenarioId,
    String fileName,
    String content,
    TestLayer layer,
    List<String> tags
) {}
