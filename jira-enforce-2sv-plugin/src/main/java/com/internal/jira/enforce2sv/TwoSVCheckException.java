package com.internal.jira.enforce2sv;

/**
 * Сигнализирует, что статус 2SV не удалось проверить (например, БД недоступна во
 * время failover Patroni). Вызывающая сторона обязана fail-open (NFR-5).
 */
public class TwoSVCheckException extends Exception {

    public TwoSVCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
