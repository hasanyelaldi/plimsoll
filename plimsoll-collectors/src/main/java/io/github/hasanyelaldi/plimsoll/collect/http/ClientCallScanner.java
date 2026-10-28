package io.github.hasanyelaldi.plimsoll.collect.http;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the outbound HTTP requests a service makes in code, with JavaParser, so that they can become edges without
 * an {@code edges-hint.yaml} entry (#37).
 *
 * <ul>
 *   <li>A <b>request</b> is a {@code WebClient} / {@code RestClient} chain ({@code .get()}, ... or
 *       {@code .method(HttpMethod.X)} directly followed by {@code .uri(...)}) or a {@code RestTemplate} call
 *       ({@code getForObject}, {@code postForEntity}, {@code exchange(url, HttpMethod.X, ...)}, ...).
 *   <li>Its <b>URI</b> is folded from string literals, {@code +}, {@code URI.create(...)}, and local variables and
 *       fields initialised with them. A field assigned elsewhere in its class still gives its initial value, and
 *       the evidence names each assignment. Anything else, such as a host looked up with {@code DiscoveryClient},
 *       leaves the host UNKNOWN.
 *   <li>Its <b>client</b> is traced back from the receiver: built in place ({@code WebClient.builder()},
 *       {@code new RestTemplate()}) or injected (a constructor parameter, or a field the class never assigns). An
 *       injected client is load-balanced when the module declares only {@code @LoadBalanced} beans of its type, or
 *       when the injection point itself is {@code @LoadBalanced}.
 * </ul>
 *
 * Types are matched by simple name ({@code WebClient.Builder}, {@code RestTemplate}); nothing is resolved across
 * files except the module's {@code @Bean} methods.
 */
public final class ClientCallScanner {

    /**
     * One request in code.
     *
     * @param qualifiedMethod the enclosing {@code Class#method}, {@code Class#<init>} for a constructor
     * @param client          {@code WebClient}, {@code RestClient} or {@code RestTemplate}; null when the receiver's
     *                        type is not known
     * @param loadBalanced    whether the client goes through Spring Cloud LoadBalancer
     * @param method          the HTTP method, e.g. {@code GET}
     * @param host            the URI's host, e.g. {@code customers-service}
     * @param path            the URI's path without query, unresolved parts as {@code {name}} or {@code {?}}
     * @param uri             the URI argument as written
     * @param resilience4j    Resilience4j annotations on the enclosing method or class, with {@code file:line}
     * @param evidence        {@code file:line} of the request
     */
    public record ClientCall(String qualifiedMethod, String client, Val<Boolean> loadBalanced, Val<String> method,
            Val<String> host, String path, String uri, List<String> resilience4j, String evidence) {
        public ClientCall {
            resilience4j = List.copyOf(resilience4j);
        }

        /** {@code GET /owners/{ownerId}}, or the path alone when the method is not known. */
        public String operation() {
            return method.isKnown() ? method.value() + " " + path : path;
        }
    }

    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
    private static final Set<String> CLIENT_TYPES =
            Set.of("WebClient", "WebClient.Builder", "RestClient", "RestClient.Builder", "RestTemplate");
    private static final Set<String> FLUENT_CLIENTS = Set.of("WebClient", "RestClient");
    private static final Map<String, String> TEMPLATE_METHODS = Map.ofEntries(
            Map.entry("getForObject", "GET"), Map.entry("getForEntity", "GET"), Map.entry("headForHeaders", "HEAD"),
            Map.entry("postForObject", "POST"), Map.entry("postForEntity", "POST"),
            Map.entry("postForLocation", "POST"), Map.entry("put", "PUT"), Map.entry("patchForObject", "PATCH"),
            Map.entry("delete", "DELETE"), Map.entry("optionsForAllow", "OPTIONS"), Map.entry("exchange", ""),
            Map.entry("execute", ""));
    private static final String LOAD_BALANCED = "org.springframework.cloud.client.loadbalancer.LoadBalanced";
    private static final String R4J = "io.github.resilience4j.";
    private static final Set<String> R4J_ANNOTATIONS =
            Set.of("Retry", "CircuitBreaker", "TimeLimiter", "Bulkhead", "RateLimiter");
    private static final Pattern URL =
            Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#:{}\\s]+)((?::\\d+)?[/?#].*|:\\d+)?", Pattern.DOTALL);
    private static final int MAX_DEPTH = 8;

    private record Unit(CompilationUnit cu, String file) {}

    /** A {@code @Bean} method returning a client type. */
    private record Bean(String type, boolean loadBalanced, String evidence) {}

    private enum Origin { LOCAL, INJECTED, UNTRACED }

    /** Where a client value comes from; {@code type} is its declared or built type, null when not known. */
    private record Trace(String type, Origin origin, boolean loadBalancedAnnotation, String evidence) {
        static Trace untraced(String type, String evidence) {
            return new Trace(type, Origin.UNTRACED, false, evidence);
        }
    }

    /** A literal piece of a URI, or a hole written as {@code {name}} / {@code {?}}. */
    private record Part(String text, boolean literal) {}

    private final List<Bean> beans = new ArrayList<>();
    private Unit unit;

    private ClientCallScanner() {}

    /** Every {@code .java} file under {@code sourceDir}; file names in evidence are relative to {@code root}. */
    public static List<ClientCall> scan(Path root, Path sourceDir) {
        if (!Files.isDirectory(sourceDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new InvalidSpecException(sourceDir + ": cannot list sources: " + e.getMessage(), e);
        }
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            try {
                sources.put(name, Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
            }
        }
        return parse(sources);
    }

    /** @param sources Java source per file name, in the order to scan them */
    public static List<ClientCall> parse(Map<String, String> sources) {
        List<Unit> units = new ArrayList<>();
        sources.forEach((file, source) -> {
            ParseResult<CompilationUnit> result = PARSER.parse(source);
            CompilationUnit cu = result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                    new InvalidSpecException(file + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
            units.add(new Unit(cu, file));
        });
        ClientCallScanner s = new ClientCallScanner();
        for (Unit u : units) {
            s.unit = u;
            s.collectBeans();
        }
        List<ClientCall> calls = new ArrayList<>();
        for (Unit u : units) {
            s.unit = u;
            s.collectCalls(calls);
        }
        calls.sort(Comparator.comparing((ClientCall c) -> c.evidence().substring(0, c.evidence().lastIndexOf(':')))
                .thenComparingInt(c -> lineOf(c.evidence())));
        return calls;
    }

    // ---- beans -----------------------------------------------------------

    private void collectBeans() {
        for (MethodDeclaration m : unit.cu().findAll(MethodDeclaration.class)) {
            if (!hasAnnotation(m.getAnnotations(), "org.springframework.context.annotation.Bean")) {
                continue;
            }
            String type = canonical(m.getType());
            if (type != null) {
                beans.add(new Bean(type, hasAnnotation(m.getAnnotations(), LOAD_BALANCED),
                        unit.file() + ":" + line(m.getName())));
            }
        }
    }

    // ---- requests --------------------------------------------------------

    private void collectCalls(List<ClientCall> out) {
        for (MethodCallExpr call : unit.cu().findAll(MethodCallExpr.class)) {
            Optional<CallableDeclaration> callable = call.findAncestor(CallableDeclaration.class);
            Optional<TypeDeclaration> type = call.findAncestor(TypeDeclaration.class);
            if (callable.isEmpty() || type.isEmpty() || call.getScope().isEmpty()) {
                continue;
            }
            fluentRequest(call, callable.get(), type.get()).or(() -> templateRequest(call, callable.get(), type.get()))
                    .ifPresent(out::add);
        }
    }

    /** {@code <receiver>.get().uri(...)} or {@code <receiver>.method(HttpMethod.X).uri(...)}; {@code call} is the {@code uri}. */
    private Optional<ClientCall> fluentRequest(MethodCallExpr call, CallableDeclaration<?> callable,
            TypeDeclaration<?> type) {
        if (!call.getNameAsString().equals("uri") || call.getArguments().isEmpty()
                || !(call.getScope().get() instanceof MethodCallExpr verb) || verb.getScope().isEmpty()) {
            return Optional.empty();
        }
        String name = verb.getNameAsString();
        String evidence = unit.file() + ":" + line(verb.getName());
        Val<String> method;
        if (verb.getArguments().isEmpty() && name.equals(name.toLowerCase(Locale.ROOT))
                && HttpMethods.METHODS.contains(name.toUpperCase(Locale.ROOT))) {
            method = Val.explicit(name.toUpperCase(Locale.ROOT), evidence);
        } else if (name.equals("method") && verb.getArguments().size() == 1) {
            method = methodArgument(verb.getArgument(0), evidence);
        } else {
            return Optional.empty();
        }
        Trace trace = trace(verb.getScope().get(), callable, type, 0);
        String client = trace.type() == null ? null : trace.type().replace(".Builder", "");
        if (client != null && !FLUENT_CLIENTS.contains(client)) {
            return Optional.empty();
        }
        return Optional.of(request(callable, type, client, trace, method, call.getArgument(0), evidence));
    }

    /** {@code restTemplate.getForObject(url, ...)}, {@code restTemplate.exchange(url, HttpMethod.X, ...)}, ... */
    private Optional<ClientCall> templateRequest(MethodCallExpr call, CallableDeclaration<?> callable,
            TypeDeclaration<?> type) {
        String verb = TEMPLATE_METHODS.get(call.getNameAsString());
        if (verb == null || call.getArguments().isEmpty()) {
            return Optional.empty();
        }
        Trace trace = trace(call.getScope().get(), callable, type, 0);
        if (!"RestTemplate".equals(trace.type())) {
            return Optional.empty();
        }
        String evidence = unit.file() + ":" + line(call.getName());
        Val<String> method;
        if (!verb.isEmpty()) {
            method = Val.explicit(verb, evidence + " (RestTemplate#" + call.getNameAsString() + ")");
        } else if (call.getArguments().size() >= 2) {
            method = methodArgument(call.getArgument(1), evidence);
        } else {
            method = Val.unknown(evidence + ": HTTP method is in a RequestEntity, not read");
        }
        return Optional.of(request(callable, type, "RestTemplate", trace, method, call.getArgument(0), evidence));
    }

    private ClientCall request(CallableDeclaration<?> callable, TypeDeclaration<?> type, String client, Trace trace,
            Val<String> method, Expression uriArg, String evidence) {
        String typeName = type.getFullyQualifiedName().orElse(type.getNameAsString());
        String methodName = callable instanceof ConstructorDeclaration ? "<init>" : callable.getNameAsString();

        List<String> caveats = new ArrayList<>();
        List<Part> parts = fold(uriArg, callable, type, caveats, 0);
        StringBuilder leading = new StringBuilder();
        int literals = 0;
        while (literals < parts.size() && parts.get(literals).literal()) {
            leading.append(parts.get(literals++).text());
        }
        Val<String> host = Val.unknown(evidence + ": host not resolved from " + uriArg);
        String path = render(parts);
        Matcher m = URL.matcher(leading);
        if (m.matches() && (m.group(2) != null || literals == parts.size())) {
            String rest = (m.group(2) == null ? "" : m.group(2)) + render(parts.subList(literals, parts.size()));
            String where = evidence + ": " + uriArg + (caveats.isEmpty() ? "" : " (" + String.join("; ", caveats) + ")");
            host = Val.explicit(m.group(1), where);
            path = path(rest);
        }
        return new ClientCall(typeName + "#" + methodName, client, loadBalanced(trace), method, host, path,
                uriArg.toString(), resilience4j(callable, type), evidence);
    }

    // ---- clients ---------------------------------------------------------

    private Val<Boolean> loadBalanced(Trace t) {
        return switch (t.origin()) {
            case LOCAL -> Val.explicit(false, t.evidence() + ": built in place, without a load balancer");
            case UNTRACED -> Val.unknown(t.evidence());
            case INJECTED -> {
                if (t.loadBalancedAnnotation()) {
                    yield Val.explicit(true, t.evidence() + ": injection point is @LoadBalanced");
                }
                List<Bean> ofType = beans.stream().filter(b -> b.type().equals(t.type())).toList();
                List<String> lb = ofType.stream().filter(Bean::loadBalanced).map(Bean::evidence).toList();
                List<String> plain = ofType.stream().filter(b -> !b.loadBalanced()).map(Bean::evidence).toList();
                if (lb.isEmpty()) {
                    yield Val.explicit(false, t.evidence() + ": no @LoadBalanced " + t.type() + " bean in the module");
                }
                if (!plain.isEmpty()) {
                    yield Val.unknown(t.evidence() + ": the module has @LoadBalanced " + t.type() + " beans ("
                            + String.join(", ", lb) + ") and others (" + String.join(", ", plain)
                            + "); which one is injected depends on qualifiers, not read");
                }
                yield Val.explicit(true, "@LoadBalanced " + t.type() + " bean at " + String.join(", ", lb)
                        + ", injected at " + t.evidence());
            }
        };
    }

    /** Where the client value {@code e}, seen in {@code callable}, comes from. */
    private Trace trace(Expression e, CallableDeclaration<?> callable, TypeDeclaration<?> type, int depth) {
        String where = unit.file() + ":" + line(e);
        if (depth > MAX_DEPTH) {
            return Trace.untraced(null, where + ": client not traced (too deep)");
        }
        if (e instanceof EnclosedExpr en) {
            return trace(en.getInner(), callable, type, depth + 1);
        }
        if (e instanceof MethodCallExpr m) {
            if (m.getScope().isPresent() && m.getScope().get() instanceof NameExpr n
                    && FLUENT_CLIENTS.contains(n.getNameAsString())
                    && (m.getNameAsString().equals("builder") || m.getNameAsString().equals("create"))) {
                return new Trace(n.getNameAsString(), Origin.LOCAL, false, where + " (" + m + ")");
            }
            if (m.getScope().isPresent()) {
                return trace(m.getScope().get(), callable, type, depth + 1);    // builder steps keep the client
            }
            return Trace.untraced(null, where + ": client returned by " + m + ", not traced");
        }
        if (e instanceof ObjectCreationExpr o) {
            String created = canonical(o.getType());
            return created != null
                    ? new Trace(created, Origin.LOCAL, false, where + " (" + o + ")")
                    : Trace.untraced(null, where + ": " + o + " is not a known client type");
        }
        Optional<String> name = variableName(e);
        if (name.isEmpty()) {
            return Trace.untraced(null, where + ": client " + e + " not traced");
        }
        return variable(name.get(), e instanceof NameExpr, callable, type, depth, where);
    }

    private Trace variable(String name, boolean bare, CallableDeclaration<?> callable, TypeDeclaration<?> type,
            int depth, String where) {
        if (bare) {
            Optional<VariableDeclarator> local = local(name, callable);
            if (local.isPresent()) {
                VariableDeclarator v = local.get();
                return v.getInitializer().map(init -> trace(init, callable, type, depth + 1))
                        .orElseGet(() -> Trace.untraced(canonical(v.getType()), where + ": local " + name
                                + " without initializer, not traced"));
            }
            for (Parameter p : callable.getParameters()) {
                if (p.getNameAsString().equals(name)) {
                    String pType = canonical(p.getType());
                    String at = unit.file() + ":" + line(p.getName());
                    if (callable instanceof ConstructorDeclaration
                            || hasAnnotation(callable.getAnnotations(), "org.springframework.context.annotation.Bean")) {
                        return new Trace(pType, Origin.INJECTED, hasAnnotation(p.getAnnotations(), LOAD_BALANCED),
                                "parameter " + name + " at " + at);
                    }
                    return Trace.untraced(pType, at + ": client is the parameter " + name + ", passed by callers");
                }
            }
        }
        for (FieldDeclaration f : type.getFields()) {
            for (VariableDeclarator v : f.getVariables()) {
                if (!v.getNameAsString().equals(name)) {
                    continue;
                }
                String fType = canonical(v.getType());
                String at = unit.file() + ":" + line(v.getName());
                List<Expression> values = new ArrayList<>();
                v.getInitializer().ifPresent(values::add);
                List<AssignExpr> assigns = assignments(name, type);
                assigns.forEach(a -> values.add(a.getValue()));
                if (hasAnnotation(f.getAnnotations(), LOAD_BALANCED)) {
                    return new Trace(fType, Origin.INJECTED, true, "field " + name + " at " + at);
                }
                if (values.isEmpty()) {
                    return new Trace(fType, Origin.INJECTED, false, "field " + name + " at " + at
                            + " (not assigned in the class)");
                }
                if (values.size() > 1) {
                    return Trace.untraced(fType, at + ": field " + name + " is assigned " + values.size()
                            + " times, not traced");
                }
                CallableDeclaration<?> in = assigns.isEmpty() ? callable
                        : assigns.get(0).findAncestor(CallableDeclaration.class).orElse(callable);
                Trace t = trace(values.get(0), in, type, depth + 1);
                return t.type() == null ? new Trace(fType, t.origin(), t.loadBalancedAnnotation(), t.evidence()) : t;
            }
        }
        return Trace.untraced(null, where + ": client " + name + " not traced");
    }

    // ---- URIs ------------------------------------------------------------

    private List<Part> fold(Expression e, CallableDeclaration<?> callable, TypeDeclaration<?> type,
            List<String> caveats, int depth) {
        if (depth > MAX_DEPTH) {
            return List.of(new Part("{?}", false));
        }
        if (e instanceof StringLiteralExpr s) {
            return List.of(new Part(s.asString(), true));
        }
        if (e instanceof TextBlockLiteralExpr t) {
            return List.of(new Part(t.asString(), true));
        }
        if (e instanceof EnclosedExpr en) {
            return fold(en.getInner(), callable, type, caveats, depth + 1);
        }
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Part> out = new ArrayList<>(fold(b.getLeft(), callable, type, caveats, depth + 1));
            out.addAll(fold(b.getRight(), callable, type, caveats, depth + 1));
            return out;
        }
        if (e instanceof MethodCallExpr m && m.getNameAsString().equals("create") && m.getArguments().size() == 1
                && m.getScope().filter(s -> Set.of("URI", "java.net.URI").contains(s.toString())).isPresent()) {
            return fold(m.getArgument(0), callable, type, caveats, depth + 1);
        }
        Optional<String> name = variableName(e);
        if (name.isPresent()) {
            Optional<VariableDeclarator> local = e instanceof NameExpr ? local(name.get(), callable) : Optional.empty();
            if (local.isPresent() && local.get().getInitializer().isPresent()) {
                return fold(local.get().getInitializer().get(), callable, type, caveats, depth + 1);
            }
            if (local.isEmpty()) {
                Optional<VariableDeclarator> field = type.getFields().stream().flatMap(f -> f.getVariables().stream())
                        .filter(v -> v.getNameAsString().equals(name.get())).findFirst();
                if (field.isPresent() && field.get().getInitializer().isPresent()) {
                    for (AssignExpr a : assignments(name.get(), type)) {
                        caveats.add("field " + name.get() + " is also assigned at " + unit.file() + ":" + line(a));
                    }
                    return fold(field.get().getInitializer().get(), callable, type, caveats, depth + 1);
                }
            }
            return List.of(new Part("{" + name.get() + "}", false));
        }
        return List.of(new Part("{?}", false));
    }

    private static String render(List<Part> parts) {
        StringBuilder b = new StringBuilder();
        parts.forEach(p -> b.append(p.text()));
        return b.toString();
    }

    /** {@code :8080/owners/{id}?x=1} → {@code /owners/{id}}. */
    private static String path(String rest) {
        String p = rest.replaceFirst("^:\\d+", "");
        int cut = p.length();
        for (char c : new char[] {'?', '#'}) {
            int i = p.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        p = p.substring(0, cut);
        return p.startsWith("/") ? p : "/" + p;
    }

    // ---- helpers ---------------------------------------------------------

    /** {@code HttpMethod.POST} or a statically imported {@code POST}; anything else is not resolved. */
    private static Val<String> methodArgument(Expression arg, String evidence) {
        String name = null;
        if (arg instanceof FieldAccessExpr f && f.getScope().toString().endsWith("HttpMethod")) {
            name = f.getNameAsString();
        } else if (arg instanceof NameExpr n) {
            name = n.getNameAsString();
        }
        return name != null && HttpMethods.METHODS.contains(name)
                ? Val.explicit(name, evidence)
                : Val.unknown(evidence + ": HTTP method is not a constant: " + arg);
    }

    /** {@code name} or {@code this.name}. */
    private static Optional<String> variableName(Expression e) {
        if (e instanceof NameExpr n) {
            return Optional.of(n.getNameAsString());
        }
        if (e instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr) {
            return Optional.of(f.getNameAsString());
        }
        return Optional.empty();
    }

    private static Optional<VariableDeclarator> local(String name, CallableDeclaration<?> callable) {
        return callable.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(name))
                .filter(v -> v.findAncestor(FieldDeclaration.class).isEmpty())
                .findFirst();
    }

    /** Assignments to the field {@code name} ({@code name = ...} or {@code this.name = ...}) anywhere in the type. */
    private static List<AssignExpr> assignments(String name, TypeDeclaration<?> type) {
        return type.findAll(AssignExpr.class).stream()
                .filter(a -> variableName(a.getTarget()).filter(name::equals).isPresent())
                .filter(a -> !(a.getTarget() instanceof NameExpr) || a.findAncestor(CallableDeclaration.class)
                        .map(c -> (CallableDeclaration<?>) c)
                        .map(c -> local(name, c).isEmpty() && c.getParameters().stream()
                                .noneMatch(p -> p.getNameAsString().equals(name)))
                        .orElse(true))
                .toList();
    }

    private List<String> resilience4j(CallableDeclaration<?> callable, TypeDeclaration<?> type) {
        List<String> out = new ArrayList<>();
        for (NodeList<AnnotationExpr> list : List.of(callable.getAnnotations(), type.getAnnotations())) {
            for (AnnotationExpr a : list) {
                if (R4J_ANNOTATIONS.contains(simpleName(a.getNameAsString())) && resolves(a, R4J)) {
                    out.add("@" + simpleName(a.getNameAsString()) + " at " + unit.file() + ":" + line(a));
                }
            }
        }
        return out;
    }

    private boolean hasAnnotation(NodeList<AnnotationExpr> annotations, String qualified) {
        return annotations.stream().anyMatch(a -> a.getNameAsString().equals(qualified)
                || a.getNameAsString().equals(simpleName(qualified)) && resolves(a, qualified));
    }

    /** Whether {@code a} names {@code qualified}: a type, or with a trailing {@code .} any type in that package tree. */
    private boolean resolves(AnnotationExpr a, String qualified) {
        boolean prefix = qualified.endsWith(".");
        String written = a.getNameAsString();
        if (written.contains(".")) {
            return prefix ? written.startsWith(qualified) : written.equals(qualified);
        }
        for (ImportDeclaration i : unit.cu().getImports()) {
            String imported = i.getNameAsString();
            if (i.isStatic()) {
                continue;
            }
            String type = i.isAsterisk() ? imported + "." + written : imported;
            if (type.endsWith("." + written) && (prefix ? type.startsWith(qualified) : type.equals(qualified))) {
                return true;
            }
        }
        return false;
    }

    /** One of {@link #CLIENT_TYPES}, or null: {@code WebClient.Builder}, an imported {@code Builder}, a qualified name. */
    private String canonical(Type t) {
        if (!(t instanceof ClassOrInterfaceType c)) {
            return null;
        }
        String written = c.getNameWithScope();
        String[] segments = written.split("\\.");
        int first = 0;
        while (first < segments.length - 1 && !Character.isUpperCase(segments[first].charAt(0))) {
            first++;
        }
        String name = String.join(".", List.of(segments).subList(first, segments.length));
        if (name.equals("Builder")) {
            for (ImportDeclaration i : unit.cu().getImports()) {
                String imported = i.getNameAsString();
                for (String client : FLUENT_CLIENTS) {
                    if (!i.isStatic() && !i.isAsterisk() && imported.endsWith("." + client + ".Builder")) {
                        name = client + ".Builder";
                    }
                }
            }
        }
        return CLIENT_TYPES.contains(name) ? name : null;
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static int lineOf(String evidence) {
        return Integer.parseInt(evidence.substring(evidence.lastIndexOf(':') + 1));
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }
}
