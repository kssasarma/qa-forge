package com.qaforge.domain.model;

import java.util.List;

public record GeneratedFilesSummary(
    List<String> playwright,
    List<String> restAssured,
    List<String> dbValidation
) {}
