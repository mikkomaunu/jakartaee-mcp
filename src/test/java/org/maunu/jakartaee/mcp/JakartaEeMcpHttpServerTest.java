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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the HTTP-based MCP server.
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpHttpServerTest {

    private JakartaEeMcpHttpServer server;
    private int port;

    @BeforeEach
    public void setUp() throws IOException {
        // Use a random port or a high port number to avoid conflicts
        port = 8081;
        server = new JakartaEeMcpHttpServer(new PackageScanner(), port);
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

    @Test
    public void testServerInfoEndpoint() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp/info");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Expected HTTP 200 OK");
        
        // Read response
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseBody = response.toString();
            assertTrue(responseBody.contains("\"protocolVersion\":\"1.0\""), 
                "Response should contain protocol version");
            assertTrue(responseBody.contains("\"name\":\"jakartaee-mcp\""), 
                "Response should contain server name");
        }
    }

    @Test
    public void testToolsListEndpoint() throws IOException {
        URL url = new URL("http://localhost:" + port + "/mcp/tools");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(200, responseCode, "Expected HTTP 200 OK");
        
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
    public void testToolsCallEndpoint() throws IOException {
        // Test calling the list_packages tool
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_packages\",\"arguments\":{\"root\":\"jakarta\"}}}";
        
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
        assertEquals(200, responseCode, "Expected HTTP 200 OK");
        
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
        }
    }

    @Test
    public void testInvalidMethod() throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"invalid_method\"}";
        
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
        assertEquals(200, responseCode, "Expected HTTP 200 OK (MCP errors are returned as JSON-RPC errors)");
        
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
                "Response should contain error for unknown method");
        }
    }

    @Test
    public void testWrongMethodOnEndpoint() throws IOException {
        // Test POST on GET-only endpoint
        URL url = new URL("http://localhost:" + port + "/mcp/info");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        assertEquals(405, responseCode, "Expected HTTP 405 Method Not Allowed");
    }
}
