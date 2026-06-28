package org.maunu.jakartaee.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP-based MCP (Model Context Protocol) server for Jakarta EE.
 * Provides tools to browse Jakarta EE packages and classes from extracted sources.
 * 
 * This server exposes HTTP endpoints for MCP protocol communication:
 * - POST /mcp - Handle MCP JSON-RPC requests
 * - GET /mcp/info - Get server info
 * - GET /mcp/tools - List available tools
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpHttpServer {

    private static final String PROTOCOL_VERSION = "1.0";
    private static final String SERVER_NAME = "jakartaee-mcp";
    private static final String SERVER_VERSION = "0.0.1";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final PackageScanner packageScanner;
    private HttpServer httpServer;
    private int port;

    public JakartaEeMcpHttpServer() {
        this(new PackageScanner());
    }

    public JakartaEeMcpHttpServer(PackageScanner packageScanner) {
        this(packageScanner, 8080);
    }

    public JakartaEeMcpHttpServer(PackageScanner packageScanner, int port) {
        this.packageScanner = packageScanner;
        this.port = port;
    }

    /**
     * Starts the HTTP-based MCP server.
     */
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/mcp", new McpHandler());
            httpServer.createContext("/mcp/info", new InfoHandler());
            httpServer.createContext("/mcp/tools", new ToolsHandler());
            httpServer.createContext("/mcp/tools/call", new ToolsCallHandler());
            httpServer.setExecutor(Executors.newSingleThreadExecutor());
            httpServer.start();
            
            System.out.println("HTTP MCP Server started on port " + port);
            System.out.println("Available endpoints:");
            System.out.println("  POST /mcp - Handle MCP JSON-RPC requests");
            System.out.println("  GET /mcp/info - Get server info");
            System.out.println("  GET /mcp/tools - List available tools");
            System.out.println("  POST /mcp/tools/call - Call a tool");
        } catch (IOException e) {
            System.err.println("Error starting HTTP server: " + e.getMessage());
            throw new RuntimeException("Failed to start HTTP server", e);
        }
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("HTTP MCP Server stopped");
        }
    }

    private Map<String, Object> getCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();
        
        List<Map<String, Object>> toolList = new ArrayList<>();
        
        // list_packages tool
        Map<String, Object> listPackagesTool = new HashMap<>();
        listPackagesTool.put("name", "list_packages");
        listPackagesTool.put("description", "List all child packages under a given root package in Jakarta EE");
        
        Map<String, Object> listPackagesParams = new HashMap<>();
        Map<String, Object> rootParam = new HashMap<>();
        rootParam.put("type", "string");
        rootParam.put("description", "The root package name (e.g., 'jakarta.servlet')");
        listPackagesParams.put("root", rootParam);
        listPackagesTool.put("inputSchema", Map.of("type", "object", "properties", listPackagesParams));
        
        toolList.add(listPackagesTool);

        // get_package_doc tool
        Map<String, Object> getPackageDocTool = new HashMap<>();
        getPackageDocTool.put("name", "get_package_doc");
        getPackageDocTool.put("description", "Get the package documentation (package-info.java or package.html) for a given package in Jakarta EE");
        
        Map<String, Object> getPackageDocParams = new HashMap<>();
        Map<String, Object> packageParam = new HashMap<>();
        packageParam.put("type", "string");
        packageParam.put("description", "The package name (e.g., 'jakarta.servlet')");
        getPackageDocParams.put("package", packageParam);
        getPackageDocTool.put("inputSchema", Map.of("type", "object", "properties", getPackageDocParams));
        
        toolList.add(getPackageDocTool);

        // list_classes tool
        Map<String, Object> listClassesTool = new HashMap<>();
        listClassesTool.put("name", "list_classes");
        listClassesTool.put("description", "List all classes in a given package in Jakarta EE");
        
        Map<String, Object> listClassesParams = new HashMap<>();
        Map<String, Object> packageParam2 = new HashMap<>();
        packageParam2.put("type", "string");
        packageParam2.put("description", "The package name (e.g., 'jakarta.servlet')");
        listClassesParams.put("package", packageParam2);
        listClassesTool.put("inputSchema", Map.of("type", "object", "properties", listClassesParams));
        
        toolList.add(listClassesTool);

        // get_source_code tool
        Map<String, Object> getSourceCodeTool = new HashMap<>();
        getSourceCodeTool.put("name", "get_source_code");
        getSourceCodeTool.put("description", "Get the source code for a given class in Jakarta EE");
        
        Map<String, Object> getSourceCodeParams = new HashMap<>();
        Map<String, Object> classNameParam = new HashMap<>();
        classNameParam.put("type", "string");
        classNameParam.put("description", "The fully qualified class name (e.g., 'jakarta.servlet.Servlet')");
        getSourceCodeParams.put("className", classNameParam);
        getSourceCodeTool.put("inputSchema", Map.of("type", "object", "properties", getSourceCodeParams));
        
        toolList.add(getSourceCodeTool);
        
        tools.put("list", toolList);
        capabilities.put("tools", tools);
        
        return capabilities;
    }

    private Map<String, Object> getServerInfo() {
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return serverInfo;
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Map<String, Object> response) throws IOException {
        String jsonResponse = formatJson(response);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    private String extractBody(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        return new String(requestBody, StandardCharsets.UTF_8);
    }

    private Map<String, Object> parseQueryParams(String query) {
        Map<String, Object> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    // Ignore
                }
            }
        }
        return params;
    }

    // Handler for POST /mcp - Main MCP JSON-RPC endpoint
    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST for MCP requests.");
                return;
            }

            String requestBody = extractBody(exchange);
            Map<String, Object> message = parseJson(requestBody);
            
            if (message == null) {
                sendErrorResponse(exchange, 400, "Invalid JSON in request body");
                return;
            }

            Map<String, Object> response = handleMessage(message);
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for GET /mcp/info - Server info endpoint
    private class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET for info requests.");
                return;
            }

            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("protocolVersion", PROTOCOL_VERSION);
            serverInfo.put("capabilities", getCapabilities());
            serverInfo.put("serverInfo", getServerInfo());
            
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("result", serverInfo);
            
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for GET /mcp/tools - List tools endpoint
    private class ToolsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET for tools list.");
                return;
            }

            List<Map<String, Object>> tools = new ArrayList<>();
            
            // list_packages tool
            Map<String, Object> listPackagesTool = new HashMap<>();
            listPackagesTool.put("name", "list_packages");
            listPackagesTool.put("description", "List all child packages under a given root package in Jakarta EE");
            
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> rootProperty = new HashMap<>();
            rootProperty.put("type", "string");
            rootProperty.put("description", "The root package name");
            properties.put("root", rootProperty);
            
            inputSchema.put("properties", properties);
            listPackagesTool.put("inputSchema", inputSchema);
            
            tools.add(listPackagesTool);

            // get_package_doc tool
            Map<String, Object> getPackageDocTool = new HashMap<>();
            getPackageDocTool.put("name", "get_package_doc");
            getPackageDocTool.put("description", "Get the package documentation (package-info.java or package.html) for a given package in Jakarta EE");
            
            Map<String, Object> getPackageDocInputSchema = new HashMap<>();
            getPackageDocInputSchema.put("type", "object");
            
            Map<String, Object> getPackageDocProperties = new HashMap<>();
            Map<String, Object> packageProperty = new HashMap<>();
            packageProperty.put("type", "string");
            packageProperty.put("description", "The package name");
            getPackageDocProperties.put("package", packageProperty);
            
            getPackageDocInputSchema.put("properties", getPackageDocProperties);
            getPackageDocTool.put("inputSchema", getPackageDocInputSchema);
            
            tools.add(getPackageDocTool);

            // list_classes tool
            Map<String, Object> listClassesTool = new HashMap<>();
            listClassesTool.put("name", "list_classes");
            listClassesTool.put("description", "List all classes in a given package in Jakarta EE");
            
            Map<String, Object> listClassesInputSchema = new HashMap<>();
            listClassesInputSchema.put("type", "object");
            
            Map<String, Object> listClassesProperties = new HashMap<>();
            Map<String, Object> packageProperty2 = new HashMap<>();
            packageProperty2.put("type", "string");
            packageProperty2.put("description", "The package name");
            listClassesProperties.put("package", packageProperty2);
            
            listClassesInputSchema.put("properties", listClassesProperties);
            listClassesTool.put("inputSchema", listClassesInputSchema);
            
            tools.add(listClassesTool);

            // get_source_code tool
            Map<String, Object> getSourceCodeTool = new HashMap<>();
            getSourceCodeTool.put("name", "get_source_code");
            getSourceCodeTool.put("description", "Get the source code for a given class in Jakarta EE");
            
            Map<String, Object> getSourceCodeInputSchema = new HashMap<>();
            getSourceCodeInputSchema.put("type", "object");
            
            Map<String, Object> getSourceCodeProperties = new HashMap<>();
            Map<String, Object> classNameProperty = new HashMap<>();
            classNameProperty.put("type", "string");
            classNameProperty.put("description", "The fully qualified class name");
            getSourceCodeProperties.put("className", classNameProperty);
            
            getSourceCodeInputSchema.put("properties", getSourceCodeProperties);
            getSourceCodeTool.put("inputSchema", getSourceCodeInputSchema);
            
            tools.add(getSourceCodeTool);

            Map<String, Object> result = new HashMap<>();
            result.put("tools", tools);
            
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("result", result);
            
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for POST /mcp/tools/call - Call tool endpoint
    private class ToolsCallHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST for tool calls.");
                return;
            }

            String requestBody = extractBody(exchange);
            Map<String, Object> message = parseJson(requestBody);
            
            if (message == null) {
                sendErrorResponse(exchange, 400, "Invalid JSON in request body");
                return;
            }

            Map<String, Object> response = handleToolsCall(message);
            sendJsonResponse(exchange, 200, response);
        }
    }

    private Map<String, Object> handleMessage(Map<String, Object> message) {
        Object methodObj = message.get("method");
        if (methodObj == null) {
            return createErrorResponse(null, -32600, "Missing method");
        }

        String method = methodObj.toString();
        Object idObj = message.get("id");
        Long id = idObj != null ? ((Number) idObj).longValue() : null;

        switch (method) {
            case "initialize":
                return handleInitialize(id);
            case "tools/list":
                return handleToolsList(id);
            case "tools/call":
                return handleToolsCall(message, id);
            default:
                return createErrorResponse(id, -32601, "Unknown method: " + method);
        }
    }

    private Map<String, Object> handleMessageForHttp(Map<String, Object> message) {
        Object idObj = message.get("id");
        Long id = idObj != null ? ((Number) idObj).longValue() : null;
        
        Object methodObj = message.get("method");
        if (methodObj == null) {
            return createErrorResponse(id, -32600, "Missing method");
        }

        String method = methodObj.toString();
        
        switch (method) {
            case "initialize":
                return handleInitialize(id);
            case "tools/list":
                return handleToolsList(id);
            case "tools/call":
                return handleToolsCall(message, id);
            default:
                return createErrorResponse(id, -32601, "Unknown method: " + method);
        }
    }

    private Map<String, Object> handleInitialize(Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", getCapabilities());
        result.put("serverInfo", getServerInfo());
        return createResponse(id, result);
    }

    private Map<String, Object> handleToolsList(Long id) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        Map<String, Object> listPackagesTool = new HashMap<>();
        listPackagesTool.put("name", "list_packages");
        listPackagesTool.put("description", "List all child packages under a given root package in Jakarta EE");
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> rootProperty = new HashMap<>();
        rootProperty.put("type", "string");
        rootProperty.put("description", "The root package name");
        properties.put("root", rootProperty);
        
        inputSchema.put("properties", properties);
        listPackagesTool.put("inputSchema", inputSchema);
        
        tools.add(listPackagesTool);

        // get_package_doc tool
        Map<String, Object> getPackageDocTool = new HashMap<>();
        getPackageDocTool.put("name", "get_package_doc");
        getPackageDocTool.put("description", "Get the package documentation (package-info.java or package.html) for a given package in Jakarta EE");
        
        Map<String, Object> getPackageDocInputSchema = new HashMap<>();
        getPackageDocInputSchema.put("type", "object");
        
        Map<String, Object> getPackageDocProperties = new HashMap<>();
        Map<String, Object> packageProperty = new HashMap<>();
        packageProperty.put("type", "string");
        packageProperty.put("description", "The package name");
        getPackageDocProperties.put("package", packageProperty);
        
        getPackageDocInputSchema.put("properties", getPackageDocProperties);
        getPackageDocTool.put("inputSchema", getPackageDocInputSchema);
        
        tools.add(getPackageDocTool);

        // list_classes tool
        Map<String, Object> listClassesTool = new HashMap<>();
        listClassesTool.put("name", "list_classes");
        listClassesTool.put("description", "List all classes in a given package in Jakarta EE");
        
        Map<String, Object> listClassesInputSchema = new HashMap<>();
        listClassesInputSchema.put("type", "object");
        
        Map<String, Object> listClassesProperties = new HashMap<>();
        Map<String, Object> packageProperty2 = new HashMap<>();
        packageProperty2.put("type", "string");
        packageProperty2.put("description", "The package name");
        listClassesProperties.put("package", packageProperty2);
        
        listClassesInputSchema.put("properties", listClassesProperties);
        listClassesTool.put("inputSchema", listClassesInputSchema);
        
        tools.add(listClassesTool);

        // get_source_code tool
        Map<String, Object> getSourceCodeToolList = new HashMap<>();
        getSourceCodeToolList.put("name", "get_source_code");
        getSourceCodeToolList.put("description", "Get the source code for a given class in Jakarta EE");
        
        Map<String, Object> getSourceCodeInputSchema = new HashMap<>();
        getSourceCodeInputSchema.put("type", "object");
        
        Map<String, Object> getSourceCodeProperties = new HashMap<>();
        Map<String, Object> classNameProperty = new HashMap<>();
        classNameProperty.put("type", "string");
        classNameProperty.put("description", "The fully qualified class name");
        getSourceCodeProperties.put("className", classNameProperty);
        
        getSourceCodeInputSchema.put("properties", getSourceCodeProperties);
        getSourceCodeToolList.put("inputSchema", getSourceCodeInputSchema);
        
        tools.add(getSourceCodeToolList);
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return createResponse(id, result);
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> message) {
        // For HTTP endpoint, we expect the message to have the tool call parameters
        Object paramsObj = message.get("params");
        Long id = null;
        
        if (message.containsKey("id")) {
            Object idObj = message.get("id");
            id = idObj != null ? ((Number) idObj).longValue() : null;
        }
        
        return handleToolsCallInternal(paramsObj, id);
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> message, Long id) {
        Object paramsObj = message.get("params");
        return handleToolsCallInternal(paramsObj, id);
    }

    private Map<String, Object> handleToolsCallInternal(Object paramsObj, Long id) {
        if (paramsObj == null || !(paramsObj instanceof Map)) {
            return createErrorResponse(id, -32600, "Missing params");
        }

        Map<String, Object> params = (Map<String, Object>) paramsObj;
        Object nameObj = params.get("name");
        if (nameObj == null) {
            return createErrorResponse(id, -32600, "Missing tool name");
        }

        String name = nameObj.toString();
        Object argumentsObj = params.get("arguments");
        Map<String, Object> arguments = null;
        if (argumentsObj instanceof Map) {
            arguments = (Map<String, Object>) argumentsObj;
        }

        if ("list_packages".equals(name)) {
            return handleListPackagesTool(arguments, id);
        } else if ("get_package_doc".equals(name)) {
            return handleGetPackageDocTool(arguments, id);
        } else if ("list_classes".equals(name)) {
            return handleListClassesTool(arguments, id);
        } else if ("get_source_code".equals(name)) {
            return handleGetSourceCodeTool(arguments, id);
        } else {
            return createErrorResponse(id, -32601, "Unknown tool: " + name);
        }
    }

    private Map<String, Object> handleListPackagesTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("root")) {
            return createErrorResponse(id, -32602, "Missing required argument: root");
        }

        String root = arguments.get("root").toString();
        List<String> packages = packageScanner.getPackages(root);

        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", String.join("\n", packages));
        contentList.add(textContent);
        
        result.put("content", contentList);
        return createResponse(id, result);
    }

    private Map<String, Object> handleListClassesTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("package")) {
            return createErrorResponse(id, -32602, "Missing required argument: package");
        }

        String packageName = arguments.get("package").toString();
        List<String> classes = packageScanner.getClasses(packageName);

        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        if (classes != null && !classes.isEmpty()) {
            textContent.put("text", String.join("\n", classes));
        } else {
            textContent.put("text", "No classes found for package: " + packageName);
        }
        contentList.add(textContent);
        
        result.put("content", contentList);
        return createResponse(id, result);
    }

    private Map<String, Object> handleGetSourceCodeTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("className")) {
            return createErrorResponse(id, -32602, "Missing required argument: className");
        }

        String className = arguments.get("className").toString();
        String sourceCode = packageScanner.getSourceCode(className);

        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        if (sourceCode != null && !sourceCode.isEmpty()) {
            textContent.put("text", sourceCode);
        } else {
            textContent.put("text", "No source code found for class: " + className);
        }
        contentList.add(textContent);
        
        result.put("content", contentList);
        return createResponse(id, result);
    }

    private Map<String, Object> handleGetPackageDocTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("package")) {
            return createErrorResponse(id, -32602, "Missing required argument: package");
        }

        String packageName = arguments.get("package").toString();
        Map.Entry<String, String> packageDoc = packageScanner.getPackageDoc(packageName);

        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        if (packageDoc != null && !packageDoc.getKey().isEmpty()) {
            textContent.put("text", "Found: " + packageDoc.getValue() + "\n\n" + packageDoc.getKey());
        } else {
            textContent.put("text", "No package documentation found for package: " + packageName);
        }
        contentList.add(textContent);
        
        result.put("content", contentList);
        return createResponse(id, result);
    }

    private Map<String, Object> createResponse(Long id, Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("result", result);
        return response;
    }

    private Map<String, Object> createErrorResponse(Long id, int code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("error", error);
        return response;
    }

    // JSON Parsing methods (same as original)
    private Map<String, Object> parseJson(String json) {
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            return parseObject(json.substring(1, json.length() - 1));
        }
        return null;
    }

    private Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new HashMap<>();
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                int end = json.indexOf('"', i + 1);
                if (end == -1) break;
                String key = json.substring(i + 1, end);
                i = end + 1;
                
                while (i < json.length() && json.charAt(i) != ':') i++;
                if (i >= json.length()) break;
                i++;
                
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                if (i >= json.length()) break;
                
                Object value = parseValue(json, i);
                if (value != null) {
                    map.put(key, value);
                    i = findEnd(json, i);
                }
            } else if (c == ',' || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                break;
            }
        }
        return map;
    }

    private Object parseValue(String json, int start) {
        char c = json.charAt(start);
        if (c == '"') {
            int end = json.indexOf('"', start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else if (c == '{') {
            int braceCount = 1;
            int end = start + 1;
            while (end < json.length() && braceCount > 0) {
                if (json.charAt(end) == '{') braceCount++;
                else if (json.charAt(end) == '}') braceCount--;
                end++;
            }
            return parseObject(json.substring(start + 1, end - 1));
        } else if (c == '[') {
            return parseArray(json, start);
        } else if (Character.isDigit(c) || c == '-') {
            return parseNumber(json, start);
        } else if (json.substring(start, Math.min(start + 4, json.length())).equals("true")) {
            return true;
        } else if (json.substring(start, Math.min(start + 5, json.length())).equals("false")) {
            return false;
        } else if (json.substring(start, Math.min(start + 4, json.length())).equals("null")) {
            return null;
        }
        return null;
    }

    private List<Object> parseArray(String json, int start) {
        List<Object> list = new ArrayList<>();
        int bracketCount = 1;
        int i = start + 1;
        int valueStart = i;
        while (i < json.length() && bracketCount > 0) {
            char c = json.charAt(i);
            if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
            else if (c == ',' && bracketCount == 1) {
                Object value = parseValue(json, valueStart);
                if (value != null) {
                    list.add(value);
                }
                valueStart = i + 1;
            }
            i++;
        }
        if (valueStart < i - 1) {
            Object value = parseValue(json, valueStart);
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private Number parseNumber(String json, int start) {
        int end = start;
        boolean isDouble = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '.' && !isDouble) {
                isDouble = true;
            } else if (!Character.isDigit(c) && c != '-' && c != '+' && c != 'e' && c != 'E') {
                break;
            }
            end++;
        }
        String numStr = json.substring(start, end);
        if (isDouble) {
            return Double.parseDouble(numStr);
        }
        return Long.parseLong(numStr);
    }

    private int findEnd(String json, int start) {
        char c = json.charAt(start);
        if (c == '"') {
            int end = json.indexOf('"', start + 1);
            return end != -1 ? end + 1 : json.length();
        } else if (c == '{') {
            int braceCount = 1;
            int i = start + 1;
            while (i < json.length() && braceCount > 0) {
                if (json.charAt(i) == '{') braceCount++;
                else if (json.charAt(i) == '}') braceCount--;
                i++;
            }
            return i;
        } else if (c == '[') {
            int bracketCount = 1;
            int i = start + 1;
            while (i < json.length() && bracketCount > 0) {
                if (json.charAt(i) == '[') bracketCount++;
                else if (json.charAt(i) == ']') bracketCount--;
                i++;
            }
            return i;
        }
        return start + 1;
    }

    // JSON Formatting methods (same as original)
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
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Main entry point for the HTTP MCP server.
     */
    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }
        
        JakartaEeMcpHttpServer server = new JakartaEeMcpHttpServer(new PackageScanner(), port);
        server.start();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
