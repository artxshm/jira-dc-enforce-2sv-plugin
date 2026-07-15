package com.internal.jira.enforce2sv;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowlistMatcherTest {

    private static final List<String> ALLOWLIST = Arrays.asList(
            "/secure/ViewProfile.jspa", "/rest/", "/plugins/", "/login.jsp");

    @Test
    void allowsExactPrefixMatch() {
        assertTrue(AllowlistMatcher.isAllowed("/jira/rest/api/2/issue/TEST-1", "/jira", ALLOWLIST));
    }

    @Test
    void blocksUriNotInAllowlist() {
        assertFalse(AllowlistMatcher.isAllowed("/jira/secure/Dashboard.jspa", "/jira", ALLOWLIST));
    }

    @Test
    void stripsContextPathBeforeMatching() {
        assertTrue(AllowlistMatcher.isAllowed("/jira/login.jsp", "/jira", ALLOWLIST));
    }

    @Test
    void handlesEmptyContextPath() {
        assertTrue(AllowlistMatcher.isAllowed("/rest/api/2/myself", "", ALLOWLIST));
    }

    @Test
    void returnsFalseForNullUri() {
        assertFalse(AllowlistMatcher.isAllowed(null, "/jira", ALLOWLIST));
    }

    @Test
    void returnsFalseForEmptyAllowlist() {
        assertFalse(AllowlistMatcher.isAllowed("/jira/rest/api/2/myself", "/jira", Collections.emptyList()));
    }
}
