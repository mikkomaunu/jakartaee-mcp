package org.maunu.jakartaee.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for JakartaEeMcp.
 *
 * @author Mikko Maunu
 */
public class JakartaEeMcpTest {

    @Test
    public void testGreet() {
        JakartaEeMcp mcp = new JakartaEeMcp();
        assertEquals("Hello from Jakarta EE MCP!", mcp.greet());
    }
}
