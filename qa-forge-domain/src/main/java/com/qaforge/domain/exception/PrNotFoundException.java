package com.qaforge.domain.exception;

/** Thrown when a PR/MR does not exist. Maps to HTTP 422 PR_NOT_FOUND. */
public class PrNotFoundException extends QaForgeException {

    public PrNotFoundException(String repositoryFullName, String prNumber) {
        super("PR/MR " + prNumber + " not found in " + repositoryFullName);
    }
}
