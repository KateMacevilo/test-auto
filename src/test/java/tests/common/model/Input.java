package tests.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class Input {
    private String contentType;
    private Map<String, String> headers;
    private JsonNode body;
}
