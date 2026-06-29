package org.maunu.jakartaee.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based MCP (Model Context Protocol) server for Jakarta EE.
 * Provides tools to browse Jakarta EE packages and classes from extracted sources.
 *
 * This server exposes HTTP endpoints for MCP protocol communication:
 * - POST /mcp - Handle MCP JSON-RPC requests
 * - GET /mcp/info - Get server info
 * - GET /mcp/tools - List available tools
 * - POST /mcp/tools/call - Call a tool
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpHttpServer {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Logger LOGGER = Logger.getLogger(JakartaEeMcpHttpServer.class.getName());

    private final ToolRegistry toolRegistry;
    private HttpServer httpServer;
    private int port;

    public JakartaEeMcpHttpServer() {
        this(new PackageScanner());
    }

    public JakartaEeMcpHttpServer(PackageScanner packageScanner) {
        this(packageScanner, 8080);
    }

    public JakartaEeMcpHttpServer(PackageScanner packageScanner, int port) {
        this.toolRegistry = new ToolRegistry(packageScanner);
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
            
            LOGGER.log(Level.INFO, "HTTP MCP Server started on port {0}", port);
            LOGGER.log(Level.INFO, "Available endpoints:");
            LOGGER.log(Level.INFO, "  POST /mcp - Handle MCP JSON-RPC requests");
            LOGGER.log(Level.INFO, "  GET /mcp/info - Get server info");
            LOGGER.log(Level.INFO, "  GET /mcp/tools - List available tools");
            LOGGER.log(Level.INFO, "  POST /mcp/tools/call - Call a tool");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting HTTP server: " + e.getMessage(), e);
            throw new RuntimeException("Failed to start HTTP server", e);
        }
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            LOGGER.log(Level.INFO, "HTTP MCP Server stopped");
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Map<String, Object> response) throws IOException {
        String jsonResponse = JsonUtils.formatJson(response);
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
        Map<String, Object> errorResponse = toolRegistry.createErrorResponse(null, statusCode, message);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    private String extractBody(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        return new String(requestBody, StandardCharsets.UTF_8);
    }

    private Map<String, Object> parseRequestBody(HttpExchange exchange) throws IOException {
        String requestBody = extractBody(exchange);
        return JsonUtils.parseJson(requestBody);
    }

    // Base handler class to reduce duplication
    private abstract class BaseHandler implements HttpHandler {
        protected final String expectedMethod;
        protected final String errorMessage;

        protected BaseHandler(String expectedMethod, String errorMessage) {
            this.expectedMethod = expectedMethod;
            this.errorMessage = errorMessage;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!expectedMethod.equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, errorMessage);
                return;
            }
            handleRequest(exchange);
        }

        protected abstract void handleRequest(HttpExchange exchange) throws IOException;
    }

    // Handler for POST /mcp - Main MCP JSON-RPC endpoint
    private class McpHandler extends BaseHandler {
        public McpHandler() {
            super("POST", "Method not allowed. Use POST for MCP requests.");
        }

        @Override
        protected void handleRequest(HttpExchange exchange) throws IOException {
            Map<String, Object> message = parseRequestBody(exchange);
            
            if (message == null) {
                sendErrorResponse(exchange, 400, "Invalid JSON in request body");
                return;
            }

            Map<String, Object> response = handleMessage(message);
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for GET /mcp/info - Server info endpoint
    private class InfoHandler extends BaseHandler {
        public InfoHandler() {
            super("GET", "Method not allowed. Use GET for info requests.");
        }

        @Override
        protected void handleRequest(HttpExchange exchange) throws IOException {
            Map<String, Object> serverInfo = new java.util.HashMap<>();
            serverInfo.put("protocolVersion", toolRegistry.getProtocolVersion());
            serverInfo.put("capabilities", toolRegistry.getCapabilities());
            serverInfo.put("serverInfo", toolRegistry.getServerInfo());
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("result", serverInfo);
            
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for GET /mcp/tools - List tools endpoint
    private class ToolsHandler extends BaseHandler {
        public ToolsHandler() {
            super("GET", "Method not allowed. Use GET for tools list.");
        }

        @Override
        protected void handleRequest(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> tools = toolRegistry.getToolsAsMaps();
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("tools", tools);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("result", result);
            
            sendJsonResponse(exchange, 200, response);
        }
    }

    // Handler for POST /mcp/tools/call - Call tool endpoint
    private class ToolsCallHandler extends BaseHandler {
        public ToolsCallHandler() {
            super("POST", "Method not allowed. Use POST for tool calls.");
        }

        @Override
        protected void handleRequest(HttpExchange exchange) throws IOException {
            Map<String, Object> message = parseRequestBody(exchange);
            
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
            return toolRegistry.createErrorResponse(null, -32600, "Missing method");
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
                return toolRegistry.createErrorResponse(id, -32601, "Unknown method: " + method);
        }
    }

    private Map<String, Object> handleInitialize(Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("protocolVersion", toolRegistry.getProtocolVersion());
        result.put("capabilities", toolRegistry.getCapabilities());
        result.put("serverInfo", toolRegistry.getServerInfo());
        return toolRegistry.createResponse(id, result);
    }

    private Map<String, Object> handleToolsList(Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("tools", toolRegistry.getToolsAsMaps());
        return toolRegistry.createResponse(id, result);
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
            return toolRegistry.createErrorResponse(id, -32600, "Missing params");
        }

        Map<String, Object> params = (Map<String, Object>) paramsObj;
        Object nameObj = params.get("name");
        if (nameObj == null) {
            return toolRegistry.createErrorResponse(id, -32600, "Missing tool name");
        }

        String name = nameObj.toString();
        Object argumentsObj = params.get("arguments");
        Map<String, Object> arguments = null;
        if (argumentsObj instanceof Map) {
            arguments = (Map<String, Object>) argumentsObj;
        }

        return toolRegistry.handleToolCall(name, arguments, id);
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