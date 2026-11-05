package com.example.aigc.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/internal/node-config");

    @Test
    void clientAbortIsNotReportedAsServerError() {
        ResponseEntity<?> response = handler.handleIOException(
                new IOException("你的主机中的软件中止了一个已建立的连接。"), request);

        assertEquals(499, response.getStatusCodeValue());
        assertNull(response.getBody());
    }

    @Test
    void wrappedClientAbortIsDetectedThroughTheCauseChain() {
        ResponseEntity<?> response = handler.handleRuntimeException(
                new RuntimeException(new IOException("Broken pipe")), request);

        assertEquals(499, response.getStatusCodeValue());
        assertNull(response.getBody());
    }

    @Test
    void otherIoErrorStillReturns500() {
        ResponseEntity<?> response = handler.handleIOException(new IOException("disk full"), request);

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof ApiResponse);
    }

    @Test
    void ordinaryRuntimeExceptionStillReturns500() {
        ResponseEntity<?> response = handler.handleRuntimeException(new IllegalStateException("boom"), request);

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof ApiResponse);
    }

    @Test
    void abortDetectionCoversCommonSocketMessages() {
        assertTrue(GlobalExceptionHandler.isConnectionAbort(new IOException("Connection reset by peer")));
        assertTrue(GlobalExceptionHandler.isConnectionAbort(new IOException("远程主机强迫关闭了一个现有的连接。")));
        assertTrue(GlobalExceptionHandler.isConnectionAbort(new java.net.SocketException("socket closed")));
    }
}
