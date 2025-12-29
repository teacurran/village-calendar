package villagecompute.calendar.api.graphql.scalars;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.graphql.api.Adapter;

/**
 * Adapter to convert between JsonNode and String for GraphQL. This allows JsonNode fields to be
 * represented as JSON strings in the GraphQL schema.
 */
public class JsonNodeAdapter implements Adapter<JsonNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public JsonNode from(String string) {
        if (string == null || string.isBlank()) {
            return null;
        }

        try {
            return MAPPER.readTree(string);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON string: " + e.getMessage(), e);
        }
    }

    @Override
    public String to(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        try {
            return MAPPER.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize JsonNode: " + e.getMessage(), e);
        }
    }
}
