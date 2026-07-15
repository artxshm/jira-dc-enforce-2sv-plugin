# Enforce 2SV for Jira Data Center

Плагин принудительного включения нативной двухэтапной проверки (2SV/2FA) для **Jira Data Center 10.x** — для версий, где ещё нет встроенного флага `2sv.enforcement.all.users.enabled` (он появился только в Jira 11.2).

A servlet-filter plugin that **enforces native two-step verification (2SV/2FA)** for all users on Jira Data Center 10.x, where the built-in enforcement flag does not exist yet (it only shipped in Jira 11.2).

## Как это работает / How it works

Servlet-фильтр (`before-dispatch`) перехватывает запросы аутентифицированных пользователей и проверяет наличие 2SV-регистрации прямым SQL-запросом к AO-таблице нативного `atlassian-authentication-plugin` (`AO_ED669C_TOTP_USER_ENROLLMENT`) через JDBC-пул самого приложения. Пользователи без настроенной 2FA перенаправляются на страницу-инструкцию (рус/англ по локали) и дальше — на вкладку «Двухэтапная проверка» профиля.

## Возможности / Features

- **Dry-run режим** (по умолчанию): только логирование, без блокировки — безопасный пилот
- **Fail-open**: при недоступности БД пользователи не блокируются (ошибка в лог)
- **Bypass-группа** для аварийного доступа (`2fa-bypass-emergency`)
- **Конфигурируемый allowlist** путей (логин, статика, REST, health-check балансировщика)
- **Кеш в HTTP-сессии** с TTL; отрицательный результат не кешируется — доступ открывается сразу после настройки 2FA
- **Админ-страница** (⚙️ → Система → Безопасность → Enforce 2SV): статистика rollout с прогресс-баром, экспорт CSV списка ненастроивших, все настройки на лету — без рестарта
- **REST API**: `GET/PUT /rest/enforce2sv/1.0/config`, `GET /rest/enforce2sv/1.0/stats` (SYSTEM_ADMIN)
- **Self-check** доступности AO-таблицы при старте (защита от смены схемы при патче Jira)
- Кластер DC: без состояния в JVM, конфигурация в общей БД через Plugin Settings API

## Совместимость / Compatibility

- Проверено на **Jira Data Center 10.6.1** (Java 17, PostgreSQL)
- Требуется нативная 2SV (atlassian-authentication-plugin 5.x) — есть во всех Jira DC 10.x
- На Jira 11.2+ плагин не нужен — используйте нативный флаг enforcement

## Установка / Install

Готовый jar: [`release/jira-enforce-2sv-plugin-1.2.0.jar`](release/)

Полная процедура прод-развёртывания: [`jira-enforce-2sv-plugin/INSTALL_CHECKLIST.md`](jira-enforce-2sv-plugin/INSTALL_CHECKLIST.md)

Сборка из исходников / build from source:

```bash
cd jira-enforce-2sv-plugin
mvn clean package   # требуется доступ к https://packages.atlassian.com/mvn/maven-external
```

## Дисклеймер / Disclaimer

Неофициальное решение: Atlassian не гарантирует стабильность имени AO-таблицы между версиями. Плагин делает self-check при старте и работает в режиме fail-open, но при апгрейде Jira сверяйте схему скриптом `jira-enforce-2sv-plugin/scripts/diagnose-2sv-table.sql`.
