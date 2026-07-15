package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.ofbiz.OfBizConnectionFactory;
import com.atlassian.jira.user.ApplicationUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проверяет наличие записи о регистрации нативной 2SV у пользователя.
 *
 * Использует {@link OfBizConnectionFactory} через {@code ComponentAccessor.getComponent(...)} —
 * доступ к JDBC-пулу приложения (NFR-3), без отдельного DataSource. OfBizConnectionFactory —
 * внутренний компонент jira-core (PICO-контейнер), а не OSGi-сервис, поэтому
 * getOSGiComponentInstanceOfType для него возвращает null, а getComponent — работает.
 *
 * Имя таблицы и колонки подтверждены (Этап 0 ТЗ, 14.07.2026): и на проде, и на локальном
 * инстансе Jira 10.6.1. При патче/апгрейде Jira перепроверить скриптом
 * scripts/diagnose-2sv-table.sql (см. также раздел рисков в ТЗ).
 */
public class TwoSVStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(TwoSVStatusChecker.class);

    private static final String TABLE = "AO_ED669C_TOTP_USER_ENROLLMENT";
    private static final String USER_KEY_COLUMN = "USER_KEY";

    // Идентификаторы в двойных кавычках обязательны: Active Objects создаёт таблицы/колонки
    // как quoted UPPERCASE, без кавычек PostgreSQL свернёт имя в нижний регистр и не найдёт
    // таблицу ("relation ao_ed669c_... does not exist"). Кавычки валидны и для других СУБД
    // в ANSI-режиме, но при смене СУБД (MS SQL/Oracle/MySQL) перепроверить отдельно.
    private static final String SQL =
            "SELECT COUNT(*) FROM \"" + TABLE + "\" WHERE \"" + USER_KEY_COLUMN + "\" = ?";

    /**
     * @throws TwoSVCheckException если проверку не удалось выполнить (недоступна БД, недоступен
     *         пул соединений, неожиданная ошибка) — вызывающая сторона обязана fail-open (NFR-5).
     *         Ловится Exception, а не только SQLException: любая ошибка проверки не должна
     *         блокировать пользователей.
     */
    public boolean isEnrolled(ApplicationUser user) throws TwoSVCheckException {
        String userKey = user.getKey();
        try {
            OfBizConnectionFactory connectionFactory = ComponentAccessor.getComponent(OfBizConnectionFactory.class);
            try (Connection connection = connectionFactory.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SQL)) {
                statement.setString(1, userKey);
                try (ResultSet rs = statement.executeQuery()) {
                    boolean enrolled = rs.next() && rs.getInt(1) > 0;
                    log.debug("2SV enrollment check for user '{}': {}", userKey, enrolled);
                    return enrolled;
                }
            }
        } catch (Exception e) {
            throw new TwoSVCheckException("Не удалось проверить статус 2SV для пользователя " + userKey, e);
        }
    }

    /**
     * Self-check при инициализации плагина (риск из ТЗ п.9: имя/структура AO-таблицы может
     * измениться при патче Jira). Проверяет доступность таблицы и пишет результат в лог:
     * INFO при успехе, ERROR — если таблица недоступна (фильтр при этом продолжит работать
     * в режиме fail-open, но админ увидит проблему сразу, а не по жалобам пользователей).
     */
    public void selfCheck() {
        try {
            long enrolled = countEnrolled();
            log.info("[{}] Self-check OK: таблица \"{}\" доступна, записей о 2SV-регистрации: {}",
                    Enforce2SVFilter.LOG_MARKER, TABLE, enrolled);
        } catch (Exception e) {
            log.error("[{}] Self-check FAILED: таблица \"{}\" недоступна — проверка 2SV будет работать "
                            + "в режиме fail-open (все пользователи пропускаются). Вероятно, схема БД изменилась "
                            + "после патча Jira — сверьте scripts/diagnose-2sv-table.sql. Причина: {}",
                    Enforce2SVFilter.LOG_MARKER, TABLE, e.getMessage(), e);
        }
    }

    /** Количество пользователей с настроенной 2SV. */
    public long countEnrolled() throws TwoSVCheckException {
        String sql = "SELECT COUNT(*) FROM \"" + TABLE + "\"";
        try {
            OfBizConnectionFactory connectionFactory = ComponentAccessor.getComponent(OfBizConnectionFactory.class);
            try (Connection connection = connectionFactory.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new TwoSVCheckException("Не удалось посчитать количество 2SV-регистраций", e);
        }
    }

    /** Количество активных пользователей Jira (по всем директориям). */
    public long countActiveUsers() throws TwoSVCheckException {
        String sql = "SELECT COUNT(*) FROM cwd_user WHERE active = 1";
        try {
            OfBizConnectionFactory connectionFactory = ComponentAccessor.getComponent(OfBizConnectionFactory.class);
            try (Connection connection = connectionFactory.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new TwoSVCheckException("Не удалось посчитать количество активных пользователей", e);
        }
    }

    /**
     * Активные пользователи без настроенной 2SV — для отчётности rollout (рассылки, отчёт ИБ).
     * Маппинг username → user key идёт через app_user (user key вида JIRAUSERxxxxx хранится там).
     *
     * @param limit максимальное число возвращаемых записей (защита от огромного ответа)
     */
    public List<Map<String, String>> listNotEnrolledActiveUsers(int limit) throws TwoSVCheckException {
        String sql = "SELECT cu.user_name, cu.display_name"
                + " FROM cwd_user cu"
                + " JOIN app_user au ON au.lower_user_name = cu.lower_user_name"
                + " WHERE cu.active = 1"
                + "   AND NOT EXISTS (SELECT 1 FROM \"" + TABLE + "\" e WHERE e.\"" + USER_KEY_COLUMN + "\" = au.user_key)"
                + " ORDER BY cu.user_name"
                + " LIMIT ?";
        try {
            OfBizConnectionFactory connectionFactory = ComponentAccessor.getComponent(OfBizConnectionFactory.class);
            try (Connection connection = connectionFactory.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    List<Map<String, String>> users = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, String> user = new LinkedHashMap<>();
                        user.put("username", rs.getString("user_name"));
                        user.put("displayName", rs.getString("display_name"));
                        users.add(user);
                    }
                    return users;
                }
            }
        } catch (Exception e) {
            throw new TwoSVCheckException("Не удалось получить список пользователей без 2SV", e);
        }
    }
}
