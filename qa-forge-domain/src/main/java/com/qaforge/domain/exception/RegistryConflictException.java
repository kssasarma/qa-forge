package com.qaforge.domain.exception;

/** Thrown when a registry write conflicts with an existing record (e.g. duplicate file name). */
public class RegistryConflictException extends QaForgeException {

    public RegistryConflictException(String message) {
        super(message);
    }
}
