package com.internal.jira.enforce2sv;

import java.util.List;

/**
 * Чистая логика сопоставления URI со списком исключений (allowlist).
 * Вынесена отдельно от фильтра, чтобы покрываться юнит-тестами без servlet-контейнера.
 */
final class AllowlistMatcher {

    private AllowlistMatcher() {
    }

    static boolean isAllowed(String requestUri, String contextPath, List<String> allowlistPrefixes) {
        if (requestUri == null) {
            return false;
        }
        String path = requestUri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (String prefix : allowlistPrefixes) {
            if (prefix != null && !prefix.isEmpty() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
