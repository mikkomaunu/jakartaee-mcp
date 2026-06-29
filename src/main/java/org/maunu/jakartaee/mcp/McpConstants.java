package org.maunu.jakartaee.mcp;

/**
 * Shared constants for MCP (Model Context Protocol) servers.
 *
 * @author Mikko Maunu
 */
public final class McpConstants {

    public static final String PROTOCOL_VERSION = "1.0";
    public static final String STREAMABLE_PROTOCOL_VERSION = "2025-03-26";
    public static final String SERVER_NAME = "jakartaee-mcp";
    public static final String SERVER_VERSION = "0.0.1";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_SSE = "text/event-stream";
    public static final String HEADER_MCP_SESSION_ID = "Mcp-Session-Id";
    public static final String HEADER_LAST_EVENT_ID = "Last-Event-ID";

    // Tool names
    public static final String TOOL_LIST_PACKAGES = "list_packages";
    public static final String TOOL_GET_PACKAGE_DOC = "get_package_doc";
    public static final String TOOL_LIST_CLASSES = "list_classes";
    public static final String TOOL_GET_SOURCE_CODE = "get_source_code";

    // Error codes
    public static final int ERROR_PARSE_ERROR = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;

    private McpConstants() {
        // Utility class - prevent instantiation
    }
}
