package com.internal.jira.enforce2sv;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Конфигурация плагина через Plugin Settings API (не статичный файл) — изменяется
 * без перезапуска ноды и одинакова на всех нодах кластера, т.к. хранится в общей БД (NFR-6).
 */
public class PluginSettingsConfig {

    static final String KEY_ENABLED = "enforce2sv.enabled";
    static final String KEY_DRY_RUN = "enforce2sv.dryRun";
    static final String KEY_BYPASS_GROUP = "enforce2sv.bypassGroup";
    static final String KEY_CACHE_TTL_MINUTES = "enforce2sv.cacheTtlMinutes";
    static final String KEY_ALLOWLIST = "enforce2sv.allowlist";

    // Дефолтный allowlist проверен вживую на Jira 10.6.1 (redirect-loop отсутствует).
    // Перед прод-rollout дополнительно сверить полноту по логам dry-run (маркер [ENFORCE_2SV],
    // redirected=true) — процедура описана в INSTALL_CHECKLIST.md, раздел 3.
    private static final List<String> DEFAULT_ALLOWLIST = Collections.unmodifiableList(Arrays.asList(
            // страница профиля = страница настройки 2SV в Jira (view-profile-panel из
            // atlassian-authentication-plugin) — обязательно в allowlist, иначе redirect-loop
            "/secure/ViewProfile.jspa",
            // логин/логаут — нельзя блокировать анонимные/переходные состояния сессии
            "/login.jsp",
            "/secure/Logout",
            // WebSudo (повторная аутентификация админа) — не должен уводить на 2SV setup.
            // Префикс без !default/.jspa: реальные запросы идут и на WebSudoAuthenticate.jspa (POST),
            // и на WebSudoAuthenticate!default.jspa (форма) — подтверждено логами dry-run.
            "/secure/admin/WebSudoAuthenticate",
            // статика и AJAX/REST, необходимые для отрисовки самой страницы 2SV setup
            "/rest/",
            "/plugins/",
            "/s/",
            "/download/",
            "/favicon.ico",
            // health-check эндпоинт балансировщика перед VIP-кластером — КРИТИЧНО не блокировать,
            // иначе LB может считать ноду нездоровой и вывести её из ротации
            "/status"
            // "/secure/admin/" сознательно НЕ в allowlist: админка не должна быть слепым пятном
            // для 2SV; для administrators на время rollout используйте bypass-группу, а не allowlist.
    ));

    private static final String DEFAULT_BYPASS_GROUP = "2fa-bypass-emergency";
    private static final int DEFAULT_CACHE_TTL_MINUTES = 15;

    // Безопасный дефолт: пока явно не включили блокировку, плагин только логирует (риск redirect-loop, см. ТЗ п.9).
    private static final boolean DEFAULT_DRY_RUN = true;
    private static final boolean DEFAULT_ENABLED = true;

    private final PluginSettingsFactory pluginSettingsFactory;

    public PluginSettingsConfig(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    private PluginSettings settings() {
        return pluginSettingsFactory.createGlobalSettings();
    }

    public boolean isEnabled() {
        return parseBoolean((String) settings().get(KEY_ENABLED), DEFAULT_ENABLED);
    }

    public void setEnabled(boolean enabled) {
        settings().put(KEY_ENABLED, Boolean.toString(enabled));
    }

    public boolean isDryRun() {
        return parseBoolean((String) settings().get(KEY_DRY_RUN), DEFAULT_DRY_RUN);
    }

    public void setDryRun(boolean dryRun) {
        settings().put(KEY_DRY_RUN, Boolean.toString(dryRun));
    }

    public String getBypassGroup() {
        String value = (String) settings().get(KEY_BYPASS_GROUP);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : DEFAULT_BYPASS_GROUP;
    }

    public void setBypassGroup(String groupName) {
        settings().put(KEY_BYPASS_GROUP, groupName);
    }

    public int getCacheTtlMinutes() {
        String value = (String) settings().get(KEY_CACHE_TTL_MINUTES);
        if (value == null) {
            return DEFAULT_CACHE_TTL_MINUTES;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_CACHE_TTL_MINUTES;
        }
    }

    public void setCacheTtlMinutes(int minutes) {
        settings().put(KEY_CACHE_TTL_MINUTES, Integer.toString(minutes));
    }

    public List<String> getAllowlist() {
        String value = (String) settings().get(KEY_ALLOWLIST);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_ALLOWLIST;
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? DEFAULT_ALLOWLIST : result;
    }

    public void setAllowlist(List<String> allowlist) {
        settings().put(KEY_ALLOWLIST, String.join(",", allowlist));
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }
}
