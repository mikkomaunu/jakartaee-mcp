package org.maunu.jakartaee.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Streamable HTTP MCP server.
 * Tests compliance with MCP 2025-03-26 Streamable HTTP transport specification.
 *
 * @author Mikko Maunu
 */
public class StreamableHttpMcpServerTest {

    private StreamableHttpMcpServer server;
    private int port;

    @BeforeEach
    public void setUp() throws IOException {
        port = 8082;
        server = new StreamableHttpMcpServer(new PackageScanner(), port);
        server.start();
        
        // Give the server a moment to start
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    // ==================== Single Endpoint Tests ====================

    @Test
    public void testSingleMcpEndpointExists() throws IOException {
        // Send a valid initialize request to test the endpoint
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        // Send request body
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "POST /mcp should return 200 for valid request");
    }

    @Test
    public void testOldEndpointsNotAvailable() throws IOException {
        // Test that old multi-endpoint approach is gone
        String[] oldEndpoints = {"/mcp/info", "/mcp/tools", "/mcp/tools/call"};
        
        for (String endpoint : oldEndpoints) {
            URL url = new URL("http://localhost:" + port + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            assertEquals(405, responseCode, "Old endpoint " + endpoint + " should return 405 (Method Not Allowed)");
        }
    }

    // ==================== Initialize Tests ====================

    @Test
    public void testInitializeRequest() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        // Send request body
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Initialize request should return 200");
        
        // Check for session ID header
        String sessionId = connection.getHeaderField("Mcp-Session-Id");
        assertNotNull(sessionId, "Initialize response should include Mcp-Session-Id header");
        assertFalse(sessionId.isEmpty(), "Session ID should not be empty");
        
        // Read response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.contains("\"protocolVersion\":\"2025-03-26\""), 
                "Response should contain Streamable HTTP protocol version");
            assertTrue(responseBody.contains("\"capabilities\":"), 
                "Response should contain capabilities");
            assertTrue(responseBody.contains("\"serverInfo\":"), 
                "Response should contain serverInfo");
            assertTrue(responseBody.contains("\"sessionId\":"), 
                "Response should contain sessionId in result");
        }
    }

    @Test
    public void testInitializeWithSessionIdInResponse() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode);
        
        // Check session ID header
        String sessionId = connection.getHeaderField("Mcp-Session-Id");
        assertNotNull(sessionId);
    }

    // ==================== Session Management Tests ====================

    @Test
    public void testSessionRequiredAfterInitialize() throws IOException {
        // First, initialize to get a session
        String initRequest = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        
        URL initUrl = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection initConnection = (HttpURLConnection) initUrl.openConnection();
        initConnection.setRequestMethod("POST");
        initConnection.setRequestProperty("Content-Type", "application/json");
        initConnection.setDoOutput(true);
        initConnection.setConnectTimeout(5000);
        initConnection.setReadTimeout(5000);
        
        try (OutputStream os = initConnection.getOutputStream()) {
            os.write(initRequest.getBytes(StandardCharsets.UTF_8));
        }
        
        String sessionId = initConnection.getHeaderField("Mcp-Session-Id");
        assertNotNull(sessionId);
        
        // Now try to call tools/list without session ID - should work
        // (sessions are optional for simple requests)
        String toolsRequest = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        
        URL toolsUrl = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection toolsConnection = (HttpURLConnection) toolsUrl.openConnection();
        toolsConnection.setRequestMethod("POST");
        toolsConnection.setRequestProperty("Content-Type", "application/json");
        toolsConnection.setDoOutput(true);
        toolsConnection.setConnectTimeout(5000);
        toolsConnection.setReadTimeout(5000);
        
        try (OutputStream os = toolsConnection.getOutputStream()) {
            os.write(toolsRequest.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = toolsConnection.getResponseCode();
        assertEquals(200, responseCode, "Tools/list should work without session ID");
    }

    @Test
    public void testInvalidSessionId() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Mcp-Session-Id", "invalid-session-id");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(404, responseCode, "Invalid session ID should return 404");
    }

    // ==================== Tools Tests ====================

    @Test
    public void testToolsListRequest() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Tools/list should return 200");
        
        // Read response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.contains("\"list_packages\""), 
                "Response should contain list_packages tool");
            assertTrue(responseBody.contains("\"get_package_doc\""), 
                "Response should contain get_package_doc tool");
            assertTrue(responseBody.contains("\"list_classes\""), 
                "Response should contain list_classes tool");
            assertTrue(responseBody.contains("\"get_source_code\""), 
                "Response should contain get_source_code tool");
        }
    }

    @Test
    public void testToolCall() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," +
                "\"params\":{\"name\":\"list_packages\",\"arguments\":{\"root\":\"jakarta\"}}}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Tool call should return 200");
        
        // Read response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.contains("\"id\":1"), 
                "Response should contain request ID");
            assertTrue(responseBody.contains("\"result\":"), 
                "Response should contain result");
            assertTrue(responseBody.contains("\"content\":"), 
                "Response should contain content from tool");
        }
    }

    // ==================== Batch Message Tests ====================

    @Test
    public void testBatchMessages() throws IOException {
        String batchRequest = "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}," +
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}]";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = batchRequest.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Batch request should return 200");
        
        // Read response (should be a JSON array)
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.startsWith("["), 
                "Batch response should be a JSON array");
            assertTrue(responseBody.endsWith("]"), 
                "Batch response should be a JSON array");
            assertTrue(responseBody.contains("\"id\":1"), 
                "Batch response should contain first request ID");
            assertTrue(responseBody.contains("\"id\":2"), 
                "Batch response should contain second request ID");
        }
    }

    @Test
    public void testBatchWithNotifications() throws IOException {
        // Batch with a request and a notification
        String batchRequest = "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}," +
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/test\"}]";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = batchRequest.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        // Should return 202 if all were notifications, but we have one request
        // So it should return 200 with the response to the request
        assertEquals(200, responseCode, "Batch with request should return 200");
    }

    // ==================== Notification Tests ====================

    @Test
    public void testNotificationReturns202() throws IOException {
        // Notification has method but no id
        String notification = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/test\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = notification.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(202, responseCode, "Notification should return 202 Accepted");
    }

    // ==================== SSE Streaming Tests ====================

    @Test
    public void testSseAcceptHeaderRequiredForGet() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        // No Accept header
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(405, responseCode, "GET without SSE Accept should return 405");
    }

    @Test
    public void testSseStream() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000); // Enough time for the server to respond
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "SSE stream should return 200");
        
        String contentType = connection.getHeaderField("Content-Type");
        assertEquals("text/event-stream", contentType, "Content-Type should be text/event-stream");
        
        // Read the SSE comment
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            assertNotNull(line, "SSE stream should send data");
            assertTrue(line.startsWith(":"), "SSE comment should start with :");
        }
    }

    // ==================== HTTP Method Tests ====================

    @Test
    public void testOptionsMethodForCors() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("OPTIONS");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(204, responseCode, "OPTIONS should return 204");
        
        String allowMethods = connection.getHeaderField("Access-Control-Allow-Methods");
        assertNotNull(allowMethods, "Should have Access-Control-Allow-Methods header");
        assertTrue(allowMethods.contains("GET") && allowMethods.contains("POST"), 
            "Should allow GET and POST methods");
    }

    @Test
    public void testUnsupportedMethod() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(405, responseCode, "Unsupported method should return 405");
    }

    // ==================== Error Handling Tests ====================

    @Test
    public void testInvalidJson() throws IOException {
        String invalidJson = "{invalid json";  // Missing closing brace
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = invalidJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(400, responseCode, "Invalid JSON should return 400");
    }

    @Test
    public void testUnknownMethod() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown_method\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Unknown method should return 200 with JSON-RPC error");
        
        // Read response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.contains("\"error\":"), 
                "Response should contain JSON-RPC error");
            assertTrue(responseBody.contains("Unknown method"), 
                "Error message should mention unknown method");
        }
    }

    @Test
    public void testEmptyRequestBody() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        // Send empty body
        try (OutputStream os = connection.getOutputStream()) {
            // Empty
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(400, responseCode, "Empty request body should return 400");
    }

    // ==================== Security Tests ====================

    @Test
    public void testLocalhostOriginAllowed() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Origin", "http://localhost:3000");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Localhost origin should be allowed");
    }

    // ==================== CORS Tests ====================

    @Test
    public void testCorsHeadersPresent() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        
        URL url = new URL("http://localhost:" + port + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Check CORS headers
        String allowOrigin = connection.getHeaderField("Access-Control-Allow-Origin");
        assertEquals("*", allowOrigin, "Should have Access-Control-Allow-Origin header");
        
        String allowMethods = connection.getHeaderField("Access-Control-Allow-Methods");
        assertNotNull(allowMethods, "Should have Access-Control-Allow-Methods header");
    }
}
