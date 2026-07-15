package com.internal.jira.enforce2sv;

import javax.servlet.http.HttpSession;

/**
 * Кеш результата проверки 2SV-статуса в рамках HttpSession с TTL (NFR-2) —
 * чтобы не ходить в БД на каждый запрос одного и того же пользователя.
 */
final class TwoSVSessionCache {

    static final String ATTR_ENROLLED = "enforce2sv.enrolled";
    static final String ATTR_CHECKED_AT = "enforce2sv.checkedAt";

    private TwoSVSessionCache() {
    }

    /**
     * @return закешированный результат, если он ещё не протух по TTL, иначе null
     */
    static Boolean getIfValid(HttpSession session, long ttlMillis, long now) {
        Object checkedAt = session.getAttribute(ATTR_CHECKED_AT);
        Object enrolled = session.getAttribute(ATTR_ENROLLED);
        if (checkedAt instanceof Long && enrolled instanceof Boolean) {
            long age = now - (Long) checkedAt;
            if (age >= 0 && age < ttlMillis) {
                return (Boolean) enrolled;
            }
        }
        return null;
    }

    static void put(HttpSession session, boolean enrolled, long now) {
        session.setAttribute(ATTR_ENROLLED, enrolled);
        session.setAttribute(ATTR_CHECKED_AT, now);
    }
}
