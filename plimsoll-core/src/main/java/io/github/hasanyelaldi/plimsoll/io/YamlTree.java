package io.github.hasanyelaldi.plimsoll.io;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A YAML document that remembers the line of every node, so values can cite {@code file:line}.
 * Shared by the spec loader and the collectors.
 */
public final class YamlTree {

    public sealed interface Node permits MapNode, ListNode, Scalar {
        int line();
    }

    public record Entry(int keyLine, Node value) {}

    public record MapNode(Map<String, Entry> entries, int line) implements Node {}

    public record ListNode(List<Node> items, int line) implements Node {}

    /** {@code token} is VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE or VALUE_NULL. */
    public record Scalar(String text, JsonToken token, int line) implements Node {}

    private YamlTree() {}

    /** Parses exactly one document. */
    public static Node parse(String yaml, String fileName) {
        List<Node> docs = parseAll(yaml, fileName);
        if (docs.isEmpty()) {
            throw new InvalidSpecException(fileName + ": empty document");
        }
        if (docs.size() > 1) {
            throw new InvalidSpecException(fileName + ":" + docs.get(1).line() + ": only one YAML document is allowed");
        }
        return docs.get(0);
    }

    /** Parses every document of a {@code ---} separated stream, in order. Empty documents are skipped. */
    public static List<Node> parseAll(String yaml, String fileName) {
        try (JsonParser p = new YAMLFactory().createParser(yaml)) {
            List<Node> docs = new ArrayList<>();
            while (p.nextToken() != null) {
                Node doc = read(p, fileName);
                if (!(doc instanceof Scalar s && s.token() == JsonToken.VALUE_NULL)) {
                    docs.add(doc);
                }
            }
            return docs;
        } catch (IOException e) {
            throw new InvalidSpecException(fileName + ": invalid YAML: " + firstLine(e.getMessage()), e);
        }
    }

    private static Node read(JsonParser p, String fileName) throws IOException {
        int line = p.currentTokenLocation().getLineNr();
        switch (p.currentToken()) {
            case START_OBJECT -> {
                Map<String, Entry> entries = new LinkedHashMap<>();
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String key = p.currentName();
                    int keyLine = p.currentTokenLocation().getLineNr();
                    p.nextToken();
                    if (entries.put(key, new Entry(keyLine, read(p, fileName))) != null) {
                        throw new InvalidSpecException(fileName + ":" + keyLine + ": duplicate key '" + key + "'");
                    }
                }
                return new MapNode(entries, line);
            }
            case START_ARRAY -> {
                List<Node> items = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    items.add(read(p, fileName));
                }
                return new ListNode(items, line);
            }
            default -> {
                return new Scalar(p.getText(), p.currentToken(), line);
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "parse error";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
