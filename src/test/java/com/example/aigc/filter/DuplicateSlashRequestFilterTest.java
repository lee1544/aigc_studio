package com.example.aigc.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DuplicateSlashRequestFilterTest {

    private final DuplicateSlashRequestFilter filter = new DuplicateSlashRequestFilter();

    @Test
    void collapseDuplicateSlashesKeepsSingleSlashes() {
        assertEquals("/api/users", DuplicateSlashRequestFilter.collapseDuplicateSlashes("/api//users"));
        assertEquals("/admin/login.html",
                DuplicateSlashRequestFilter.collapseDuplicateSlashes("/admin//login.html"));
        assertEquals("/api/public/settings",
                DuplicateSlashRequestFilter.collapseDuplicateSlashes("//api///public//settings"));
    }

    @Test
    void collapseDuplicateSlashesLeavesPlainPathsUntouched() {
        assertEquals("/api/users", DuplicateSlashRequestFilter.collapseDuplicateSlashes("/api/users"));
        assertEquals("/", DuplicateSlashRequestFilter.collapseDuplicateSlashes("/"));
        assertNull(DuplicateSlashRequestFilter.collapseDuplicateSlashes(null));
    }

    @Test
    void normalizesRequestUriBeforeFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "//api//users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenUri = new String[1];

        filter.doFilter(request, response, (req, res) -> seenUri[0] =
                ((HttpServletRequest) req).getRequestURI());

        assertEquals("/api/users", seenUri[0]);
    }

    @Test
    void leavesNormalRequestUnwrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object[] seenRequest = new Object[1];

        filter.doFilter(request, response, (req, res) -> seenRequest[0] = req);

        assertSame(request, seenRequest[0]);
    }
}
