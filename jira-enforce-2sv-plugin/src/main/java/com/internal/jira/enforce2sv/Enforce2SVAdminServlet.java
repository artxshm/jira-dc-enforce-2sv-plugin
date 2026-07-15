package com.internal.jira.enforce2sv;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Админ-страница плагина: тумблеры enabled/dryRun, bypass-группа, TTL кеша, allowlist
 * и статистика rollout. Доступ — только SYSTEM_ADMIN. Сохранение — через существующий
 * REST (PUT /rest/enforce2sv/1.0/config), статистика — GET /rest/enforce2sv/1.0/stats;
 * сама страница только рендерит форму (никакой логики записи в сервлете).
 *
 * URL: /plugins/servlet/enforce2sv-admin (ссылка добавлена в раздел Безопасность админки).
 */
public class Enforce2SVAdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager();
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null || !globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only system administrators can access this page");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        String ctx = request.getContextPath();

        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"ru\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"decorator\" content=\"atl.admin\">");
        out.println("  <title>Enforce 2SV — настройки</title>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div style=\"max-width: 760px;\">");
        out.println("  <h2>Enforce 2SV — принудительная двухэтапная проверка</h2>");
        out.println("  <p>Плагин перенаправляет пользователей без настроенной 2SV на страницу-инструкцию,");
        out.println("  пока они не включат двухэтапную проверку в профиле.</p>");

        out.println("  <h3>Статистика rollout</h3>");
        out.println("  <div id=\"stats\" style=\"font-size:16px; margin-bottom:8px;\">Загрузка…</div>");
        out.println("  <div style=\"background:#DFE1E6; border-radius:4px; height:18px; max-width:480px; overflow:hidden;\">");
        out.println("    <div id=\"progressBar\" style=\"background:#36B37E; height:18px; width:0%;");
        out.println("      transition:width .4s; color:#fff; font-size:12px; line-height:18px; text-align:center;\"></div>");
        out.println("  </div>");
        out.println("  <div style=\"margin-top:12px;\">");
        out.println("    <button class=\"aui-button\" id=\"refreshStats\">Обновить</button>");
        out.println("    <button class=\"aui-button\" id=\"exportCsv\">Экспорт CSV (без 2SV)</button>");
        out.println("  </div>");
        out.println("  <h4 style=\"margin-top:16px;\">Пользователи без 2SV</h4>");
        out.println("  <div id=\"notEnrolledList\" style=\"max-height:360px; overflow-y:auto; max-width:640px;\"></div>");

        out.println("  <h3 style=\"margin-top:24px;\">Настройки</h3>");
        out.println("  <form id=\"cfg\" class=\"aui\" onsubmit=\"return false;\">");
        out.println("    <div class=\"checkbox\"><input class=\"checkbox\" type=\"checkbox\" id=\"enabled\">");
        out.println("      <label for=\"enabled\">Плагин включён (enabled)</label></div>");
        out.println("    <div class=\"checkbox\"><input class=\"checkbox\" type=\"checkbox\" id=\"dryRun\">");
        out.println("      <label for=\"dryRun\">Режим наблюдения (dry-run): только логировать, не блокировать</label></div>");
        out.println("    <div class=\"field-group\"><label for=\"bypassGroup\">Bypass-группа</label>");
        out.println("      <input class=\"text\" type=\"text\" id=\"bypassGroup\">");
        out.println("      <div class=\"description\">Члены группы никогда не блокируются (аварийный доступ)</div></div>");
        out.println("    <div class=\"field-group\"><label for=\"cacheTtlMinutes\">TTL кеша, минут</label>");
        out.println("      <input class=\"text short-field\" type=\"number\" min=\"1\" id=\"cacheTtlMinutes\"></div>");
        out.println("    <div class=\"field-group\"><label for=\"allowlist\">Allowlist (по префиксу на строку)</label>");
        out.println("      <textarea class=\"textarea\" id=\"allowlist\" rows=\"10\" style=\"font-family:monospace;\"></textarea>");
        out.println("      <div class=\"description\">Пути, не требующие 2SV: логин/логаут, статика, REST, страница профиля,");
        out.println("      health-check балансировщика. Менять осторожно — ошибка может вызвать redirect-loop.</div></div>");
        out.println("    <div class=\"buttons-container\"><div class=\"buttons\">");
        out.println("      <button class=\"aui-button aui-button-primary\" id=\"save\">Сохранить</button>");
        out.println("      <span id=\"saveResult\" style=\"margin-left:12px;\"></span>");
        out.println("    </div></div>");
        out.println("  </form>");
        out.println("</div>");

        out.println("<script>");
        out.println("(function() {");
        out.println("  var base = '" + ctx + "/rest/enforce2sv/1.0';");
        out.println("  function esc(s) { var d = document.createElement('div'); d.textContent = s == null ? '' : s; return d.innerHTML; }");
        out.println("  fetch(base + '/config', {headers: {'Accept': 'application/json'}})");
        out.println("    .then(function(r) { return r.json(); })");
        out.println("    .then(function(c) {");
        out.println("      document.getElementById('enabled').checked = c.enabled;");
        out.println("      document.getElementById('dryRun').checked = c.dryRun;");
        out.println("      document.getElementById('bypassGroup').value = c.bypassGroup;");
        out.println("      document.getElementById('cacheTtlMinutes').value = c.cacheTtlMinutes;");
        out.println("      document.getElementById('allowlist').value = c.allowlist.join('\\n');");
        out.println("    });");
        out.println("  var lastStats = null;");
        out.println("  function loadStats() {");
        out.println("    document.getElementById('stats').textContent = 'Загрузка…';");
        out.println("    fetch(base + '/stats?limit=5000', {headers: {'Accept': 'application/json'}})");
        out.println("      .then(function(r) { return r.json(); })");
        out.println("      .then(function(s) {");
        out.println("        lastStats = s;");
        out.println("        var pct = s.activeUsers > 0 ? Math.round(100 * s.enrolled / s.activeUsers) : 0;");
        out.println("        document.getElementById('stats').innerHTML =");
        out.println("          '<strong>' + s.enrolled + '</strong> из <strong>' + s.activeUsers + '</strong>'");
        out.println("          + ' активных пользователей настроили 2SV — <strong>' + pct + '%</strong>'");
        out.println("          + ' (осталось: <strong>' + s.notEnrolled + '</strong>)';");
        out.println("        var bar = document.getElementById('progressBar');");
        out.println("        bar.style.width = pct + '%';");
        out.println("        bar.textContent = pct >= 10 ? pct + '%' : '';");
        out.println("        bar.style.background = pct >= 80 ? '#36B37E' : (pct >= 40 ? '#FFAB00' : '#DE350B');");
        out.println("        var rows = s.notEnrolledUsers.map(function(u) {");
        out.println("          return '<tr><td>' + esc(u.username) + '</td><td>' + esc(u.displayName) + '</td></tr>';");
        out.println("        }).join('');");
        out.println("        document.getElementById('notEnrolledList').innerHTML = s.notEnrolledUsers.length === 0");
        out.println("          ? '<p>Все активные пользователи настроили 2SV 🎉</p>'");
        out.println("          : '<table class=\"aui\"><thead><tr><th>Логин</th><th>Имя</th></tr></thead><tbody>' + rows + '</tbody></table>';");
        out.println("      })");
        out.println("      .catch(function() { document.getElementById('stats').textContent = 'Не удалось загрузить статистику'; });");
        out.println("  }");
        out.println("  loadStats();");
        out.println("  document.getElementById('refreshStats').addEventListener('click', loadStats);");
        out.println("  document.getElementById('exportCsv').addEventListener('click', function() {");
        out.println("    if (!lastStats) { return; }");
        out.println("    var csv = '\\uFEFFusername;display_name\\r\\n' + lastStats.notEnrolledUsers.map(function(u) {");
        out.println("      return '\"' + (u.username || '').replace(/\"/g, '\"\"') + '\";\"' + (u.displayName || '').replace(/\"/g, '\"\"') + '\"';");
        out.println("    }).join('\\r\\n');");
        out.println("    var a = document.createElement('a');");
        out.println("    a.href = URL.createObjectURL(new Blob([csv], {type: 'text/csv;charset=utf-8'}));");
        out.println("    a.download = 'users-without-2sv.csv';");
        out.println("    a.click();");
        out.println("    URL.revokeObjectURL(a.href);");
        out.println("  });");
        out.println("  document.getElementById('save').addEventListener('click', function() {");
        out.println("    var body = {");
        out.println("      enabled: document.getElementById('enabled').checked,");
        out.println("      dryRun: document.getElementById('dryRun').checked,");
        out.println("      bypassGroup: document.getElementById('bypassGroup').value.trim(),");
        out.println("      cacheTtlMinutes: parseInt(document.getElementById('cacheTtlMinutes').value, 10) || 15,");
        out.println("      allowlist: document.getElementById('allowlist').value.split('\\n')");
        out.println("        .map(function(s) { return s.trim(); }).filter(function(s) { return s.length > 0; })");
        out.println("    };");
        out.println("    fetch(base + '/config', {");
        out.println("      method: 'PUT',");
        out.println("      headers: {'Content-Type': 'application/json', 'X-Atlassian-Token': 'no-check'},");
        out.println("      body: JSON.stringify(body)");
        out.println("    }).then(function(r) {");
        out.println("      document.getElementById('saveResult').textContent = r.ok ? 'Сохранено' : 'Ошибка: ' + r.status;");
        out.println("    });");
        out.println("  });");
        out.println("})();");
        out.println("</script>");
        out.println("</body>");
        out.println("</html>");
    }
}
