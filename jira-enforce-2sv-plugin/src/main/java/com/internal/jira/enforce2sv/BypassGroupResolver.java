package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;

/**
 * Проверка членства в группе-исключении (аварийный/recovery-доступ).
 * Управление составом группы — штатными средствами Jira (User Management), не плагином.
 *
 * GroupManager берётся через {@link ComponentAccessor} — см. Enforce2SVFilter.
 */
public class BypassGroupResolver {

    private final PluginSettingsConfig config;

    public BypassGroupResolver(PluginSettingsConfig config) {
        this.config = config;
    }

    public boolean isBypassed(ApplicationUser user) {
        String bypassGroup = config.getBypassGroup();
        if (bypassGroup == null || bypassGroup.isEmpty()) {
            return false;
        }
        GroupManager groupManager = ComponentAccessor.getGroupManager();
        return groupManager.isUserInGroup(user, bypassGroup);
    }
}
