package io.github.hasanyelaldi.plimsoll.io;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A YAML document that remembers the line of every node, so values can cite {@code file:line}. */
final class YamlTree {

    sealed interface Node permits MapNode, ListNode, Scalar {
        int line();
    }

    record Entry(int keyLine, Node value) {}

    record MapNode(Map<String, Entry> entries, int line) implements Node {}

    record ListNode(List<Node> items, int line) implements Node {}

    /** {@code token} is VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE or VALUE_NULL. */
    record Scalar(String text, JsonToken token, int line) implements Node {}

    private YamlTree() {}

    static Node parse(String yaml, String fileName) {
        try (JsonParser p = new YAMLFactory().createParser(yaml)) {
            if (p.nextToken() == null) {
                throw new InvalidSpecException(fileName + ": empty document");
            }
            Node root = read(p, fileName);
            if (p.nextToken() != null) {
                throw new InvalidSpecException(fileName + ":" + p.currentTokenLocation().getLineNr()
                        + ": only one YAML document is allowed");
            }
            return root;
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
