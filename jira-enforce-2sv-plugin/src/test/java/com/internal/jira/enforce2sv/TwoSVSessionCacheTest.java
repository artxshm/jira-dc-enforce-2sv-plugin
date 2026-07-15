package com.internal.jira.enforce2sv;

import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TwoSVSessionCacheTest {

    @Test
    void returnsNullWhenNothingCached() {
        HttpSession session = new FakeHttpSession();
        assertNull(TwoSVSessionCache.getIfValid(session, 900_000L, 1_000_000L));
    }

    @Test
    void returnsCachedValueWithinTtl() {
        HttpSession session = new FakeHttpSession();
        long now = 1_000_000L;
        TwoSVSessionCache.put(session, true, now);

        Boolean result = TwoSVSessionCache.getIfValid(session, 900_000L, now + 500_000L);

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void returnsNullWhenTtlExpired() {
        HttpSession session = new FakeHttpSession();
        long now = 1_000_000L;
        TwoSVSessionCache.put(session, false, now);

        Boolean result = TwoSVSessionCache.getIfValid(session, 900_000L, now + 900_001L);

        assertNull(result);
    }

    @Test
    void cachesNegativeResultToo() {
        HttpSession session = new FakeHttpSession();
        long now = 1_000_000L;
        TwoSVSessionCache.put(session, false, now);

        Boolean result = TwoSVSessionCache.getIfValid(session, 900_000L, now + 1);

        assertFalse(result);
    }

    /** Простейшая фейковая реализация HttpSession для проверки атрибутов без Mockito-стабов. */
    private static class FakeHttpSession implements HttpSession {
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
        @Override public java.util.Enumeration<String> getAttributeNames() { return java.util.Collections.enumeration(attrs.keySet()); }
    }
}
