package org.maunu.jakartaee.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central registry for MCP tools in Jakarta EE MCP server.
 * Eliminates duplication by providing a single source of truth for tool definitions.
 *
 * @author Mikko Maunu
 */
public class ToolRegistry {

    private static final Logger LOGGER = Logger.getLogger(ToolRegistry.class.getName());
    
    private final PackageScanner packageScanner;
    private final List<ToolDefinition> tools;

    /**
     * Creates a new ToolRegistry with a default PackageScanner.
     */
    public ToolRegistry() {
        this(new PackageScanner());
    }

    /**
     * Creates a new ToolRegistry with the specified PackageScanner.
     *
     * @param packageScanner the package scanner to use for tool operations
     */
    public ToolRegistry(PackageScanner packageScanner) {
        this.packageScanner = packageScanner;
        this.tools = createDefaultTools();
    }

    /**
     * Creates the default set of tools for Jakarta EE MCP.
     *
     * @return list of default ToolDefinition objects
     */
    private List<ToolDefinition> createDefaultTools() {
        List<ToolDefinition> toolList = new ArrayList<>();

        // list_packages tool
        toolList.add(ToolDefinition.createWithStringParam(
            "list_packages",
            "List all child packages under a given root package in Jakarta EE",
            "root",
            "The root package name (e.g., 'jakarta.servlet')"
        ));

        // get_package_doc tool
        toolList.add(ToolDefinition.createWithStringParam(
            "get_package_doc",
            "Get the package documentation (package-info.java or package.html) for a given package in Jakarta EE",
            "package",
            "The package name (e.g., 'jakarta.servlet')"
        ));

        // list_classes tool
        toolList.add(ToolDefinition.createWithStringParam(
            "list_classes",
            "List all classes in a given package in Jakarta EE",
            "package",
            "The package name (e.g., 'jakarta.servlet')"
        ));

        // get_source_code tool
        toolList.add(ToolDefinition.createWithStringParam(
            "get_source_code",
            "Get the source code for a given class in Jakarta EE",
            "className",
            "The fully qualified class name (e.g., 'jakarta.servlet.Servlet')"
        ));

        return toolList;
    }

    /**
     * Gets all registered tools as a list of Map objects for MCP protocol.
     *
     * @return list of tool definitions as Maps
     */
    public List<Map<String, Object>> getToolsAsMaps() {
        List<Map<String, Object>> toolMaps = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            toolMaps.add(tool.toMap());
        }
        return toolMaps;
    }

    /**
     * Gets the capabilities object for MCP protocol.
     *
     * @return capabilities Map containing tools
     */
    public Map<String, Object> getCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> toolsMap = new HashMap<>();
        toolsMap.put("list", getToolsAsMaps());
        capabilities.put("tools", toolsMap);
        return capabilities;
    }

    /**
     * Gets the PackageScanner used by this registry.
     *
     * @return the PackageScanner instance
     */
    public PackageScanner getPackageScanner() {
        return packageScanner;
    }

    /**
     * Handles a tool call by name and arguments.
     *
     * @param name the tool name
     * @param arguments the tool arguments
     * @param id the request ID
     * @return the response Map
     */
    public Map<String, Object> handleToolCall(String name, Map<String, Object> arguments, Long id) {
        LOGGER.log(Level.INFO, "Tool call started: {0} with arguments={1}", new Object[]{name, arguments});
        
        switch (name) {
            case "list_packages":
                return handleListPackagesTool(arguments, id);
            case "get_package_doc":
                return handleGetPackageDocTool(arguments, id);
            case "list_classes":
                return handleListClassesTool(arguments, id);
            case "get_source_code":
                return handleGetSourceCodeTool(arguments, id);
            default:
                LOGGER.log(Level.WARNING, "Unknown tool called: {0}", name);
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

    /**
     * Creates a successful MCP response.
     *
     * @param id the request ID
     * @param result the result object
     * @return the response Map
     */
    public Map<String, Object> createResponse(Long id, Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("result", result);
        return response;
    }

    /**
     * Creates an error MCP response.
     *
     * @param id the request ID
     * @param code the error code
     * @param message the error message
     * @return the error response Map
     */
    public Map<String, Object> createErrorResponse(Long id, int code, String message) {
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

    /**
     * Gets the server info for MCP protocol.
     *
     * @return server info Map
     */
    public Map<String, Object> getServerInfo() {
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "jakartaee-mcp");
        serverInfo.put("version", "0.0.1");
        return serverInfo;
    }

    /**
     * Gets the protocol version.
     *
     * @return protocol version string
     */
    public String getProtocolVersion() {
        return "1.0";
    }
}