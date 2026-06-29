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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.maunu.jakartaee.mcp.McpConstants.*;

/**
 * Streamable HTTP MCP (Model Context Protocol) server for Jakarta EE.
 * Implements the Streamable HTTP transport as specified in MCP 2025-03-26.
 *
 * This server provides a single /mcp endpoint that supports both POST and GET methods:
 * - POST: Client sends JSON-RPC messages to the server
 * - GET: Client opens an SSE stream to receive server-initiated messages
 *
 * Key features:
 * - Single endpoint architecture (replaces multiple endpoints)
 * - Server-Sent Events (SSE) streaming support
 * - Session management via Mcp-Session-Id header
 * - Batch message handling (JSON-RPC arrays)
 * - Proper content negotiation via Accept headers
 * - Compliance with MCP 2025-03-26 specification
 *
 * @author Mikko Maunu
 */
public class StreamableHttpMcpServer {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ORIGIN = "Origin";
    
    private static final Logger LOGGER = Logger.getLogger(StreamableHttpMcpServer.class.getName());

    private final ToolRegistry toolRegistry;
    private HttpServer httpServer;
    private final int port;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);

    /**
     * Session state for Streamable HTTP transport.
     */
    private static class Session {
        final String sessionId;
        final String clientOrigin;
        volatile boolean isActive;
        volatile long lastEventId;
        
        Session(String sessionId, String clientOrigin) {
            this.sessionId = sessionId;
            this.clientOrigin = clientOrigin;
            this.isActive = true;
            this.lastEventId = 0;
        }
        
        void terminate() {
            this.isActive = false;
        }
    }

    /**
     * Creates a Streamable HTTP MCP server on default port 8080.
     */
    public StreamableHttpMcpServer() {
        this(new PackageScanner());
    }

    /**
     * Creates a Streamable HTTP MCP server with a custom package scanner.
     *
     * @param packageScanner the package scanner to use
     */
    public StreamableHttpMcpServer(PackageScanner packageScanner) {
        this(packageScanner, 8080);
    }

    /**
     * Creates a Streamable HTTP MCP server on a specified port.
     *
     * @param packageScanner the package scanner to use
     * @param port the port to listen on
     */
    public StreamableHttpMcpServer(PackageScanner packageScanner, int port) {
        this.toolRegistry = new ToolRegistry(packageScanner);
        this.port = port;
    }

    /**
     * Starts the Streamable HTTP MCP server.
     */
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext(MCP_ENDPOINT, new McpEndpointHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            
            LOGGER.log(Level.INFO, "Streamable HTTP MCP Server started on port {0}", port);
            LOGGER.log(Level.INFO, "Single endpoint: POST/GET {0}", MCP_ENDPOINT);
            LOGGER.log(Level.INFO, "Protocol version: {0}", STREAMABLE_PROTOCOL_VERSION);
            LOGGER.log(Level.INFO, "Supports: SSE streaming, session management, batch messages");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting HTTP server: " + e.getMessage(), e);
            throw new RuntimeException("Failed to start Streamable HTTP server", e);
        }
    }

    /**
     * Stops the HTTP server and terminates all sessions.
     */
    public void stop() {
        // Terminate all active sessions
        sessions.values().forEach(session -> session.terminate());
        sessions.clear();
        
        if (httpServer != null) {
            httpServer.stop(0);
            LOGGER.log(Level.INFO, "Streamable HTTP MCP Server stopped");
        }
    }

    /**
     * Creates a new session and returns the session ID.
     *
     * @param clientOrigin the Origin header from the client request
     * @return the new session ID
     */
    private String createSession(String clientOrigin) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId, clientOrigin);
        sessions.put(sessionId, session);
        LOGGER.log(Level.FINE, "Created new session: {0}", sessionId);
        return sessionId;
    }

    /**
     * Gets the session for a given session ID.
     *
     * @param sessionId the session ID
     * @return the session, or null if not found or expired
     */
    private Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Session session = sessions.get(sessionId);
        if (session != null && session.isActive) {
            return session;
        }
        return null;
    }

    /**
     * Terminates a session.
     *
     * @param sessionId the session ID to terminate
     */
    private void terminateSession(String sessionId) {
        if (sessionId != null) {
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.terminate();
                sessions.remove(sessionId);
                LOGGER.log(Level.FINE, "Terminated session: {0}", sessionId);
            }
        }
    }

    /**
     * Generates a unique event ID for SSE events.
     *
     * @return a unique event ID
     */
    private String generateEventId() {
        return String.valueOf(eventCounter.incrementAndGet());
    }

    /**
     * Validates the Origin header for security (prevents DNS rebinding attacks).
     *
     * @param origin the Origin header value
     * @return true if the origin is valid (localhost or null)
     */
    private boolean isValidOrigin(String origin) {
        if (origin == null) {
            return true; // Allow null origin for non-browser clients
        }
        // Allow localhost origins
        String lowerOrigin = origin.toLowerCase();
        return lowerOrigin.startsWith("http://localhost:") ||
               lowerOrigin.startsWith("http://127.0.0.1:") ||
               lowerOrigin.startsWith("https://localhost:") ||
               lowerOrigin.startsWith("https://127.0.0.1:");
    }

    /**
     * Extracts the session ID from the request headers.
     *
     * @param exchange the HTTP exchange
     * @return the session ID, or null if not present
     */
    private String extractSessionId(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst(HEADER_MCP_SESSION_ID);
    }

    /**
     * Extracts the Accept header from the request.
     *
     * @param exchange the HTTP exchange
     * @return the Accept header value, or null if not present
     */
    private String extractAcceptHeader(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst(HEADER_ACCEPT);
    }

    /**
     * Extracts the Origin header from the request.
     *
     * @param exchange the HTTP exchange
     * @return the Origin header value, or null if not present
     */
    private String extractOrigin(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst(HEADER_ORIGIN);
    }

    /**
     * Extracts the Last-Event-ID header from the request.
     *
     * @param exchange the HTTP exchange
     * @return the Last-Event-ID header value, or null if not present
     */
    private String extractLastEventId(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst(HEADER_LAST_EVENT_ID);
    }

    /**
     * Checks if the client accepts SSE streaming.
     *
     * @param acceptHeader the Accept header value
     * @return true if the client accepts text/event-stream
     */
    private boolean acceptsSse(String acceptHeader) {
        if (acceptHeader == null) {
            return false;
        }
        return acceptHeader.contains("text/event-stream");
    }

    /**
     * Checks if the client accepts JSON.
     *
     * @param acceptHeader the Accept header value
     * @return true if the client accepts application/json
     */
    private boolean acceptsJson(String acceptHeader) {
        if (acceptHeader == null) {
            return true; // Default to JSON
        }
        return acceptHeader.contains("application/json");
    }

    /**
     * Validates that a string is valid JSON.
     *
     * @param json the string to validate
     * @return true if valid JSON
     */
    private boolean isValidJson(String json) {
        json = json.trim();
        if (json.isEmpty()) {
            return false;
        }
        
        // Quick check: valid JSON must start with {, [, or be a primitive
        char first = json.charAt(0);
        char last = json.charAt(json.length() - 1);
        
        if (first == '{') {
            return last == '}';
        } else if (first == '[') {
            return last == ']';
        } else if (first == '"') {
            return last == '"';
        } else if (Character.isDigit(first) || first == '-' || first == 't' || first == 'f' || first == 'n') {
            // Number, true, false, null
            return true;
        }
        return false;
    }

    /**
     * Parses a JSON-RPC message or batch from a string.
     *
     * @param requestBody the request body string
     * @return a list of JSON-RPC messages (single message wrapped in a list)
     */
    @SuppressWarnings("unchecked")
    private List<Object> parseMessagesFromString(String requestBody) {
        requestBody = requestBody.trim();
        
        // First, validate it's valid JSON
        if (!isValidJson(requestBody)) {
            return List.of((Object) null);
        }
        
        // Check if it's a JSON array (batch)
        if (requestBody.startsWith("[") && requestBody.endsWith("]")) {
            List<Object> array = JsonUtils.parseJsonArray(requestBody);
            if (array.isEmpty() && !requestBody.equals("[]")) {
                // Parse failed for array
                return List.of((Object) null);
            }
            return array;
        }
        
        // Single message
        Map<String, Object> single = JsonUtils.parseJson(requestBody);
        if (single == null && !requestBody.isEmpty()) {
            // Parse failed for single message
            return List.of((Object) null);
        }
        return List.of(single);
    }

    /**
     * Extracts the request body as a string.
     *
     * @param exchange the HTTP exchange
     * @return the request body string
     * @throws IOException if the request body cannot be read
     */
    private String extractBody(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        return new String(requestBody, StandardCharsets.UTF_8);
    }

    /**
     * Sends a JSON response with the specified status code.
     *
     * @param exchange the HTTP exchange
     * @param statusCode the HTTP status code
     * @param response the response map to send
     * @throws IOException if the response cannot be sent
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        String jsonResponse = JsonUtils.formatValue(response);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", 
            "Content-Type, Accept, " + HEADER_MCP_SESSION_ID + ", " + HEADER_LAST_EVENT_ID);
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", 
            HEADER_MCP_SESSION_ID);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Sends an error response with the specified status code and message.
     *
     * @param exchange the HTTP exchange
     * @param statusCode the HTTP status code
     * @param message the error message
     * @throws IOException if the response cannot be sent
     */
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = toolRegistry.createErrorResponse(null, statusCode, message);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    /**
     * Sends an error response with the specified status code and JSON-RPC error.
     *
     * @param exchange the HTTP exchange
     * @param httpStatusCode the HTTP status code
     * @param jsonRpcErrorCode the JSON-RPC error code
     * @param message the error message
     * @param requestId the request ID (can be null)
     * @throws IOException if the response cannot be sent
     */
    private void sendJsonRpcError(HttpExchange exchange, int httpStatusCode, 
                                   int jsonRpcErrorCode, String message, Long requestId) throws IOException {
        Map<String, Object> errorResponse = toolRegistry.createErrorResponse(
            requestId, jsonRpcErrorCode, message);
        sendJsonResponse(exchange, httpStatusCode, errorResponse);
    }

    /**
     * Sends a 202 Accepted response for notifications-only requests.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if the response cannot be sent
     */
    private void sendAcceptedResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", HEADER_MCP_SESSION_ID);
        exchange.sendResponseHeaders(202, 0);
        // Close the response body to complete the response
        exchange.getResponseBody().close();
    }

    /**
     * Sends an SSE stream response.
     *
     * @param exchange the HTTP exchange
     * @param sessionId the session ID (can be null)
     * @param messages the list of JSON-RPC messages to send
     * @throws IOException if the SSE stream cannot be opened
     */
    private void sendSseStream(HttpExchange exchange, String sessionId, List<Object> messages) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_SSE);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", 
            "Cache-Control, " + HEADER_LAST_EVENT_ID);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        
        if (sessionId != null) {
            exchange.getResponseHeaders().set(HEADER_MCP_SESSION_ID, sessionId);
        }
        
        exchange.sendResponseHeaders(200, 0);
        
        try (OutputStream os = exchange.getResponseBody()) {
            for (Object message : messages) {
                String eventId = generateEventId();
                String jsonMessage = JsonUtils.formatValue(message);
                
                // SSE format: data: <json>\n\n
                String sseEvent = "id: " + eventId + "\n" +
                                 "data: " + jsonMessage + "\n\n";
                os.write(sseEvent.getBytes(StandardCharsets.UTF_8));
                os.flush();
                
                LOGGER.log(Level.FINE, "Sent SSE event {0}: {1}", new Object[]{eventId, message});
            }
            // Close the connection
        }
    }

    /**
     * Sends an SSE stream response for a single message.
     *
     * @param exchange the HTTP exchange
     * @param sessionId the session ID (can be null)
     * @param message the JSON-RPC message to send
     * @throws IOException if the SSE stream cannot be opened
     */
    private void sendSseStream(HttpExchange exchange, String sessionId, Object message) throws IOException {
        sendSseStream(exchange, sessionId, List.of(message));
    }

    /**
     * Handles a JSON-RPC message and returns the appropriate response(s).
     *
     * @param message the JSON-RPC message (can be request, notification, or response)
     * @param session the current session (can be null)
     * @return the response message(s) to send back
     */
    private Object handleMessage(Object message, Session session) {
        if (message == null) {
            return toolRegistry.createErrorResponse(null, -32700, "Parse error: null message");
        }
        
        if (!(message instanceof Map)) {
            return toolRegistry.createErrorResponse(null, -32700, "Parse error: expected JSON object");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap = (Map<String, Object>) message;
        
        // Check if it's a request, notification, or response
        boolean hasMethod = messageMap.containsKey("method");
        boolean hasId = messageMap.containsKey("id");
        boolean hasResult = messageMap.containsKey("result");
        boolean hasError = messageMap.containsKey("error");
        
        // If it's a response (has result or error but no method), just acknowledge
        if ((hasResult || hasError) && !hasMethod) {
            // This is a response from the client - we don't need to respond to responses
            return null;
        }
        
        // If it's a notification (has method but no id), return 202 Accepted
        if (hasMethod && !hasId) {
            // Notification - process but don't return a response
            handleNotification(messageMap);
            return null; // Will trigger 202 Accepted
        }
        
        // It's a request (has method and id)
        if (hasMethod && hasId) {
            Object idObj = messageMap.get("id");
            Long id = null;
            if (idObj != null) {
                if (idObj instanceof Number) {
                    id = ((Number) idObj).longValue();
                } else if (idObj instanceof String) {
                    try {
                        id = Long.parseLong((String) idObj);
                    } catch (NumberFormatException e) {
                        // Keep id as null for string IDs
                    }
                }
            }
            
            String method = messageMap.get("method").toString();
            return handleRequest(messageMap, id, session);
        }
        
        // Invalid message
        return toolRegistry.createErrorResponse(null, -32600, "Invalid request");
    }

    /**
     * Handles a JSON-RPC notification (message with method but no id).
     *
     * @param message the notification message
     */
    private void handleNotification(@SuppressWarnings("unused") Map<String, Object> message) {
        // Currently, we don't handle any server-initiated notifications
        // This is a placeholder for future server-to-client notifications
        LOGGER.log(Level.FINE, "Received notification: {0}", message);
    }

    /**
     * Handles a JSON-RPC request (message with method and id).
     *
     * @param message the request message
     * @param id the request ID
     * @param session the current session (can be null)
     * @return the response message
     */
    private Object handleRequest(Map<String, Object> message, Long id, Session session) {
        String method = message.get("method").toString();
        
        switch (method) {
            case "initialize":
                return handleInitialize(message, id, session);
            case "tools/list":
                return handleToolsList(id);
            case "tools/call":
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) message.get("params");
                return handleToolsCall(params, id);
            default:
                return toolRegistry.createErrorResponse(id, -32601, "Unknown method: " + method);
        }
    }

    /**
     * Handles the initialize request.
     *
     * @param message the initialize request
     * @param id the request ID
     * @param session the current session
     * @return the initialize response with session ID
     */
    private Object handleInitialize(Map<String, Object> message, Long id, Session session) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("protocolVersion", STREAMABLE_PROTOCOL_VERSION);
        result.put("capabilities", toolRegistry.getCapabilities());
        result.put("serverInfo", toolRegistry.getServerInfo());
        
        // Add session ID to the response
        String sessionId;
        if (session != null) {
            sessionId = session.sessionId;
        } else {
            // This shouldn't happen as initialize should create a session
            sessionId = createSession(extractOriginFromMessage(message));
        }
        result.put("sessionId", sessionId);
        
        return toolRegistry.createResponse(id, result);
    }

    /**
     * Extracts the client origin from an initialize message if available.
     *
     * @param message the initialize message
     * @return the client origin, or null if not present
     */
    private String extractOriginFromMessage(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.get("params");
        if (params != null) {
            Object clientInfo = params.get("clientInfo");
            if (clientInfo instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> clientInfoMap = (Map<String, Object>) clientInfo;
                Object origin = clientInfoMap.get("origin");
                if (origin instanceof String) {
                    return (String) origin;
                }
            }
        }
        return null;
    }

    /**
     * Handles the tools/list request.
     *
     * @param id the request ID
     * @return the tools list response
     */
    private Object handleToolsList(Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("tools", toolRegistry.getToolsAsMaps());
        return toolRegistry.createResponse(id, result);
    }

    /**
     * Handles the tools/call request.
     *
     * @param params the request parameters
     * @param id the request ID
     * @return the tool call response
     */
    @SuppressWarnings("unchecked")
    private Object handleToolsCall(Map<String, Object> params, Long id) {
        if (params == null) {
            return toolRegistry.createErrorResponse(id, -32600, "Missing params");
        }
        
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
     * Handles a batch of JSON-RPC messages.
     *
     * @param messages the list of messages
     * @param session the current session
     * @return the list of responses, or null if all were notifications
     */
    private List<Object> handleBatch(List<Object> messages, Session session) {
        java.util.List<Object> responses = new java.util.ArrayList<>();
        boolean hasRequests = false;
        
        for (Object message : messages) {
            Object response = handleMessage(message, session);
            if (response != null) {
                responses.add(response);
                hasRequests = true;
            }
        }
        
        // If all were notifications, return empty list to trigger 202 Accepted
        if (!hasRequests && !responses.isEmpty()) {
            return responses;
        } else if (hasRequests) {
            return responses;
        } else {
            return null; // All notifications - 202 Accepted
        }
    }

    /**
     * Main handler for the /mcp endpoint.
     * Handles both POST and GET methods according to the Streamable HTTP specification.
     */
    private class McpEndpointHandler implements HttpHandler {
        
        @Override
        @SuppressWarnings("unchecked")
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String clientOrigin = extractOrigin(exchange);
            
            // Security: Validate Origin header
            if (!isValidOrigin(clientOrigin)) {
                LOGGER.log(Level.WARNING, "Rejected request from invalid origin: {0}", clientOrigin);
                sendErrorResponse(exchange, 403, "Forbidden: Invalid origin");
                return;
            }
            
            // Handle OPTIONS for CORS preflight
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
                return;
            }
            
            // Handle DELETE for session termination
            if ("DELETE".equals(method)) {
                handleDelete(exchange);
                return;
            }
            
            // Handle GET for SSE stream
            if ("GET".equals(method)) {
                handleGet(exchange);
                return;
            }
            
            // Handle POST for JSON-RPC messages
            if ("POST".equals(method)) {
                handlePost(exchange);
                return;
            }
            
            // Method not allowed
            sendErrorResponse(exchange, 405, "Method not allowed. Use POST or GET for " + MCP_ENDPOINT);
        }
        
        /**
         * Handles OPTIONS request for CORS preflight.
         */
        private void handleOptions(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", 
                "Content-Type, Accept, " + HEADER_MCP_SESSION_ID + ", " + HEADER_LAST_EVENT_ID);
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
            exchange.sendResponseHeaders(204, 0);
        }
        
        /**
         * Handles DELETE request for session termination.
         */
        private void handleDelete(HttpExchange exchange) throws IOException {
            String sessionId = extractSessionId(exchange);
            
            if (sessionId == null) {
                sendErrorResponse(exchange, 400, "Missing " + HEADER_MCP_SESSION_ID + " header");
                return;
            }
            
            terminateSession(sessionId);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(204, 0);
            LOGGER.log(Level.INFO, "Session terminated: {0}", sessionId);
        }
        
        /**
         * Handles GET request for opening an SSE stream.
         */
        private void handleGet(HttpExchange exchange) throws IOException {
            String acceptHeader = extractAcceptHeader(exchange);
            
            // Client must accept text/event-stream
            if (!acceptsSse(acceptHeader)) {
                // If client doesn't accept SSE, return 405
                sendErrorResponse(exchange, 405, "SSE not accepted. Include 'text/event-stream' in Accept header");
                return;
            }
            
            String sessionId = extractSessionId(exchange);
            Session session = getSession(sessionId);
            
            // For GET requests without a session, we can't establish a session
            // According to spec, sessions are established via POST initialize
            if (session == null && sessionId != null) {
                // Session ID provided but not found
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(404, 0);
                return;
            }
            
            // Open SSE stream
            // For now, we don't send any initial messages on GET
            // The client will use POST for requests and GET for listening
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_SSE);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            
            if (sessionId != null) {
                exchange.getResponseHeaders().set(HEADER_MCP_SESSION_ID, sessionId);
            }
            
            exchange.sendResponseHeaders(200, 0);
            
            // For now, just close the connection immediately
            // In a full implementation, we would maintain a connection pool
            // and send events as they occur
            try (OutputStream os = exchange.getResponseBody()) {
                // Send a comment to indicate the connection is open
                // SSE comments start with ':'
                String comment = ": SSE stream opened\n\n";
                os.write(comment.getBytes(StandardCharsets.UTF_8));
                os.flush();
                
                // Keep the connection open briefly for demonstration
                // In production, this would be a long-lived connection
                Thread.sleep(1000);
            } catch (Exception e) {
                // Connection closed by client
                LOGGER.log(Level.FINE, "SSE connection closed");
            }
        }
        
        /**
         * Handles POST request for JSON-RPC messages.
         */
        private void handlePost(HttpExchange exchange) throws IOException {
            String acceptHeader = extractAcceptHeader(exchange);
            String sessionId = extractSessionId(exchange);
            String clientOrigin = extractOrigin(exchange);
            
            Session session = null;
            
            // Extract and validate session
            if (sessionId != null) {
                session = getSession(sessionId);
                if (session == null) {
                    sendErrorResponse(exchange, 404, "Session not found or expired");
                    return;
                }
                // Verify session origin matches request origin
                if (session.clientOrigin != null && !session.clientOrigin.equals(clientOrigin)) {
                    sendErrorResponse(exchange, 403, "Session origin mismatch");
                    return;
                }
            }
            
            // Parse the request body
            String requestBodyStr;
            try {
                requestBodyStr = extractBody(exchange);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error reading request body: " + e.getMessage(), e);
                sendErrorResponse(exchange, 400, "Error reading request body: " + e.getMessage());
                return;
            }
            
            if (requestBodyStr == null || requestBodyStr.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Empty request body");
                return;
            }
            
            // Quick validation: check if it's valid JSON structure
            if (!isValidJson(requestBodyStr)) {
                sendErrorResponse(exchange, 400, "Invalid JSON in request body");
                return;
            }
            
            List<Object> messages;
            try {
                messages = parseMessagesFromString(requestBodyStr);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error parsing request body: " + e.getMessage(), e);
                sendErrorResponse(exchange, 400, "Invalid JSON in request body: " + e.getMessage());
                return;
            }
            
            if (messages.isEmpty()) {
                sendErrorResponse(exchange, 400, "Empty request body");
                return;
            }
            
            // Check if any of the messages are null (parse failure)
            boolean hasNullMessage = false;
            for (Object msg : messages) {
                if (msg == null) {
                    hasNullMessage = true;
                    break;
                }
            }
            if (hasNullMessage) {
                sendErrorResponse(exchange, 400, "Invalid JSON in request body");
                return;
            }
            
            // Check if it's a batch (array) or single message
            boolean isBatch = messages.size() > 1;
            
            // Check if all messages are notifications (no id field)
            boolean allNotifications = true;
            boolean hasRequests = false;
            
            for (Object msg : messages) {
                if (msg instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msgMap = (Map<String, Object>) msg;
                    if (msgMap.containsKey("id") && msgMap.containsKey("method")) {
                        allNotifications = false;
                        hasRequests = true;
                    } else if (msgMap.containsKey("method") && !msgMap.containsKey("id")) {
                        // Notification
                    } else if ((msgMap.containsKey("result") || msgMap.containsKey("error")) && !msgMap.containsKey("method")) {
                        // Response from client - ignore for response purposes
                    }
                }
            }
            
            // Process messages
            if (isBatch) {
                // Handle batch
                Object batchResponse = handleBatch(messages, session);
                
                if (batchResponse == null) {
                    // All were notifications - return 202 Accepted
                    sendAcceptedResponse(exchange);
                    return;
                }
                
                if (batchResponse instanceof List) {
                    List<?> responseList = (List<?>) batchResponse;
                    
                    if (acceptsSse(acceptHeader)) {
                        // Send as SSE stream
                        String responseSessionId = sessionId;
                        if (responseSessionId == null) {
                            // Create a new session for this response
                            responseSessionId = createSession(clientOrigin);
                            if (sessionId == null) {
                                // This was an initialize request - add session ID to response
                                exchange.getResponseHeaders().set(HEADER_MCP_SESSION_ID, responseSessionId);
                            }
                        }
                        sendSseStream(exchange, responseSessionId, responseList);
                    } else {
                        // Send as JSON array
                        sendJsonResponse(exchange, 200, batchResponse);
                    }
                } else {
                    // Single response
                    if (acceptsSse(acceptHeader)) {
                        String responseSessionId = sessionId;
                        if (responseSessionId == null) {
                            responseSessionId = createSession(clientOrigin);
                            exchange.getResponseHeaders().set(HEADER_MCP_SESSION_ID, responseSessionId);
                        }
                        sendSseStream(exchange, responseSessionId, batchResponse);
                    } else {
                        sendJsonResponse(exchange, 200, batchResponse);
                    }
                }
            } else {
                // Single message
                Object message = messages.get(0);
                Object response = handleMessage(message, session);
                
                if (response == null) {
                    // This was a notification - return 202 Accepted
                    sendAcceptedResponse(exchange);
                    return;
                }
                
                // For initialize request, create a new session
                if (message instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) message;
                    if ("initialize".equals(messageMap.get("method")) && sessionId == null) {
                        sessionId = createSession(clientOrigin);
                        exchange.getResponseHeaders().set(HEADER_MCP_SESSION_ID, sessionId);
                        session = getSession(sessionId);
                        // Re-handle with session
                        response = handleMessage(message, session);
                    }
                }
                
                // Check if client accepts SSE
                if (acceptsSse(acceptHeader)) {
                    sendSseStream(exchange, sessionId, response);
                } else {
                    sendJsonResponse(exchange, 200, response);
                }
            }
        }
    }

    /**
     * Main entry point for the Streamable HTTP MCP server.
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
        
        StreamableHttpMcpServer server = new StreamableHttpMcpServer(new PackageScanner(), port);
        server.start();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        // Cannot change port after server is created
        throw new UnsupportedOperationException("Port cannot be changed after server creation");
    }
}
