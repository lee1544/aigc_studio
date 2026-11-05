package com.example.aigc.config;

import com.example.aigc.annotation.SuperAdminOnly;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code @SuperAdminOnly} 的放行/拒绝逻辑与注解解析。 */
class SuperAdminInterceptorTest {

    private SuperAdminInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SuperAdminInterceptor();
        ReflectionTestUtils.setField(interceptor, "objectMapper", new ObjectMapper());
    }

    @Test
    void allowsSuperAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/organizations");
        request.setAttribute("isSuperAdmin", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler("create")));
    }

    @Test
    void allowsDevBypass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/organizations");
        request.setAttribute("isDevBypass", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler("create")));
    }

    @Test
    void rejectsOrgAdminWith403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/organizations");
        request.setAttribute("isSuperAdmin", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler("create")));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("创建组织仅限超级管理员操作"));
    }

    @Test
    void ignoresHandlersWithoutAnnotation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler("plain")));
    }

    @Test
    void classLevelAnnotationAppliesToAllMethods() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/model-connections");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new AnnotatedController(),
                AnnotatedController.class.getMethod("any"));

        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, response.getStatus());
    }

    private HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod(name));
    }

    public static class SampleController {
        @SuperAdminOnly("创建组织")
        public void create() {
        }

        public void plain() {
        }
    }

    @SuperAdminOnly("示例平台操作")
    public static class AnnotatedController {
        public void any() {
        }
    }
}
