package org.maunu.jakartaee.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for JakartaEeMcpServer.
 * Tests the MCP server using piped streams for direct in-process communication.
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpServerIntegrationTest {

    private ExecutorService executor;
    private JakartaEeMcpServer server;
    
    private PipedInputStream serverInput;
    private PipedOutputStream clientOutput;
    private PipedOutputStream serverOutput;
    private PipedInputStream clientInput;
    
    private BufferedReader clientReader;
    private BufferedWriter clientWriter;

    @BeforeEach
    public void setup() throws IOException {
        // Create piped streams for bidirectional communication
        serverInput = new PipedInputStream();
        clientOutput = new PipedOutputStream(serverInput);
        
        serverOutput = new PipedOutputStream();
        clientInput = new PipedInputStream(serverOutput);
        
        clientReader = new BufferedReader(new java.io.InputStreamReader(clientInput));
        clientWriter = new BufferedWriter(new java.io.OutputStreamWriter(clientOutput));
        
        // Create server with piped streams
        BufferedReader serverReader = new BufferedReader(new java.io.InputStreamReader(serverInput));
        PrintWriter serverPrintWriter = new PrintWriter(serverOutput, true);
        
        // Use the current thread's context classloader to ensure sources are accessible
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        server = new JakartaEeMcpServer(new PackageScanner(contextClassLoader), serverReader, serverPrintWriter);
        
        // Start server in a separate thread
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> server.start());
        
        // Give server time to start and send serverInfo
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Read and verify serverInfo message
        String serverInfo = clientReader.readLine();
        assertNotNull(serverInfo, "Server should send serverInfo on startup");
        assertTrue(serverInfo.contains("serverInfo"), "serverInfo message should contain serverInfo");
    }

    @AfterEach
    public void teardown() throws IOException, InterruptedException {
        // Close client writer to signal server to stop
        try {
            clientWriter.close();
        } catch (IOException e) {
            // Ignore
        }
        
        // Shutdown server thread
        if (executor != null) {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        
        // Close all streams
        try { clientReader.close(); } catch (IOException e) {}
        try { clientWriter.close(); } catch (IOException e) {}
        try { clientInput.close(); } catch (IOException e) {}
        try { clientOutput.close(); } catch (IOException e) {}
        try { serverInput.close(); } catch (IOException e) {}
        try { serverOutput.close(); } catch (IOException e) {}
    }

    private String readResponse() throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = clientReader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                response.append(line);
                break;
            }
        }
        return response.toString();
    }

    private void sendRequest(String method, Map<String, Object> params, long id) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        if (params != null && !params.isEmpty()) {
            request.put("params", params);
        }
        request.put("id", id);
        
        String json = formatJson(request);
        clientWriter.write(json);
        clientWriter.newLine();
        clientWriter.flush();
    }

    private String formatJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(formatValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson(value.toString()) + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return formatJson(map);
        } else if (value instanceof List) {
            return formatArray((List<?>) value);
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    @SuppressWarnings("unused")
    private String formatArray(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(formatValue(item));
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Test
    public void testServerStartsAndSendsServerInfo() {
        // Server info was already verified in setup()
        assertTrue(true, "Server started and sent serverInfo");
    }

    @Test
    public void testInitialize() throws IOException {
        Map<String, Object> params = new HashMap<>();
        sendRequest("initialize", params, 1L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to initialize");
        assertTrue(response.contains("protocolVersion"), "Response should contain protocolVersion");
        assertTrue(response.contains("capabilities"), "Response should contain capabilities");
        assertTrue(response.contains("\"id\":1"), "Response should have matching id");
    }

    @Test
    public void testToolsList() throws IOException {
        Map<String, Object> params = new HashMap<>();
        sendRequest("tools/list", params, 2L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/list");
        assertTrue(response.contains("tools"), "Response should contain tools array");
        assertTrue(response.contains("list_packages"), "Response should contain list_packages tool");
    }

    @Test
    public void testListPackagesWithJakartaServlet() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("root", "jakarta.servlet");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "list_packages");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 3L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/call");
        assertTrue(response.contains("content") || response.contains("text"), 
            "Response should contain content with package listing");
    }

    @Test
    public void testListPackagesWithJakarta() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("root", "jakarta");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "list_packages");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 4L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/call for jakarta root");
        assertTrue(response.contains("content") || response.contains("text"), 
            "Response should contain content");
    }

    @Test
    public void testListPackagesWithNonExistentPackage() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("root", "nonexistent.package.xyz");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "list_packages");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 5L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response even for non-existent package");
    }

    @Test
    public void testGetPackageDocWithJakartaJws() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("package", "jakarta.jws");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "get_package_doc");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 6L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/call for get_package_doc");
        assertTrue(response.contains("content") || response.contains("text"), 
            "Response should contain content");
    }

    @Test
    public void testGetPackageDocWithNonExistentPackage() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("package", "nonexistent.package.xyz");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "get_package_doc");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 7L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response even for non-existent package");
        assertTrue(response.contains("No package documentation found") || response.contains("nonexistent.package.xyz"),
            "Response should indicate package documentation not found");
    }

    @Test
    public void testListClassesWithJakartaServlet() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("package", "jakarta.servlet");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "list_classes");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 8L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/call for list_classes");
        assertTrue(response.contains("content") || response.contains("text"), 
            "Response should contain content");
    }

    @Test
    public void testListClassesWithNonExistentPackage() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("package", "nonexistent.package.xyz");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "list_classes");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 9L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response even for non-existent package");
        assertTrue(response.contains("No classes found") || response.contains("nonexistent.package.xyz"),
            "Response should indicate no classes found");
    }

    @Test
    public void testGetSourceCodeWithServlet() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("className", "jakarta.servlet.Servlet");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "get_source_code");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 12L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response to tools/call for get_source_code");
        assertTrue(response.contains("content") || response.contains("text"), 
            "Response should contain content");
    }

    @Test
    public void testGetSourceCodeWithNonExistentClass() throws IOException {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("className", "nonexistent.package.NonExistentClass");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "get_source_code");
        params.put("arguments", arguments);
        
        sendRequest("tools/call", params, 13L);
        
        String response = readResponse();
        assertNotNull(response, "Should receive response even for non-existent class");
        assertTrue(response.contains("No source code found") || response.contains("NonExistentClass"),
            "Response should indicate source code not found");
    }
}
