package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds where code wraps a call in a Spring Cloud CircuitBreaker (#18): {@code factory.create("id")} on a
 * {@code ReactiveCircuitBreakerFactory} or {@code ReactiveResilience4JCircuitBreakerFactory}, and the {@code run(...)}
 * that uses it, inline ({@code factory.create("id").run(mono, fallback)}) or through a local variable.
 *
 * <p>What {@code run} wraps is its first argument. A lambda parameter of {@code transform}, {@code transformDeferred}
 * or {@code as} stands for the publisher those are called on, and a local variable for its initialiser. The wrapped
 * expression then names the requests it makes in two ways: requests written inside it, by position, and a call
 * {@code receiver.method(...)} at the root of its chain on a field, parameter or local of type {@code T}, which wraps
 * every request in {@code T#method} (one level deep, as {@code ClientCallScanner} names methods). Petclinic's
 * {@code ApiGatewayController} wraps {@code visitsServiceClient.getVisitsForPets(...)} this way.
 *
 * <p>The blocking factories ({@code CircuitBreakerFactory}, {@code Resilience4JCircuitBreakerFactory}) and
 * {@code create(id, group)} are noted and not modelled. Types are matched by simple name.
 */
public final class BreakerCallScanner {

    /**
     * One {@code run} of a breaker.
     *
     * @param id      the id given to {@code create}, or null when it is not a string literal
     * @param create  {@code file:line} of the {@code create} call
     * @param file    the file the wrapped expression is in
     * @param from    first line of the wrapped expression
     * @param to      last line of the wrapped expression
     * @param target  {@code T#method} the wrapped expression calls at its root ({@code T} fully qualified when an
     *                import or the package says so), or null
     * @param wraps   the wrapped expression as written
     * @param fallback the fallback given to {@code run} as written, or null when there is none
     */
    public record Wrap(String id, String create, String file, int from, int to, String target, String wraps,
            String fallback) {

        /** Whether a request at {@code evidence} ({@code file:line}) in {@code qualifiedMethod} is inside this run. */
        public boolean covers(String qualifiedMethod, String evidence) {
            int colon = evidence.lastIndexOf(':');
            if (colon > 0 && evidence.substring(0, colon).equals(file)) {
                try {
                    int line = Integer.parseInt(evidence.substring(colon + 1));
                    if (line >= from && line <= to) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // not a line: fall through to the method
                }
            }
            return target != null && (qualifiedMethod.equals(target)
                    || !target.contains(".") && qualifiedMethod.endsWith("." + target));
        }
    }

    public record Result(List<Wrap> wraps, List<String> notes) {
        public Result {
            wraps = List.copyOf(wraps);
            notes = List.copyOf(notes);
        }
    }

    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
    private static final Set<String> REACTIVE = Set.of("ReactiveCircuitBreakerFactory",
            "ReactiveResilience4JCircuitBreakerFactory");
    private static final Set<String> BLOCKING = Set.of("CircuitBreakerFactory", "Resilience4JCircuitBreakerFactory");
    private static final Set<String> TRANSFORMS = Set.of("transform", "transformDeferred", "as");

    private final List<Wrap> wraps = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    private BreakerCallScanner() {}

    /** Scans the {@code .java} files under {@code sourceDir}; file names relative to {@code root}. */
    public static Result scan(Path root, Path sourceDir) {
        Map<String, String> sources = new LinkedHashMap<>();
        if (Files.isDirectory(sourceDir)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            } catch (IOException e) {
                throw new InvalidSpecException(sourceDir + ": cannot list sources: " + e.getMessage(), e);
            }
            for (Path file : files) {
                String name = root.relativize(file).toString().replace('\\', '/');
                try {
                    sources.put(name, Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
                }
            }
        }
        return parse(sources);
    }

    /** @param sources Java source per file name, in the order to scan them */
    public static Result parse(Map<String, String> sources) {
        BreakerCallScanner s = new BreakerCallScanner();
        sources.forEach((file, source) -> {
            ParseResult<CompilationUnit> result = PARSER.parse(source);
            CompilationUnit cu = result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                    new InvalidSpecException(file + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
            s.unit(cu, file);
        });
        return new Result(s.wraps, s.notes);
    }

    private void unit(CompilationUnit cu, String file) {
        for (MethodCallExpr create : cu.findAll(MethodCallExpr.class)) {
            if (!create.getNameAsString().equals("create") || create.getArguments().isEmpty()
                    || create.getScope().isEmpty()) {
                continue;
            }
            Optional<String> type = receiverName(create.getScope().get()).flatMap(n -> declaredType(n, create));
            if (type.isEmpty()) {
                continue;
            }
            String at = at(file, create);
            if (BLOCKING.contains(type.get())) {
                notes.add(at + ": " + create + " on a blocking " + type.get()
                        + " is not modelled; only the reactive factories are (#18)");
                continue;
            }
            if (!REACTIVE.contains(type.get())) {
                continue;
            }
            if (create.getArguments().size() != 1) {
                notes.add(at + ": " + create + " names a group; create(id, group) is not modelled");
                continue;
            }
            String id = create.getArgument(0) instanceof StringLiteralExpr s ? s.asString() : null;
            List<MethodCallExpr> runs = runs(create);
            if (runs.isEmpty()) {
                notes.add(at + ": " + create + " is not run where it is created; the calls it wraps are not known");
                continue;
            }
            for (MethodCallExpr run : runs) {
                if (run.getArguments().isEmpty()) {
                    continue;
                }
                Expression wrapped = wrapped(run.getArgument(0), 0);
                String fallback = run.getArguments().size() > 1 && !run.getArgument(1).isNullLiteralExpr()
                        ? run.getArgument(1).toString() : null;
                wraps.add(new Wrap(id, at, file, line(wrapped, true), line(wrapped, false), target(wrapped, cu),
                        wrapped.toString(), fallback));
            }
        }
    }

    /** {@code create(...).run(...)}, or {@code cb.run(...)} on a local {@code cb} initialised with it. */
    private static List<MethodCallExpr> runs(MethodCallExpr create) {
        Optional<Node> parent = create.getParentNode();
        if (parent.isPresent() && parent.get() instanceof MethodCallExpr run && run.getNameAsString().equals("run")
                && run.getScope().filter(s -> s == create).isPresent()) {
            return List.of(run);
        }
        if (parent.isPresent() && parent.get() instanceof VariableDeclarator v) {
            String name = v.getNameAsString();
            Node scope = v.findAncestor(com.github.javaparser.ast.stmt.BlockStmt.class).map(b -> (Node) b)
                    .orElse(v.findRootNode());
            return scope.findAll(MethodCallExpr.class).stream()
                    .filter(m -> m.getNameAsString().equals("run")
                            && m.getScope().filter(s -> s instanceof NameExpr n && n.getNameAsString().equals(name))
                                    .isPresent())
                    .toList();
        }
        return List.of();
    }

    /** What a {@code run} argument stands for: through {@code transform(it -> ...)} and local variables. */
    private static Expression wrapped(Expression e, int depth) {
        if (depth > 8 || !(e instanceof NameExpr n)) {
            return e;
        }
        String name = n.getNameAsString();
        for (Node a = n.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof LambdaExpr l && l.getParameters().stream().anyMatch(p -> p.getNameAsString().equals(name))) {
                if (l.getParentNode().orElse(null) instanceof MethodCallExpr t && TRANSFORMS.contains(t.getNameAsString())
                        && t.getScope().isPresent() && t.getArguments().contains(l)) {
                    return wrapped(t.getScope().get(), depth + 1);
                }
                return e;
            }
            if (a instanceof CallableDeclaration<?>) {
                break;
            }
        }
        return localInitialiser(n).map(i -> wrapped(i, depth + 1)).orElse(e);
    }

    /** {@code T#method} for a chain whose root is {@code receiver.method(...)} or {@code method(...)}. */
    private static String target(Expression wrapped, CompilationUnit cu) {
        Expression cur = wrapped;
        MethodCallExpr root = null;
        while (cur instanceof MethodCallExpr m) {
            root = m;
            if (m.getScope().isEmpty()) {
                break;
            }
            cur = m.getScope().get();
        }
        if (root == null) {
            return null;
        }
        MethodCallExpr r = root;
        if (r.getScope().isEmpty() || r.getScope().get() instanceof ThisExpr) {
            return r.findAncestor(TypeDeclaration.class)
                    .map(t -> ((TypeDeclaration<?>) t).getFullyQualifiedName().orElse(t.getNameAsString())
                            + "#" + r.getNameAsString())
                    .orElse(null);
        }
        return receiverName(root.getScope().get()).flatMap(n -> declaredType(n, r))
                .map(t -> qualified(t, cu) + "#" + r.getNameAsString()).orElse(null);
    }

    /** The name of {@code x} or {@code this.x}. */
    private static Optional<String> receiverName(Expression scope) {
        if (scope instanceof NameExpr n) {
            return Optional.of(n.getNameAsString());
        }
        if (scope instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr) {
            return Optional.of(f.getNameAsString());
        }
        return Optional.empty();
    }

    /** The simple type name of the parameter, local or field {@code name} visible at {@code at}. */
    private static Optional<String> declaredType(String name, Node at) {
        for (Node a = at.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof LambdaExpr l) {
                for (Parameter p : l.getParameters()) {
                    if (p.getNameAsString().equals(name)) {
                        return simple(p.getType());
                    }
                }
            }
            if (a instanceof CallableDeclaration<?> c) {
                for (Parameter p : c.getParameters()) {
                    if (p.getNameAsString().equals(name)) {
                        return simple(p.getType());
                    }
                }
                Optional<VariableDeclarator> local = c.findAll(VariableDeclarator.class).stream()
                        .filter(v -> v.getNameAsString().equals(name) && !(v.getParentNode().orElse(null)
                                instanceof FieldDeclaration))
                        .findFirst();
                if (local.isPresent()) {
                    return simple(local.get().getType());
                }
            }
            if (a instanceof TypeDeclaration<?> t) {
                for (FieldDeclaration f : t.getFields()) {
                    for (VariableDeclarator v : f.getVariables()) {
                        if (v.getNameAsString().equals(name)) {
                            return simple(v.getType());
                        }
                    }
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Expression> localInitialiser(NameExpr n) {
        return n.findAncestor(CallableDeclaration.class).flatMap(c -> ((CallableDeclaration<?>) c)
                .findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(n.getNameAsString())
                        && !(v.getParentNode().orElse(null) instanceof FieldDeclaration))
                .findFirst())
                .flatMap(VariableDeclarator::getInitializer);
    }

    private static Optional<String> simple(Type type) {
        return type instanceof ClassOrInterfaceType t ? Optional.of(t.getNameAsString()) : Optional.empty();
    }

    /** {@code T} fully qualified through an import of it or the file's package; else as written. */
    private static String qualified(String simple, CompilationUnit cu) {
        for (ImportDeclaration i : cu.getImports()) {
            if (!i.isAsterisk() && !i.isStatic() && i.getName().getIdentifier().equals(simple)) {
                return i.getNameAsString();
            }
        }
        boolean declaredHere = cu.getTypes().stream().anyMatch(t -> t.getNameAsString().equals(simple));
        if (declaredHere || cu.getImports().stream().noneMatch(ImportDeclaration::isAsterisk)) {
            return cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).map(p -> p + "." + simple)
                    .orElse(simple);
        }
        return simple;
    }

    private static int line(Node n, boolean begin) {
        return (begin ? n.getBegin() : n.getEnd()).map(p -> p.line).orElse(1);
    }

    private static String at(String file, Node n) {
        return file + ":" + line(n, true);
    }
}
