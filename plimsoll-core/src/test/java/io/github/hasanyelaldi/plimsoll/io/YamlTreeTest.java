package io.github.hasanyelaldi.plimsoll.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlTreeTest {

    private static final String TWO_DOCS = """
            a: 1
            ---
            b:
              c: 2
            """;

    @Test
    void parseAllReturnsEveryDocumentWithLines() {
        List<Node> docs = YamlTree.parseAll(TWO_DOCS, "app.yml");

        assertThat(docs).hasSize(2);
        MapNode second = (MapNode) docs.get(1);
        assertThat(second.entries().get("b").keyLine()).isEqualTo(3);
        assertThat(((MapNode) second.entries().get("b").value()).entries().get("c").keyLine()).isEqualTo(4);
    }

    @Test
    void parseAllSkipsEmptyDocuments() {
        assertThat(YamlTree.parseAll("---\n---\na: 1\n", "app.yml")).hasSize(1);
        assertThat(YamlTree.parseAll("", "app.yml")).isEmpty();
    }

    @Test
    void parseRejectsASecondDocument() {
        assertThatThrownBy(() -> YamlTree.parse(TWO_DOCS, "spec.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("spec.yaml:3: only one YAML document is allowed");
    }
}
