package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Страница-объяснение, на которую фильтр редиректит пользователей без настроенной 2SV.
 * Объясняет, почему доступ ограничен, и ведёт на вкладку «Двухэтапная проверка» профиля
 * (view-profile-panel нативного atlassian-authentication-plugin).
 *
 * Язык (рус/англ) выбирается по локали пользователя в Jira (LocaleManager); для анонимных
 * запросов — по Accept-Language браузера. Дефолт — русский.
 *
 * Страница доступна по /plugins/servlet/enforce2sv-info — путь покрыт префиксом
 * "/plugins/" в allowlist, поэтому redirect-loop невозможен.
 */
public class Enforce2SVInfoServlet extends HttpServlet {

    private static final String PROFILE_2SV_TAB =
            "/secure/ViewProfile.jspa?selectedTab=com.atlassian.plugins.authentication.atlassian-authentication-plugin:jira-user-profile-2sv";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        String displayName = user != null ? user.getDisplayName() : "";
        String profileUrl = request.getContextPath() + PROFILE_2SV_TAB;

        Locale locale = user != null
                ? ComponentAccessor.getLocaleManager().getLocaleFor(user)
                : request.getLocale();
        boolean en = locale != null && "en".equalsIgnoreCase(locale.getLanguage());

        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"" + (en ? "en" : "ru") + "\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"decorator\" content=\"atl.general\">");
        out.println("  <title>" + (en
                ? "Two-factor authentication setup required"
                : "Требуется настройка двухфакторной аутентификации") + "</title>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div style=\"max-width: 640px; margin: 48px auto; padding: 0 16px;\">");
        out.println("  <div class=\"aui-message aui-message-warning\">");
        out.println("    <p class=\"title\"><strong>" + (en
                ? "Two-factor authentication setup required"
                : "Требуется настройка двухфакторной аутентификации") + "</strong></p>");
        out.println("  </div>");
        if (!displayName.isEmpty()) {
            out.println("  <p>" + (en ? "Hello, " : "Здравствуйте, ")
                    + "<strong>" + escapeHtml(displayName) + "</strong>!</p>");
        }
        if (en) {
            out.println("  <p>According to the company information security policy, you must enable");
            out.println("  <strong>two-step verification (2FA)</strong> to continue working in Jira.");
            out.println("  Access to all other sections will be restored right after setup.</p>");
            out.println("  <h3>How to enable it (takes 2 minutes):</h3>");
            out.println("  <ol>");
            out.println("    <li>Install an authenticator app on your phone if you don't have one yet");
            out.println("        (Google Authenticator, Microsoft Authenticator, FreeOTP, etc.).</li>");
            out.println("    <li>Click the “Go to setup” button below — the");
            out.println("        <strong>Two-step verification</strong> tab of your profile will open; click “Enable”.</li>");
            out.println("    <li>Scan the QR code with the app and enter the six-digit code.</li>");
            out.println("    <li>Save the recovery codes in a safe place — you will need them if you lose your phone.</li>");
            out.println("  </ol>");
            out.println("  <p style=\"margin-top: 24px;\">");
            out.println("    <a class=\"aui-button aui-button-primary\" href=\"" + profileUrl + "\">Go to setup</a>");
            out.println("  </p>");
            out.println("  <p style=\"color: #6B778C; margin-top: 24px;\">If you have any issues with the setup —");
            out.println("  please contact the technical support team.</p>");
        } else {
            out.println("  <p>Согласно политике информационной безопасности компании, для продолжения работы в Jira");
            out.println("  необходимо включить <strong>двухэтапную проверку (2FA)</strong>. Доступ к остальным разделам");
            out.println("  будет открыт сразу после настройки.</p>");
            out.println("  <h3>Как включить (займёт 2 минуты):</h3>");
            out.println("  <ol>");
            out.println("    <li>Установите на телефон приложение-аутентификатор, если его ещё нет");
            out.println("        (Google Authenticator, Microsoft Authenticator, FreeOTP и т.п.).</li>");
            out.println("    <li>Нажмите кнопку «Перейти к настройке» ниже — откроется вкладка");
            out.println("        <strong>«Двухэтапная проверка»</strong> в вашем профиле; нажмите «Включить».</li>");
            out.println("    <li>Отсканируйте QR-код приложением и введите шестизначный код.</li>");
            out.println("    <li>Сохраните резервные коды в надёжном месте — они понадобятся при утере телефона.</li>");
            out.println("  </ol>");
            out.println("  <p style=\"margin-top: 24px;\">");
            out.println("    <a class=\"aui-button aui-button-primary\" href=\"" + profileUrl + "\">Перейти к настройке</a>");
            out.println("  </p>");
            out.println("  <p style=\"color: #6B778C; margin-top: 24px;\">Если у вас возникли проблемы с настройкой —");
            out.println("  обратитесь в службу технической поддержки.</p>");
        }
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
