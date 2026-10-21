package io.github.hasanyelaldi.plimsoll.collect.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import io.github.hasanyelaldi.plimsoll.collect.http.HttpMethods;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the request mappings a Spring MVC / WebFlux service serves: {@code @GetMapping}, {@code @PostMapping},
 * {@code @PutMapping}, {@code @DeleteMapping}, {@code @PatchMapping} and {@code @RequestMapping} on the methods of
 * {@code @RestController} and {@code @Controller} classes, under the class's own {@code @RequestMapping} path.
 *
 * <p>Annotations count only when they resolve to {@code org.springframework.web.bind.annotation} through the imports
 * or a qualified name. Mappings on interfaces (a Feign client, an HTTP interface) are calls, not served operations,
 * and are not picked up. A {@code @RequestMapping} without {@code method} serves every method: its method is UNKNOWN.
 * A path that is not a string literal is not resolved; the mapping is skipped and noted.
 */
public final class RequestMappingScanner {

    private static final String PKG = "org.springframework.web.bind.annotation";
    private static final String STEREOTYPE = "org.springframework.stereotype";
    private static final Map<String, String> SHORTCUTS = Map.of("GetMapping", "GET", "PostMapping", "POST",
            "PutMapping", "PUT", "DeleteMapping", "DELETE", "PatchMapping", "PATCH");
    private static final String REQUEST_MAPPING = "RequestMapping";

    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));

    /**
     * One served operation.
     *
     * @param method   e.g. {@code POST}, with the mapping annotation's {@code file:line}; UNKNOWN for any method
     * @param path     class and method paths joined, always starting with {@code /}
     * @param evidence {@code file:line} of the method's mapping annotation
     */
    public record Mapping(Val<String> method, String path, String evidence) {

        /** e.g. {@code POST /owners}, or {@code * /owners} for any method. */
        public String operation() {
            return (method.isKnown() ? method.value() : "*") + " " + path;
        }
    }

    /**
     * @param mappings sorted by file, then line, then path
     * @param notes    mappings that were found but not resolved
     */
    public record Result(List<Mapping> mappings, List<String> notes) {
        public Result {
            mappings = List.copyOf(mappings);
            notes = List.copyOf(notes);
        }
    }

    private RequestMappingScanner() {}

    /** Every {@code .java} file under {@code serviceRoot/src/main/java}; file names relative to {@code serviceRoot}. */
    public static Result scan(Path serviceRoot) {
        return scan(serviceRoot, serviceRoot);
    }

    /** As {@link #scan(Path)}, with file names relative to {@code base}. */
    public static Result scan(Path base, Path serviceRoot) {
        Path sourceDir = serviceRoot.resolve("src/main/java");
        if (!Files.isDirectory(sourceDir)) {
            return new Result(List.of(), List.of());
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new InvalidSpecException(sourceDir + ": cannot list sources: " + e.getMessage(), e);
        }
        List<Mapping> mappings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Path file : files) {
            String name = base.relativize(file).toString().replace('\\', '/');
            try {
                Result r = parse(Files.readString(file, StandardCharsets.UTF_8), name);
                mappings.addAll(r.mappings());
                notes.addAll(r.notes());
            } catch (IOException e) {
                throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
            }
        }
        return new Result(mappings, notes);
    }

    /** One compilation unit. */
    public static Result parse(String source, String fileName) {
        ParseResult<CompilationUnit> result = PARSER.parse(source);
        CompilationUnit cu = result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                new InvalidSpecException(fileName + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
        List<Mapping> mappings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            boolean controller = type.getAnnotations().stream()
                    .anyMatch(a -> resolves(a, cu, PKG, "RestController")
                            || resolves(a, cu, STEREOTYPE, "Controller"));
            if (!controller) {
                continue;
            }
            List<String> prefixes = List.of("");
            Optional<AnnotationExpr> classMapping = type.getAnnotations().stream()
                    .filter(a -> resolves(a, cu, PKG, REQUEST_MAPPING)).findFirst();
            if (classMapping.isPresent()) {
                Optional<List<String>> p = paths(classMapping.get());
                if (p.isEmpty()) {
                    notes.add(fileName + ":" + line(classMapping.get()) + ": @RequestMapping path of "
                            + type.getNameAsString() + " is not a string literal; its mappings are skipped");
                    continue;
                }
                prefixes = p.get();
            }
            for (MethodDeclaration m : type.getMethods()) {
                for (AnnotationExpr a : m.getAnnotations()) {
                    mapping(a, cu, fileName, prefixes, m, mappings, notes);
                }
            }
        }
        mappings.sort(Comparator.comparingInt((Mapping m) -> line(m.evidence())).thenComparing(Mapping::path));
        return new Result(mappings, notes);
    }

    private static void mapping(AnnotationExpr a, CompilationUnit cu, String file, List<String> prefixes,
            MethodDeclaration m, List<Mapping> out, List<String> notes) {
        String evidence = file + ":" + line(a);
        List<Val<String>> methods;
        Optional<String> shortcut = SHORTCUTS.keySet().stream().filter(s -> resolves(a, cu, PKG, s)).findFirst();
        if (shortcut.isPresent()) {
            methods = List.of(Val.explicit(SHORTCUTS.get(shortcut.get()), evidence));
        } else if (resolves(a, cu, PKG, REQUEST_MAPPING)) {
            methods = requestMethods(a, evidence);
        } else {
            return;
        }
        Optional<List<String>> paths = paths(a);
        if (paths.isEmpty()) {
            notes.add(evidence + ": mapping path of " + m.getNameAsString() + " is not a string literal; skipped");
            return;
        }
        for (String prefix : prefixes) {
            for (String path : paths.get()) {
                for (Val<String> method : methods) {
                    out.add(new Mapping(method, join(prefix, path), evidence));
                }
            }
        }
    }

    /** {@code method = RequestMethod.POST} or a list of them; absent means every method (UNKNOWN). */
    private static List<Val<String>> requestMethods(AnnotationExpr a, String evidence) {
        if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair pair : n.getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    List<Val<String>> out = new ArrayList<>();
                    for (Expression e : elements(pair.getValue())) {
                        out.add(HttpMethods.normalize(e.toString())
                                .<Val<String>>map(v -> Val.explicit(v, evidence))
                                .orElseGet(() -> Val.unknown(evidence + ": request method " + e + " not resolved")));
                    }
                    return out;
                }
            }
        }
        return List.of(Val.unknown(evidence + ": @RequestMapping without method serves every HTTP method"));
    }

    /** The {@code value} / {@code path} strings, {@code [""]} when absent, empty when one is not a literal. */
    private static Optional<List<String>> paths(AnnotationExpr a) {
        Expression value = null;
        if (a instanceof SingleMemberAnnotationExpr s) {
            value = s.getMemberValue();
        } else if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair pair : n.getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    value = pair.getValue();
                }
            }
        }
        if (value == null) {
            return Optional.of(List.of(""));
        }
        List<String> out = new ArrayList<>();
        for (Expression e : elements(value)) {
            if (!e.isStringLiteralExpr()) {
                return Optional.empty();
            }
            out.add(e.asStringLiteralExpr().asString());
        }
        return Optional.of(out.isEmpty() ? List.of("") : out);
    }

    private static List<Expression> elements(Expression e) {
        return e instanceof ArrayInitializerExpr array ? array.getValues() : List.of(e);
    }

    /** As Spring joins them: a leading {@code /} is added, duplicate and trailing ones dropped. */
    static String join(String prefix, String path) {
        String joined = ("/" + prefix + "/" + path).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static boolean resolves(AnnotationExpr a, CompilationUnit cu, String pkg, String simpleName) {
        String written = a.getNameAsString();
        if (written.equals(pkg + "." + simpleName)) {
            return true;
        }
        if (!written.equals(simpleName)) {
            return false;
        }
        for (ImportDeclaration i : cu.getImports()) {
            if (!i.isStatic() && (i.isAsterisk() ? i.getNameAsString().equals(pkg)
                    : i.getNameAsString().equals(pkg + "." + simpleName))) {
                return true;
            }
        }
        return false;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }

    private static int line(String evidence) {
        return Integer.parseInt(evidence.substring(evidence.lastIndexOf(':') + 1));
    }
}
