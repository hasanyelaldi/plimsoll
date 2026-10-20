package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.hasanyelaldi.plimsoll.collect.http.HttpMethods;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the Resilience4j annotations {@code @Retry}, {@code @CircuitBreaker} and {@code @TimeLimiter} on classes and
 * methods with JavaParser. An annotation counts only when it resolves to the Resilience4j type through its imports
 * or a qualified name, so Spring Retry's {@code @Retryable} or a project's own {@code @Retry} are not picked up.
 *
 * <p>The instance {@code name} is EXPLICIT when it is a string literal and UNKNOWN otherwise (a constant or an
 * expression): it is not resolved.
 *
 * <p>In each method body it also finds the HTTP calls of a {@code WebClient} or {@code RestClient} chain: a
 * {@code .get()}, {@code .post()}, ... or {@code .method(HttpMethod.X)} directly followed by {@code .uri(...)}.
 */
public final class AnnotationScanner {

    public enum Kind {
        RETRY("io.github.resilience4j.retry.annotation", "Retry"),
        CIRCUIT_BREAKER("io.github.resilience4j.circuitbreaker.annotation", "CircuitBreaker"),
        TIME_LIMITER("io.github.resilience4j.timelimiter.annotation", "TimeLimiter");

        final String pkg;
        final String simpleName;

        Kind(String pkg, String simpleName) {
            this.pkg = pkg;
            this.simpleName = simpleName;
        }

        String qualifiedName() {
            return pkg + "." + simpleName;
        }
    }

    /**
     * @param annotations instance name per annotation found; evidence is the annotation's {@code file:line}
     * @param httpCalls   HTTP calls in the body, in source order
     */
    public record ScannedMethod(String name, String returnType, int line, Map<Kind, Val<String>> annotations,
            List<HttpCall> httpCalls) {
        public ScannedMethod {
            annotations = annotations.isEmpty() ? Map.of() : new EnumMap<>(annotations);
            httpCalls = List.copyOf(httpCalls);
        }
    }

    /**
     * One {@code WebClient} / {@code RestClient} request in a method body.
     *
     * @param method   e.g. {@code POST}; UNKNOWN when {@code .method(...)} is given an expression
     * @param evidence {@code file:line} of the call
     */
    public record HttpCall(Val<String> method, String evidence) {}

