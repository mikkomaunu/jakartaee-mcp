package org.maunu.jakartaee.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for JSON parsing and formatting.
 * Provides simple JSON serialization/deserialization for MCP messages.
 *
 * @author Mikko Maunu
 */
public final class JsonUtils {

    private JsonUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses a JSON string into a Map.
     *
     * @param json the JSON string to parse
     * @return the parsed Map, or null if parsing fails
     */
    public static Map<String, Object> parseJson(String json) {
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            return parseObject(json.substring(1, json.length() - 1));
        }
        return null;
    }

    /**
     * Parses a JSON array string into a List.
     *
     * @param json the JSON array string to parse
     * @return the parsed List, or empty list if parsing fails
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseJsonArray(String json) {
        json = json.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            return parseArray(json, 0);
        }
        return List.of();
    }

    /**
     * Parses a JSON object string into a Map.
     *
     * @param json the JSON object string (without outer braces)
     * @return the parsed Map
     */
    public static Map<String, Object> parseObject(String json) {
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

    /**
     * Parses a JSON value at the given position.
     *
     * @param json the JSON string
     * @param start the starting position
     * @return the parsed value
     */
    public static Object parseValue(String json, int start) {
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

    /**
     * Parses a JSON array at the given position.
     *
     * @param json the JSON string
     * @param start the starting position
     * @return the parsed List
     */
    public static List<Object> parseArray(String json, int start) {
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

    /**
     * Parses a JSON number at the given position.
     *
     * @param json the JSON string
     * @param start the starting position
     * @return the parsed Number
     */
    public static Number parseNumber(String json, int start) {
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

    /**
     * Finds the end position of a JSON value starting at the given position.
     *
     * @param json the JSON string
     * @param start the starting position
     * @return the end position
     */
    public static int findEnd(String json, int start) {
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

    /**
     * Formats a Map as a JSON string.
     *
     * @param map the Map to format
     * @return the JSON string
     */
    public static String formatJson(Map<String, Object> map) {
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

    /**
     * Formats a value as a JSON string.
     *
     * @param value the value to format
     * @return the JSON string representation
     */
    @SuppressWarnings("unchecked")
    public static String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson(value.toString()) + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            return formatJson((Map<String, Object>) value);
        } else if (value instanceof List) {
            return formatArray((List<?>) value);
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    /**
     * Formats a List as a JSON array string.
     *
     * @param list the List to format
     * @return the JSON array string
     */
    public static String formatArray(List<?> list) {
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

    /**
     * Escapes special characters in a string for JSON output.
     *
     * @param s the string to escape
     * @return the escaped string
     */
    public static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
