package villagecompute.calendar.api.graphql.scalars;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit tests for JsonNodeAdapter - converts between JsonNode and String for GraphQL.
 */
class JsonNodeAdapterTest {

    private JsonNodeAdapter adapter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        adapter = new JsonNodeAdapter();
        mapper = new ObjectMapper();
    }

    // ========== from() - String to JsonNode ==========

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {"   "})
    void testFrom_NullEmptyOrBlankString_ReturnsNull(String input) {
        JsonNode result = adapter.from(input);
        assertNull(result);
    }

    @Test
    void testFrom_ValidJsonObject_ReturnsJsonNode() {
        String json = "{\"name\":\"test\",\"value\":123}";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isObject());
        assertEquals("test", result.get("name").asText());
        assertEquals(123, result.get("value").asInt());
    }

    @Test
    void testFrom_ValidJsonArray_ReturnsJsonNode() {
        String json = "[1, 2, 3, \"four\"]";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals(4, result.size());
        assertEquals(1, result.get(0).asInt());
        assertEquals("four", result.get(3).asText());
    }

    @Test
    void testFrom_ValidJsonString_ReturnsJsonNode() {
        String json = "\"just a string\"";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isTextual());
        assertEquals("just a string", result.asText());
    }

    @Test
    void testFrom_ValidJsonNumber_ReturnsJsonNode() {
        String json = "42";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isNumber());
        assertEquals(42, result.asInt());
    }

    @Test
    void testFrom_ValidJsonBoolean_ReturnsJsonNode() {
        String json = "true";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isBoolean());
        assertTrue(result.asBoolean());
    }

    @Test
    void testFrom_ValidJsonNull_ReturnsNullNode() {
        String json = "null";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertTrue(result.isNull());
    }

    @Test
    void testFrom_InvalidJson_ThrowsIllegalArgumentException() {
        String invalidJson = "{invalid json}";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.from(invalidJson));

        assertTrue(exception.getMessage().startsWith("Invalid JSON string:"));
        assertNotNull(exception.getCause());
    }

    @Test
    void testFrom_NestedJsonObject_ReturnsJsonNode() {
        String json = "{\"outer\":{\"inner\":{\"value\":\"deep\"}}}";
        JsonNode result = adapter.from(json);

        assertNotNull(result);
        assertEquals("deep", result.get("outer").get("inner").get("value").asText());
    }

    // ========== to() - JsonNode to String ==========

    @Test
    void testTo_NullJsonNode_ReturnsNull() {
        String result = adapter.to(null);
        assertNull(result);
    }

    @Test
    void testTo_ObjectNode_ReturnsValidJson() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "test");
        node.put("count", 42);

        String result = adapter.to(node);

        assertNotNull(result);
        JsonNode parsed = mapper.readTree(result);
        assertEquals("test", parsed.get("name").asText());
        assertEquals(42, parsed.get("count").asInt());
    }

    @Test
    void testTo_ArrayNode_ReturnsValidJson() throws Exception {
        ArrayNode node = mapper.createArrayNode();
        node.add(1);
        node.add("two");
        node.add(true);

        String result = adapter.to(node);

        assertNotNull(result);
        JsonNode parsed = mapper.readTree(result);
        assertTrue(parsed.isArray());
        assertEquals(3, parsed.size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"\"hello world\"", "123.45", "false", "null"})
    void testTo_PrimitiveNode_ReturnsValidJson(String json) throws Exception {
        JsonNode node = mapper.readTree(json);

        String result = adapter.to(node);

        assertEquals(json, result);
    }

    @Test
    void testTo_ComplexNestedStructure_ReturnsValidJson() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode nested = node.putObject("config");
        nested.put("enabled", true);
        ArrayNode items = nested.putArray("items");
        items.add("a");
        items.add("b");

        String result = adapter.to(node);

        assertNotNull(result);
        JsonNode parsed = mapper.readTree(result);
        assertTrue(parsed.get("config").get("enabled").asBoolean());
        assertEquals("a", parsed.get("config").get("items").get(0).asText());
    }

    // ========== Roundtrip tests ==========

    @Test
    void testRoundtrip_ObjectJson_PreservesData() {
        String original = "{\"key\":\"value\",\"number\":42,\"nested\":{\"inner\":true}}";

        JsonNode node = adapter.from(original);
        String roundtrip = adapter.to(node);
        JsonNode reparsed = adapter.from(roundtrip);

        assertEquals("value", reparsed.get("key").asText());
        assertEquals(42, reparsed.get("number").asInt());
        assertTrue(reparsed.get("nested").get("inner").asBoolean());
    }

    @Test
    void testRoundtrip_ArrayJson_PreservesData() {
        String original = "[1,2,3,{\"nested\":true}]";

        JsonNode node = adapter.from(original);
        String roundtrip = adapter.to(node);
        JsonNode reparsed = adapter.from(roundtrip);

        assertTrue(reparsed.isArray());
        assertEquals(4, reparsed.size());
        assertTrue(reparsed.get(3).get("nested").asBoolean());
    }
}
