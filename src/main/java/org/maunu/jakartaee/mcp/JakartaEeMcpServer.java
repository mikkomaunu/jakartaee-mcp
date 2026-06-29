package org.maunu.jakartaee.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP (Model Context Protocol) server for Jakarta EE.
 * Provides tools to browse Jakarta EE packages and classes from extracted sources.
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpServer {

    private static final String PROTOCOL_VERSION = "1.0";
    private static final String SERVER_NAME = "jakartaee-mcp";
    private static final String SERVER_VERSION = "0.0.1";
    private static final Logger LOGGER = Logger.getLogger(JakartaEeMcpServer.class.getName());

    private final PackageScanner packageScanner;
    private final BufferedReader in;
    private final PrintWriter out;

    public JakartaEeMcpServer() {
        this(new PackageScanner(), new BufferedReader(new InputStreamReader(System.in)), new PrintWriter(System.out, true));
    }

    public JakartaEeMcpServer(PackageScanner packageScanner, BufferedReader in, PrintWriter out) {
        this.packageScanner = packageScanner;
        this.in = in;
        this.out = out;
    }

    /**
     * Starts the MCP server.
     */
    public void start() {
        LOGGER.log(Level.INFO, "MCP server starting with protocol version {0}", PROTOCOL_VERSION);
        sendServerInfo();

        String line;
        try {
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Map<String, Object> message = parseJson(line);
                if (message == null) {
                    continue;
                }

                handleMessage(message);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading input: " + e.getMessage(), e);
        }
    }

    private void sendServerInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("protocolVersion", PROTOCOL_VERSION);
        info.put("capabilities", getCapabilities());
        info.put("serverInfo", Map.of(
            "name", SERVER_NAME,
            "version", SERVER_VERSION
        ));
        sendMessage("serverInfo", info);
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

    private void handleMessage(Map<String, Object> message) {
        Object methodObj = message.get("method");
        if (methodObj == null) {
            return;
        }

        String method = methodObj.toString();
        Object idObj = message.get("id");
        Long id = idObj != null ? ((Number) idObj).longValue() : null;

        LOGGER.log(Level.INFO, "Handling message with method: {0}, id: {1}", new Object[]{method, id});

        switch (method) {
            case "initialize":
                handleInitialize(message, id);
                break;
            case "tools/list":
                handleToolsList(id);
                break;
            case "tools/call":
                handleToolsCall(message, id);
                break;
            default:
                LOGGER.log(Level.WARNING, "Unknown method: {0}", method);
        }
    }

    private void handleInitialize(Map<String, Object> message, Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", getCapabilities());
        result.put("serverInfo", Map.of(
            "name", SERVER_NAME,
            "version", SERVER_VERSION
        ));
        sendResponse(id, result);
    }

    private void handleToolsList(Long id) {
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
        sendResponse(id, result);
    }

    private void handleToolsCall(Map<String, Object> message, Long id) {
        Object paramsObj = message.get("params");
        if (paramsObj == null || !(paramsObj instanceof Map)) {
            sendError(id, -32600, "Missing params");
            return;
        }

        Map<String, Object> params = (Map<String, Object>) paramsObj;
        Object nameObj = params.get("name");
        if (nameObj == null) {
            sendError(id, -32600, "Missing tool name");
            return;
        }

        String name = nameObj.toString();
        Object argumentsObj = params.get("arguments");
        Map<String, Object> arguments = null;
        if (argumentsObj instanceof Map) {
            arguments = (Map<String, Object>) argumentsObj;
        }

        if ("list_packages".equals(name)) {
            handleListPackagesTool(arguments, id);
        } else if ("get_package_doc".equals(name)) {
            handleGetPackageDocTool(arguments, id);
        } else if ("list_classes".equals(name)) {
            handleListClassesTool(arguments, id);
        } else if ("get_source_code".equals(name)) {
            handleGetSourceCodeTool(arguments, id);
        } else {
            sendError(id, -32601, "Unknown tool: " + name);
        }
    }

    private void handleListPackagesTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("root")) {
            sendError(id, -32602, "Missing required argument: root");
            return;
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
        sendResponse(id, result);
    }

    private void handleListClassesTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("package")) {
            sendError(id, -32602, "Missing required argument: package");
            return;
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
        sendResponse(id, result);
    }

    private void handleGetSourceCodeTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("className")) {
            sendError(id, -32602, "Missing required argument: className");
            return;
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
        sendResponse(id, result);
    }

    private void handleGetPackageDocTool(Map<String, Object> arguments, Long id) {
        if (arguments == null || !arguments.containsKey("package")) {
            sendError(id, -32602, "Missing required argument: package");
            return;
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
        sendResponse(id, result);
    }

    private void sendResponse(Long id, Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("result", result);
        sendMessage(null, response);
    }

    private void sendError(Long id, int code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.put("id", id);
        }
        response.put("error", error);
        sendMessage(null, response);
    }

    private void sendMessage(String method, Map<String, Object> params) {
        Map<String, Object> message = new HashMap<>();
        message.put("jsonrpc", "2.0");
        if (method != null) {
            message.put("method", method);
        }
        message.putAll(params);
        out.println(formatJson(message));
        out.flush();
    }

    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for MCP messages
        // This is a simplified implementation
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
                // Parse key
                int end = json.indexOf('"', i + 1);
                if (end == -1) break;
                String key = json.substring(i + 1, end);
                i = end + 1;
                
                // Skip colon
                while (i < json.length() && json.charAt(i) != ':') i++;
                if (i >= json.length()) break;
                i++;
                
                // Skip whitespace
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
                if (i >= json.length()) break;
                
                // Parse value
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
     * Main entry point for the MCP server.
     */
    public static void main(String[] args) {
        new JakartaEeMcpServer().start();
    }
}
