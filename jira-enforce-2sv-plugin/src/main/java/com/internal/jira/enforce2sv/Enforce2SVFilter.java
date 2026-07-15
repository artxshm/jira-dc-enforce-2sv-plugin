package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Реализует схему из раздела 5 ТЗ: авторизован? -> allowlist? -> bypass-группа? ->
 * кеш в сессии? -> проверка БД -> редирект/dry-run.
 *
 * Зависимости создаются вручную в {@link #init}, а не через Spring/OSGi constructor
 * injection: в этой сборке резолвинг бинов через Spring Scanner (как host-сервисов, так и
 * собственных классов плагина) оказался ненадёжным — ComponentAccessor и прямое
 * конструирование гарантированно работают в любом контексте (см. INSTALL_CHECKLIST.md).
 */
public class Enforce2SVFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(Enforce2SVFilter.class);

    /** Уникальный маркер для grep/парсинга логов (FR-6). */
    static final String LOG_MARKER = "ENFORCE_2SV";

    // Редирект ведёт на нашу страницу-объяснение (Enforce2SVInfoServlet), а не сразу на
    // профиль: пользователь должен понимать, ПОЧЕМУ его перенаправили и что делать.
    // Сама настройка 2SV в Jira — это view-profile-panel на /secure/ViewProfile.jspa
    // (подтверждено на Jira 10.6.1; отдельного URL типа /plugins/servlet/two-step-verification
    // в Jira нет — он только для Bitbucket/Bamboo/Crowd).
    static final String DEFAULT_2SV_SETUP_PATH = "/plugins/servlet/enforce2sv-info";

    private TwoSVStatusChecker statusChecker;
    private BypassGroupResolver bypassGroupResolver;
    private PluginSettingsConfig config;

    public Enforce2SVFilter() {
    }

    /** Конструктор для юнит-тестов — внедряет моки напрямую, минуя init(). */
    Enforce2SVFilter(TwoSVStatusChecker statusChecker, BypassGroupResolver bypassGroupResolver, PluginSettingsConfig config) {
        this.statusChecker = statusChecker;
        this.bypassGroupResolver = bypassGroupResolver;
        this.config = config;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        if (config != null) {
            return;
        }
        PluginSettingsFactory pluginSettingsFactory =
                ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
        config = new PluginSettingsConfig(pluginSettingsFactory);
        statusChecker = new TwoSVStatusChecker();
        bypassGroupResolver = new BypassGroupResolver(config);

        // Self-check доступности AO-таблицы (риск из ТЗ п.9) — результат в лог INFO/ERROR
        statusChecker.selfCheck();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!config.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            // анонимные запросы (в т.ч. страница логина) не проверяем — FR-1 требует проверки
            // только для аутентифицированных пользователей
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        if (AllowlistMatcher.isAllowed(uri, httpRequest.getContextPath(), config.getAllowlist())) {
            chain.doFilter(request, response);
            return;
        }

        if (bypassGroupResolver.isBypassed(user)) {
            chain.doFilter(request, response);
            return;
        }

        boolean enrolled = resolveEnrollment(user, httpRequest.getSession(true));

        if (enrolled) {
            chain.doFilter(request, response);
            return;
        }

        boolean dryRun = config.isDryRun();
        log.info("[{}] user='{}' uri='{}' dryRun={} redirected={}",
                LOG_MARKER, user.getUsername(), uri, dryRun, !dryRun);

        if (dryRun) {
            chain.doFilter(request, response);
            return;
        }

        httpResponse.sendRedirect(httpRequest.getContextPath() + DEFAULT_2SV_SETUP_PATH);
    }

    /**
     * Возвращает статус регистрации 2SV, используя кеш сессии (NFR-2) и
     * fail-open при недоступности БД (NFR-5).
     *
     * Кешируется ТОЛЬКО положительный результат (enrolled=true): он стабилен и именно
     * настроившие пользователи генерируют основной трафик. Отрицательный результат не
     * кешируется — иначе пользователь, только что включивший 2SV, продолжал бы получать
     * редиректы до истечения TTL (или перелогина). Нагрузка от некеширования негатива
     * минимальна: не-настроившие пользователи заперты на страницах из allowlist,
     * до SQL-проверки доходят только их единичные редиректящие запросы.
     */
    private boolean resolveEnrollment(ApplicationUser user, HttpSession session) {
        long ttlMillis = config.getCacheTtlMinutes() * 60_000L;
        long now = System.currentTimeMillis();

        Boolean cached = TwoSVSessionCache.getIfValid(session, ttlMillis, now);
        if (cached != null) {
            return cached;
        }

        try {
            boolean enrolled = statusChecker.isEnrolled(user);
            if (enrolled) {
                TwoSVSessionCache.put(session, true, now);
            }
            return enrolled;
        } catch (TwoSVCheckException e) {
            log.error("[{}] Fail-open: не удалось проверить статус 2SV для пользователя '{}', запрос пропущен. Причина: {}",
                    LOG_MARKER, user.getUsername(), e.getMessage(), e);
            return true;
        }
    }
}
