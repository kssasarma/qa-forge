package com.qaforge.application.agent.support;

/** File- and identifier-naming helpers for generation agents, per PRD §14.2. */
public final class NamingUtil {

    private NamingUtil() {}

    /** {@code "User completes checkout"} → {@code "user_completes_checkout"}. */
    public static String snakeCase(String text) {
        String normalized = text.strip().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("^_+|_+$", "");
    }

    /** {@code "user completes checkout"} → {@code "UserCompletesCheckout"}. */
    public static String pascalCase(String text) {
        StringBuilder result = new StringBuilder();
        for (String word : text.strip().split("[^a-zA-Z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    /** {@code "User completes checkout"} → {@code "userCompletesCheckout"}. */
    public static String camelCase(String text) {
        String pascal = pascalCase(text);
        if (pascal.isEmpty()) {
            return pascal;
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }
}
