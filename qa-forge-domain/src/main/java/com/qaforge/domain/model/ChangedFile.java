package com.qaforge.domain.model;

/** One file touched by a diff. {@code changeType} is one of ADDED, MODIFIED, DELETED, RENAMED. */
public record ChangedFile(
    String filePath,
    String changeType,
    int linesAdded,
    int linesDeleted,
    String patchText
) {}