    /** @param name fully qualified, nested types joined with {@code .} */
    public record ScannedClass(String name, String file, int line, Map<Kind, Val<String>> annotations,
            List<ScannedMethod> methods) {
        public ScannedClass {
            annotations = annotations.isEmpty() ? Map.of() : new EnumMap<>(annotations);
            methods = List.copyOf(methods);
        }
    }

    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));

    private AnnotationScanner() {}

    /** Every {@code .java} file under {@code sourceDir}; file names in evidence are relative to {@code root}. */
    public static List<ScannedClass> scan(Path root, Path sourceDir) {
        if (!Files.isDirectory(sourceDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new InvalidSpecException(sourceDir + ": cannot list sources: " + e.getMessage(), e);
        }
        List<ScannedClass> classes = new ArrayList<>();
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            try {
                classes.addAll(parse(Files.readString(file, StandardCharsets.UTF_8), name));
            } catch (IOException e) {
                throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
            }
        }
        classes.sort(Comparator.comparing(ScannedClass::name));
        return classes;
    }

    /** One compilation unit; every type in it, nested types included. */
    public static List<ScannedClass> parse(String source, String fileName) {
        ParseResult<CompilationUnit> result = PARSER.parse(source);
        CompilationUnit cu = result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                new InvalidSpecException(fileName + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
        List<ScannedClass> classes = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            String typeName = type.getFullyQualifiedName().orElse(type.getNameAsString());
            List<ScannedMethod> methods = new ArrayList<>();
            for (MethodDeclaration m : type.getMethods()) {
                methods.add(new ScannedMethod(m.getNameAsString(), returnType(m), line(m.getName()),
                        annotations(m.getAnnotations(), cu, fileName), httpCalls(m, fileName)));
            }
            classes.add(new ScannedClass(typeName, fileName, line(type.getName()),
                    annotations(type.getAnnotations(), cu, fileName), methods));
        }
        return classes;
    }

    private static Map<Kind, Val<String>> annotations(List<AnnotationExpr> found, CompilationUnit cu, String file) {
        Map<Kind, Val<String>> out = new EnumMap<>(Kind.class);
        for (AnnotationExpr a : found) {
            kind(a, cu).ifPresent(k -> out.put(k, instanceName(a, file + ":" + line(a))));
        }
        return out;
    }

    private static Optional<Kind> kind(AnnotationExpr a, CompilationUnit cu) {
        String written = a.getNameAsString();
        for (Kind k : Kind.values()) {
            if (written.equals(k.qualifiedName())) {
                return Optional.of(k);
            }
            if (written.equals(k.simpleName) && imports(cu, k)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }

    private static boolean imports(CompilationUnit cu, Kind k) {
        for (ImportDeclaration i : cu.getImports()) {
            if (i.isStatic()) {
                continue;
            }
            if (i.isAsterisk() ? i.getNameAsString().equals(k.pkg) : i.getNameAsString().equals(k.qualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static Val<String> instanceName(AnnotationExpr a, String evidence) {
        if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair pair : n.getPairs()) {
                if (pair.getNameAsString().equals("name")) {
                    if (pair.getValue().isStringLiteralExpr()) {
                        return Val.explicit(pair.getValue().asStringLiteralExpr().asString(), evidence);
                    }
                    return Val.unknown(evidence + ": name is not a string literal: " + pair.getValue());
                }
            }
        }
        return Val.unknown(evidence + ": no name attribute");
    }

    private static List<HttpCall> httpCalls(MethodDeclaration m, String file) {
        List<HttpCall> calls = new ArrayList<>();
        for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
            boolean beforeUri = call.getParentNode()
                    .filter(p -> p instanceof MethodCallExpr uri && uri.getNameAsString().equals("uri")
                            && uri.getScope().filter(call::equals).isPresent())
                    .isPresent();
            if (!beforeUri) {
                continue;
            }
            String evidence = file + ":" + line(call.getName());
            String name = call.getNameAsString();
            if (call.getArguments().isEmpty() && name.equals(name.toLowerCase(Locale.ROOT))) {
                HttpMethods.normalize(name).ifPresent(v -> calls.add(new HttpCall(Val.explicit(v, evidence), evidence)));
            } else if (name.equals("method") && call.getArguments().size() == 1) {
                calls.add(new HttpCall(methodArgument(call.getArgument(0), evidence), evidence));
            }
        }
        calls.sort(Comparator.comparingInt(c -> lineOf(c.evidence())));
        return calls;
    }

    /** {@code HttpMethod.POST} or a statically imported {@code POST}; anything else is not resolved. */
    private static Val<String> methodArgument(Expression arg, String evidence) {
        Optional<String> name = Optional.empty();
        if (arg instanceof FieldAccessExpr f && f.getScope().toString().endsWith("HttpMethod")) {
            name = HttpMethods.normalize(f.getNameAsString());
        } else if (arg instanceof NameExpr n) {
            name = HttpMethods.normalize(n.getNameAsString()).filter(v -> v.equals(n.getNameAsString()));
        }
        return name.<Val<String>>map(v -> Val.explicit(v, evidence))
                .orElseGet(() -> Val.unknown(evidence + ": HTTP method is not a constant: " + arg));
    }

    private static int lineOf(String evidence) {
        return Integer.parseInt(evidence.substring(evidence.lastIndexOf(':') + 1));
    }

    /** Simple name of the return type without type arguments, e.g. {@code CompletableFuture}. */
    private static String returnType(MethodDeclaration m) {
        if (m.getType() instanceof ClassOrInterfaceType t) {
            return t.getNameAsString();
        }
        return m.getType().asString();
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }
}
