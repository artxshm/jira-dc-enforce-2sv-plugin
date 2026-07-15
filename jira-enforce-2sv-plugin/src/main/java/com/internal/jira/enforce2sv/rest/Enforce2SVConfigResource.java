package com.internal.jira.enforce2sv.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.internal.jira.enforce2sv.PluginSettingsConfig;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Позволяет системным администраторам менять enabled/dryRun/bypassGroup/ttl/allowlist
 * без пересборки плагина и без рестарта ноды (NFR-6). Доступ — только SYSTEM_ADMIN.
 *
 * GET  /rest/enforce2sv/1.0/config
 * PUT  /rest/enforce2sv/1.0/config  { "enabled": true, "dryRun": false, ... }
 *
 * Зависимости берутся через {@link ComponentAccessor} (без конструкторной инъекции):
 * REST-модуль Jira использует Jersey/HK2 для инстанцирования ресурсов, а не Spring-контекст
 * плагина, поэтому обычный {@code @Inject} для произвольных бинов там не резолвится —
 * ComponentAccessor работает в любом контексте одинаково надёжно.
 */
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class Enforce2SVConfigResource {

    private PluginSettingsConfig config() {
        PluginSettingsFactory pluginSettingsFactory =
                ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
        return new PluginSettingsConfig(pluginSettingsFactory);
    }

    @GET
    public Response get() {
        Response denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Response.ok(toMap(config())).build();
    }

    @PUT
    public Response update(ConfigUpdateRequest update) {
        Response denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        PluginSettingsConfig config = config();
        if (update.enabled != null) {
            config.setEnabled(update.enabled);
        }
        if (update.dryRun != null) {
            config.setDryRun(update.dryRun);
        }
        if (update.bypassGroup != null) {
            config.setBypassGroup(update.bypassGroup);
        }
        if (update.cacheTtlMinutes != null) {
            config.setCacheTtlMinutes(update.cacheTtlMinutes);
        }
        if (update.allowlist != null) {
            config.setAllowlist(Arrays.asList(update.allowlist));
        }
        return Response.ok(toMap(config)).build();
    }

    private Map<String, Object> toMap(PluginSettingsConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", config.isEnabled());
        body.put("dryRun", config.isDryRun());
        body.put("bypassGroup", config.getBypassGroup());
        body.put("cacheTtlMinutes", config.getCacheTtlMinutes());
        body.put("allowlist", config.getAllowlist());
        return body;
    }

    private Response requireAdmin() {
        JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager();
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null || !globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return null;
    }

    public static class ConfigUpdateRequest {
        public Boolean enabled;
        public Boolean dryRun;
        public String bypassGroup;
        public Integer cacheTtlMinutes;
        public String[] allowlist;
    }
}
