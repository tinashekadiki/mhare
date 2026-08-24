package zw.ac.uz.emhare.communications.content.application;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates schema-version 1 structured blocks without accepting raw HTML. @author Tinashe K */
@Component
public class StructuredContentValidator {

  private static final Set<String> ALLOWED_BLOCK_TYPES =
      Set.of("HEADING", "PARAGRAPH", "LIST", "QUOTE", "CALLOUT", "IMAGE", "LINKS");
  private static final Set<String> TEXT_BLOCK_TYPES =
      Set.of("HEADING", "PARAGRAPH", "QUOTE", "CALLOUT");
  private static final Pattern HTML = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
  private final ObjectMapper objectMapper;

  public StructuredContentValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String validateAndNormalize(String content) {
    try {
      JsonNode root = objectMapper.readTree(content);
      if (root == null || !root.isArray()) {
        throw new IllegalArgumentException("Structured content must be an array of blocks.");
      }
      for (JsonNode block : root) {
        validateBlock(block);
      }
      return objectMapper.writeValueAsString(root);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Structured content is not valid JSON.", exception);
    }
  }

  public JsonNode read(String content) {
    try {
      return objectMapper.readTree(content);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored structured content is invalid.", exception);
    }
  }

  private void validateBlock(JsonNode block) {
    if (!block.isObject()) {
      throw new IllegalArgumentException("Each structured content block must be an object.");
    }
    String type = requiredText(block, "type").toUpperCase();
    if (!ALLOWED_BLOCK_TYPES.contains(type)) {
      throw new IllegalArgumentException("Unsupported structured content block: " + type + ".");
    }
    rejectRawHtml(block);
    if (TEXT_BLOCK_TYPES.contains(type)) {
      requiredText(block, "text");
    }
    if (type.equals("LIST")) {
      JsonNode items = block.get("items");
      if (items == null || !items.isArray() || items.isEmpty()) {
        throw new IllegalArgumentException("List blocks require at least one item.");
      }
      for (JsonNode item : items) {
        if (!item.isTextual() || item.textValue().isBlank()) {
          throw new IllegalArgumentException("List items must contain text.");
        }
      }
    }
    if (type.equals("IMAGE")) {
      requiredText(block, "mediaAssetId");
      requiredText(block, "alternativeText");
    }
    if (type.equals("LINKS")) {
      JsonNode links = block.get("links");
      if (links == null || !links.isArray() || links.isEmpty()) {
        throw new IllegalArgumentException("Link collections require at least one link.");
      }
      for (JsonNode link : links) {
        validatePublicLink(requiredText(link, "url"));
        requiredText(link, "label");
      }
    }
  }

  private void rejectRawHtml(JsonNode node) {
    if (node.isTextual() && HTML.matcher(node.textValue()).find()) {
      throw new IllegalArgumentException("Raw HTML is not allowed in structured content.");
    }
    if (node.isObject()) {
      for (String name : node.propertyNames()) {
        if (name.equalsIgnoreCase("html") || name.equalsIgnoreCase("rawHtml")) {
          throw new IllegalArgumentException(
              "Raw HTML fields are not allowed in structured content.");
        }
        rejectRawHtml(node.get(name));
      }
    } else if (node.isArray()) {
      node.forEach(this::rejectRawHtml);
    }
  }

  private void validatePublicLink(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Link collection contains an invalid URL.", exception);
    }
    if (uri.isAbsolute() && !Set.of("http", "https").contains(uri.getScheme().toLowerCase())) {
      throw new IllegalArgumentException("Public links must use HTTP or HTTPS.");
    }
    if (!uri.isAbsolute() && !value.startsWith("/")) {
      throw new IllegalArgumentException("Relative public links must start with '/'.");
    }
  }

  private String requiredText(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
    return value.textValue().trim();
  }
}
