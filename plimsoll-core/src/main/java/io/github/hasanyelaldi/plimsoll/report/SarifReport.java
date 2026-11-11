package io.github.hasanyelaldi.plimsoll.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SARIF 2.1.0, for code scanning (#75). Locations follow ADR 0013; paths are relative to {@code SRCROOT}. */
final class SarifReport {

    private SarifReport() {}

    static final String SRCROOT = "SRCROOT";

    private static final String README = "https://github.com/hasanyelaldi/plimsoll#rules";

    private record Rule(String id, String name, String description) {}

    /** Every rule id a finding can carry, in report order. */
    private static final List<Rule> RULES = List.of(
            new Rule("PLM-A1", "Amplification",
                    "How many requests one entry request can cause at a service, assuming every try fails."),
            new Rule("PLM-A2a", "RetryBudgetExceedsDeadline",
                    "The time for all tries and backoffs of an edge is longer than the caller's deadline."),
            new Rule("PLM-A2b", "AbandonedWork",
                    "The callee may keep working after the caller stopped waiting for a try."),
            new Rule("PLM-A3", "NonIdempotentRetry", "A call that is not idempotent is retried."),
            new Rule("PLM-A4", "BackoffHygiene", "Retries with zero backoff, or without jitter."),
            new Rule("PLM-A5", "LayeredRetries",
                    "One edge retried by two or more layers, or one path with retries on two or more edges."),
            new Rule("PLM-C1", "MechanismNotModelled",
                    "A retry, timeout or breaker mechanism was found that no collector models."));

    /**
     * A relative path ending in a file extension, then a line. It may not follow a word character, '.', '/', ':' or
     * '-', so the host of a URL never matches.
     */
    private static final Pattern FILE_LINE = Pattern.compile(
            "(?<![\\w./:-])([\\w.-]+(?:/[\\w.{}*$-]+)*\\.[A-Za-z]\\w*):(\\d+)");

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Two-space indent, {@code "key": value}, and "\n" on every platform. */
    private static final ObjectWriter WRITER = JSON.writer(new DefaultPrettyPrinter()
            .withSeparators(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n")));

    private record FileLine(String path, int line) {}

    static String render(AnalysisResult r, String specName, AnalysisOptions options, Collected collected) {
        ObjectNode root = JSON.createObjectNode();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        ObjectNode run = root.putArray("runs").addObject();

        ObjectNode driver = run.putObject("tool").putObject("driver");
        driver.put("name", "Plimsoll");
        driver.put("informationUri", "https://github.com/hasanyelaldi/plimsoll");
        ArrayNode rules = driver.putArray("rules");
        for (Rule rule : RULES) {
            ObjectNode node = rules.addObject();
            node.put("id", rule.id());
            node.put("name", rule.name());
            node.putObject("shortDescription").put("text", rule.description());
            node.put("helpUri", README);
        }

        run.putObject("originalUriBaseIds").putObject(SRCROOT).putObject("description")
                .put("text", "The analysed directory, or the directory of the spec.");

        ArrayNode results = run.putArray("results");
        Map<String, Integer> seen = new HashMap<>();
        for (Finding f : r.findings()) {
            String key = f.ruleId() + "\n" + f.severity() + "\n" + f.location();
            results.add(result(f, specName, collected, sha256(key) + ":" + seen.merge(key, 1, Integer::sum)));
        }

        ObjectNode properties = run.putObject("properties");
        properties.put("input", specName);
        properties.put("summary", Reports.summary(r));
        properties.put("amplificationLimit", options.maxAmplification());
        ObjectNode amplification = properties.putObject("amplification");
        for (ServiceAmplification a : r.amplification()) {
            amplification.put(a.service(), Reports.amplification(a));
        }
        properties.put("coverageSource", collected.coverage().source());
        ArrayNode notes = properties.putArray("notes");
        collected.notes().forEach(notes::add);

        try {
            return WRITER.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode result(Finding f, String specName, Collected collected, String fingerprint) {
        ObjectNode result = JSON.createObjectNode();
        result.put("ruleId", f.ruleId());
        result.put("ruleIndex", ruleIndex(f.ruleId()));
        result.put("level", switch (f.severity()) {
            case ERROR -> "error";
            case WARN -> "warning";
            case INFO -> "note";
        });
        result.putObject("message").put("text", message(f));

        List<FileLine> places = fileLines(f);
        Optional<FileLine> primary = places.isEmpty() ? fallback(f, specName, collected) : Optional.of(places.get(0));
        primary.ifPresent(p -> location(result.putArray("locations").addObject(), p));
        if (places.size() > 1) {
            ArrayNode related = result.putArray("relatedLocations");
            for (int i = 1; i < places.size(); i++) {
                ObjectNode node = related.addObject();
                node.put("id", i);
                location(node, places.get(i));
            }
        }

        // Several findings can share a line (all gateway routes take the default-filters Retry). Code scanning tells
        // results apart only by primaryLocationLineHash and computes it from the line's text when it is absent, so
        // findings on one line would merge. Rule, severity and location identify a finding across runs; the
        // occurrence counts findings that share all three, e.g. A2a once per layer of an edge (ADR 0013).
        result.putObject("partialFingerprints").put("primaryLocationLineHash", fingerprint);

        ObjectNode properties = result.putObject("properties");
        properties.put("location", f.location());
        properties.put("severity", f.severity().name());
        ArrayNode values = properties.putArray("values");
        for (UsedValue v : f.values()) {
            ObjectNode value = values.addObject();
            value.put("name", v.name());
            value.put("value", v.display());
            value.put("source", v.source().name());
            value.put("evidence", v.evidence());
        }
        if (f.dependsOnUnknown()) {
            properties.putArray("tags").add(Finding.DEPENDS_ON_UNKNOWN);
            ArrayNode unknowns = properties.putArray("unknowns");
            f.unknowns().forEach(unknowns::add);
        }
        return result;
    }

    private static int ruleIndex(String id) {
        for (int i = 0; i < RULES.size(); i++) {
            if (RULES.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("no SARIF rule for " + id);
    }

    private static String message(Finding f) {
        StringBuilder text = new StringBuilder(f.location()).append(": ").append(f.detail());
        if (!f.values().isEmpty()) {
            text.append("\nValues used:");
            for (UsedValue v : f.values()) {
                text.append("\n- ").append(v.name()).append(" = ").append(Reports.value(v));
            }
        }
        if (f.dependsOnUnknown()) {
            text.append('\n').append(Reports.unknowns(f));
        }
        return text.toString();
    }

    /** Distinct {@code file:line}s in the evidence, EXPLICIT values first (ADR 0013). */
    private static List<FileLine> fileLines(Finding f) {
        Set<FileLine> found = new LinkedHashSet<>();
        for (boolean explicit : new boolean[] {true, false}) {
            for (UsedValue v : f.values()) {
                if ((v.source() == Source.EXPLICIT) == explicit && v.evidence() != null) {
                    Matcher m = FILE_LINE.matcher(v.evidence());
                    while (m.find()) {
                        found.add(new FileLine(m.group(1), Integer.parseInt(m.group(2))));
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Line 1 of the spec, or of the first file read for the first service in the location (ADR 0013). */
    private static Optional<FileLine> fallback(Finding f, String specName, Collected collected) {
        List<Coverage.ServiceSources> services = collected.coverage().services();
        if (services.isEmpty()) {
            return Optional.of(new FileLine(specName, 1));
        }
        String service = f.location().split("[→,\\s]", 2)[0];
        return services.stream()
                .filter(s -> s.service().equals(service))
                .flatMap(s -> s.read().stream())
                .filter(p -> !p.endsWith("/"))
                .findFirst()
                .map(p -> new FileLine(p, 1));
    }

    private static void location(ObjectNode node, FileLine place) {
        ObjectNode physical = node.putObject("physicalLocation");
        ObjectNode artifact = physical.putObject("artifactLocation");
        artifact.put("uri", uri(place.path()));
        artifact.put("uriBaseId", SRCROOT);
        physical.putObject("region").put("startLine", place.line());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String uri(String path) {
        try {
            return new URI(null, null, path, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a relative path: " + path, e);
        }
    }
}
