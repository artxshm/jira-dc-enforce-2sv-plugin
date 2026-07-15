package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * LENIENT: setUp() стабит config.isEnabled()/getAllowlist() для общего случая,
 * но часть тестов (например passesThroughWhenPluginDisabled) выходит из фильтра раньше
 * и не обращается к этим стабам — со strict-режимом Mockito это считается
 * "unnecessary stubbing" и падает тест, хотя логической ошибки нет.
 *
 * ComponentAccessor.getJiraAuthenticationContext() мокается статически, т.к. Enforce2SVFilter
 * берёт JiraAuthenticationContext через ComponentAccessor, а не конструкторной инъекцией
 * (см. комментарий в Enforce2SVFilter).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Enforce2SVFilterTest {

    @Mock private JiraAuthenticationContext authenticationContext;
    @Mock private TwoSVStatusChecker statusChecker;
    @Mock private BypassGroupResolver bypassGroupResolver;
    @Mock private PluginSettingsConfig config;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private ApplicationUser user;

    private HttpSession session;
    private Enforce2SVFilter filter;
    private MockedStatic<ComponentAccessor> componentAccessor;

    @BeforeEach
    void setUp() {
        filter = new Enforce2SVFilter(statusChecker, bypassGroupResolver, config);
        session = new InMemoryHttpSession();
        when(config.isEnabled()).thenReturn(true);
        when(config.getAllowlist()).thenReturn(Arrays.asList("/rest/", "/login.jsp"));
        when(config.getCacheTtlMinutes()).thenReturn(15);

        componentAccessor = Mockito.mockStatic(ComponentAccessor.class);
        componentAccessor.when(ComponentAccessor::getJiraAuthenticationContext).thenReturn(authenticationContext);
    }

    @AfterEach
    void tearDown() {
        componentAccessor.close();
    }

    @Test
    void passesThroughWhenPluginDisabled() throws Exception {
        when(config.isEnabled()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(authenticationContext, never()).getLoggedInUser();
    }

    @Test
    void passesThroughAnonymousRequests() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void passesThroughAllowlistedUri() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/rest/api/2/myself");
        when(request.getContextPath()).thenReturn("/jira");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(statusChecker, never()).isEnrolled(user);
    }

    @Test
    void passesThroughBypassGroupMember() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(bypassGroupResolver.isBypassed(user)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(statusChecker, never()).isEnrolled(user);
    }

    @Test
    void passesThroughAndCachesWhenEnrolled() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(statusChecker.isEnrolled(user)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void usesSessionCacheOnSecondRequest() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(statusChecker.isEnrolled(user)).thenReturn(true);

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(statusChecker, times(1)).isEnrolled(user);
        verify(chain, times(2)).doFilter(request, response);
    }

    @Test
    void doesNotCacheNegativeResult_allowsImmediatelyAfterEnrollment() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(config.isDryRun()).thenReturn(false);
        // первый запрос — пользователь ещё не настроил 2SV, второй — уже настроил
        when(statusChecker.isEnrolled(user)).thenReturn(false).thenReturn(true);

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        // негатив не закешировался: оба запроса дошли до проверки БД,
        // и сразу после включения 2SV пользователя пустило без ожидания TTL
        verify(statusChecker, times(2)).isEnrolled(user);
        verify(response, times(1)).sendRedirect(anyString());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void redirectsWhenNotEnrolledAndNotDryRun() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(statusChecker.isEnrolled(user)).thenReturn(false);
        when(config.isDryRun()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/jira" + Enforce2SVFilter.DEFAULT_2SV_SETUP_PATH);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void onlyLogsWhenNotEnrolledAndDryRun() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(statusChecker.isEnrolled(user)).thenReturn(false);
        when(config.isDryRun()).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void failsOpenWhenDatabaseUnavailable() throws Exception {
        when(authenticationContext.getLoggedInUser()).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/jira/secure/Dashboard.jspa");
        when(request.getContextPath()).thenReturn("/jira");
        when(request.getSession(true)).thenReturn(session);
        when(bypassGroupResolver.isBypassed(user)).thenReturn(false);
        when(statusChecker.isEnrolled(user)).thenThrow(new TwoSVCheckException("db down", new RuntimeException()));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    /** Простая реализация HttpSession на карте — без over-stubbing строгими моками Mockito. */
    private static class InMemoryHttpSession implements HttpSession {
        private final java.util.Map<String, Object> attrs = new java.util.HashMap<>();

        @Override public Object getAttribute(String name) { return attrs.get(name); }
        @Override public void setAttribute(String name, Object value) { attrs.put(name, value); }
        @Override public void removeAttribute(String name) { attrs.remove(name); }

        @Override public long getCreationTime() { return 0; }
        @Override public String getId() { return "fake"; }
        @Override public long getLastAccessedTime() { return 0; }
        @Override public javax.servlet.ServletContext getServletContext() { return null; }
        @Override public void setMaxInactiveInterval(int interval) { }
        @Override public int getMaxInactiveInterval() { return 0; }
        @Override public javax.servlet.http.HttpSessionContext getSessionContext() { return null; }
        @Override public Object getValue(String name) { return null; }
        @Override public String[] getValueNames() { return new String[0]; }
        @Override public void putValue(String name, Object value) { }
        @Override public void removeValue(String name) { }
        @Override public void invalidate() { }
        @Override public boolean isNew() { return false; }
        @Override public java.util.Enumeration<String> getAttributeNames() { return Collections.enumeration(attrs.keySet()); }
    }
}
