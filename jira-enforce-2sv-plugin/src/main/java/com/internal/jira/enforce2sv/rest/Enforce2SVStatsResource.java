package com.internal.jira.enforce2sv.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.internal.jira.enforce2sv.TwoSVCheckException;
import com.internal.jira.enforce2sv.TwoSVStatusChecker;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Статистика прогресса 2SV-rollout для администраторов (доступ — только SYSTEM_ADMIN):
 *
 * GET /rest/enforce2sv/1.0/stats            — счётчики + список ненастроивших (до limit)
 * GET /rest/enforce2sv/1.0/stats?limit=500  — увеличить лимит списка
 *
 * Ответ:
 * {
 *   "activeUsers": 120,
 *   "enrolled": 45,
 *   "notEnrolled": 75,
 *   "notEnrolledUsers": [{"username": "...", "displayName": "..."}, ...]
 * }
 */
@Path("/stats")
@Produces(MediaType.APPLICATION_JSON)
public class Enforce2SVStatsResource {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 5000;

    @GET
    public Response stats(@QueryParam("limit") @DefaultValue("" + DEFAULT_LIMIT) int limit) {
        Response denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        TwoSVStatusChecker checker = new TwoSVStatusChecker();
        try {
            long activeUsers = checker.countActiveUsers();
            long enrolled = checker.countEnrolled();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("activeUsers", activeUsers);
            body.put("enrolled", enrolled);
            body.put("notEnrolled", Math.max(0, activeUsers - enrolled));
            body.put("notEnrolledUsers", checker.listNotEnrolledActiveUsers(effectiveLimit));
            return Response.ok(body).build();
        } catch (TwoSVCheckException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
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
}
