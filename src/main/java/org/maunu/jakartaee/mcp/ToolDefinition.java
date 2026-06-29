package org.maunu.jakartaee.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tool definition for MCP (Model Context Protocol).
 * Contains the tool's metadata including name, description, and input schema.
 *
 * @author Mikko Maunu
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;

    /**
     * Creates a new ToolDefinition.
     *
     * @param name the tool name
     * @param description the tool description
     * @param inputSchema the input schema for the tool
     */
    public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = new HashMap<>(inputSchema);
    }

    /**
     * Creates a simple tool definition with a single string parameter.
     *
     * @param name the tool name
     * @param description the tool description
     * @param paramName the parameter name
     * @param paramDescription the parameter description
     * @return a new ToolDefinition
     */
    public static ToolDefinition createWithStringParam(String name, String description, 
                                                        String paramName, String paramDescription) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> param = new HashMap<>();
        param.put("type", "string");
        param.put("description", paramDescription);
        properties.put(paramName, param);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);

        return new ToolDefinition(name, description, inputSchema);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInputSchema() {
        return new HashMap<>(inputSchema);
    }

    /**
     * Converts this ToolDefinition to a Map representation for MCP protocol.
     *
     * @return the Map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("description", description);
        map.put("inputSchema", inputSchema);
        return map;
    }

    @Override
    public String toString() {
        return "ToolDefinition{name='" + name + "', description='" + description + "'}";
    }
}
