package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig.BreakerConfig;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringDurations;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker.WindowType;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the {@code Customizer} beans of the Spring Cloud CircuitBreaker Resilience4j factories (#18): a
 * {@code @Bean} method returning {@code Customizer<ReactiveResilience4JCircuitBreakerFactory>} or
 * {@code Customizer<Resilience4JCircuitBreakerFactory>} as a lambda whose statements call
 * {@code configureDefault(id -> new Resilience4JConfigBuilder(id)...build())} or
 * {@code configure(builder -> builder..., "id", ...)} on the factory.
 *
 * <p>A {@code CircuitBreakerConfig} or {@code TimeLimiterConfig} is read when it is {@code ofDefaults()} or a
 * {@code custom()...build()} chain with literal arguments; a builder that leaves one out starts from
 * {@code ofDefaults()}, as {@code Resilience4JConfigBuilder} does. Any other expression gives UNKNOWN values that
 * name it. A customizer it cannot follow at all (another statement, a class implementing {@code Customizer}, ids that
 * are not literals) is recorded as unread, and the factory's programmatic configuration is then UNKNOWN as a whole.
 * Checked against the real factory by {@code SpringCloudCircuitBreakerOracleTest}.
 */
public final class CustomizerScanner {

    /** The two Resilience4j factories of Spring Cloud CircuitBreaker. */
    public enum Factory {
        REACTIVE("ReactiveResilience4JCircuitBreakerFactory"),
        BLOCKING("Resilience4JCircuitBreakerFactory");

        private final String type;

        Factory(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }

        static Optional<Factory> of(String simpleName) {
            return Stream.of(values()).filter(f -> f.type.equals(simpleName)).findFirst();
        }
    }

    /**
     * One {@code Resilience4JConfigBuilder} as the code builds it.
     *
     * @param where where it is built, {@code file:line (...)}
     */
    public record ProgrammaticConfig(BreakerConfig breaker, Val<Duration> timeLimit, String where) {}

    /**
     * What the customizers of one factory set.
     *
     * @param defaultConfig the last {@code configureDefault}, or null
     * @param byId the {@code configure(..., ids)} configs
     * @param unread customizers or statements not followed; when not empty the configuration is UNKNOWN
     */
    public record Customizations(Factory factory, ProgrammaticConfig defaultConfig,
            Map<String, ProgrammaticConfig> byId, List<String> unread) {
        public Customizations {
            byId = Map.copyOf(byId);
            unread = List.copyOf(unread);
        }

        /** No customizer: the factory keeps its registry defaults. */
        public static Customizations none(Factory factory) {
            return new Customizations(factory, null, Map.of(), List.of());
        }

        /** Sources not searched: whatever a customizer would set is UNKNOWN. */
        public static Customizations notSearched(Factory factory, String why) {
            return new Customizations(factory, null, Map.of(), List.of(why));
        }
    }

    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
    private static final String TL_KB = Resilience4jConfig.TIME_LIMITER_KB;
    private static final String CB_KB = Resilience4jConfig.BREAKER_KB;

    private final DefaultsKb kb;
    private final Map<Factory, Builder> found = new EnumMap<>(Factory.class);

    private CustomizerScanner(DefaultsKb kb) {
        this.kb = kb;
        for (Factory f : Factory.values()) {
            found.put(f, new Builder());
        }
    }

    /** Scans the {@code .java} files under {@code sourceDir}; file names relative to {@code root}. */
    public static Map<Factory, Customizations> scan(Path root, Path sourceDir, DefaultsKb kb) {
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
        return parse(sources, kb);
    }

    /** @param sources Java source per file name, in the order to scan them */
    public static Map<Factory, Customizations> parse(Map<String, String> sources, DefaultsKb kb) {
        CustomizerScanner s = new CustomizerScanner(kb);
        sources.forEach((file, source) -> {
            ParseResult<CompilationUnit> result = PARSER.parse(source);
            CompilationUnit cu = result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                    new InvalidSpecException(file + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
            s.unit(cu, file);
        });
        Map<Factory, Customizations> out = new EnumMap<>(Factory.class);
        s.found.forEach((f, b) -> out.put(f, b.build(f)));
        return out;
    }

    /** Collects per factory; two beans that set the same thing conflict, because their order is not read. */
    private static final class Builder {
        final List<Bean> beans = new ArrayList<>();
        final List<String> unread = new ArrayList<>();

        Customizations build(Factory f) {
            ProgrammaticConfig def = null;
            String defBean = null;
            Map<String, ProgrammaticConfig> byId = new LinkedHashMap<>();
            Map<String, String> idBean = new LinkedHashMap<>();
            List<String> conflicts = new ArrayList<>();
            for (Bean b : beans) {
                if (b.defaultConfig != null) {
                    if (def != null) {
                        conflicts.add(b.where + " and " + defBean + " both call configureDefault; the order of "
                                + "Customizer beans is not read");
                    }
                    def = b.defaultConfig;
                    defBean = b.where;
                }
                for (Map.Entry<String, ProgrammaticConfig> e : b.byId.entrySet()) {
                    String other = idBean.put(e.getKey(), b.where);
                    if (other != null) {
                        conflicts.add(b.where + " and " + other + " both configure '" + e.getKey()
                                + "'; the order of Customizer beans is not read");
                    }
                    byId.put(e.getKey(), e.getValue());
                }
            }
            List<String> all = new ArrayList<>(unread);
            all.addAll(conflicts);
            return new Customizations(f, def, byId, all);
        }
    }

    private static final class Bean {
        final String where;
        ProgrammaticConfig defaultConfig;
        final Map<String, ProgrammaticConfig> byId = new LinkedHashMap<>();

        Bean(String where) {
            this.where = where;
        }
    }

    // ---- beans -----------------------------------------------------------

    private void unit(CompilationUnit cu, String file) {
        List<ClassOrInterfaceType> read = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            Optional<Factory> f = customizedFactory(m.getType());
            if (f.isEmpty() || m.getAnnotationByName("Bean").isEmpty()) {
                continue;
            }
            read.add((ClassOrInterfaceType) m.getType());
            String where = at(file, m) + " (@Bean " + m.getNameAsString() + ", Customizer<" + f.get().type() + ">)";
            bean(f.get(), m, where, "@Bean " + m.getNameAsString(), file);
        }
        for (ClassOrInterfaceType t : cu.findAll(ClassOrInterfaceType.class)) {
            Optional<Factory> f = customizedFactory(t);
            if (f.isPresent() && read.stream().noneMatch(r -> r == t)) {
                found.get(f.get()).unread.add(at(file, t) + ": Customizer<" + f.get().type()
                        + "> outside a @Bean method returning a lambda is not read");
            }
        }
    }

    private static Optional<Factory> customizedFactory(Type type) {
        if (!(type instanceof ClassOrInterfaceType t) || !t.getNameAsString().equals("Customizer")) {
            return Optional.empty();
        }
        return t.getTypeArguments().filter(a -> a.size() == 1)
                .flatMap(a -> a.get(0) instanceof ClassOrInterfaceType arg ? Factory.of(arg.getNameAsString())
                        : Optional.empty());
    }

    private void bean(Factory f, MethodDeclaration m, String where, String label, String file) {
        Builder builder = found.get(f);
        Optional<LambdaExpr> lambda = m.getBody()
                .filter(b -> b.getStatements().size() == 1 && b.getStatement(0) instanceof ReturnStmt)
                .flatMap(b -> ((ReturnStmt) b.getStatement(0)).getExpression())
                .filter(Expression::isLambdaExpr).map(Expression::asLambdaExpr)
                .filter(l -> l.getParameters().size() == 1);
        if (lambda.isEmpty()) {
            builder.unread.add(where + ": not a single 'return factory -> ...' statement; not read");
            return;
        }
        String factory = lambda.get().getParameter(0).getNameAsString();
        Bean bean = new Bean(where);
        for (Expression call : statements(lambda.get())) {
            if (!(call instanceof MethodCallExpr c) || !isName(c.getScope(), factory)) {
                builder.unread.add(at(file, call) + ": " + call + " in " + where + " is not read");
                continue;
            }
            switch (c.getNameAsString()) {
                case "configureDefault" -> {
                    if (c.getArguments().size() != 1) {
                        builder.unread.add(at(file, c) + ": configureDefault in " + where + " is not read");
                        continue;
                    }
                    bean.defaultConfig = configureDefault(c.getArgument(0), file, "configureDefault of " + label);
                }
                case "configure" -> {
                    List<String> ids = new ArrayList<>();
                    for (Expression id : c.getArguments().subList(Math.min(1, c.getArguments().size()),
                            c.getArguments().size())) {
                        if (id instanceof StringLiteralExpr s) {
                            ids.add(s.asString());
                        } else {
                            ids = null;
                            break;
                        }
                    }
                    if (ids == null || ids.isEmpty()) {
                        builder.unread.add(at(file, c) + ": configure in " + where
                                + " names its ids with something other than string literals; not read");
                        continue;
                    }
                    ProgrammaticConfig config = configure(c.getArgument(0), file,
                            "configure(..., " + String.join(", ", ids.stream().map(i -> "\"" + i + "\"").toList())
                                    + ") of " + label);
                    ids.forEach(id -> bean.byId.put(id, config));
                }
                // decorates the CircuitBreaker instance (event consumers); its config stays as built
                case "addCircuitBreakerCustomizer" -> { }
                default -> builder.unread.add(at(file, c) + ": " + c.getNameAsString() + " in " + where
                        + " is not read");
            }
        }
        builder.beans.add(bean);
    }

    private static List<Expression> statements(LambdaExpr l) {
        Statement body = l.getBody();
        if (body instanceof ExpressionStmt e) {
            return List.of(e.getExpression());
        }
        List<Expression> out = new ArrayList<>();
        for (Statement s : ((BlockStmt) body).getStatements()) {
            out.add(s instanceof ExpressionStmt e ? e.getExpression() : new NameExpr(s.toString().strip()));
        }
        return out;
    }

    /** {@code id -> new Resilience4JConfigBuilder(id)...build()}. */
    private ProgrammaticConfig configureDefault(Expression arg, String file, String ctx) {
        Optional<Expression> body = lambdaResult(arg);
        if (body.isPresent()) {
            List<MethodCallExpr> chain = chain(body.get());
            Expression root = chain.isEmpty() ? body.get() : chain.get(0).getScope().orElseThrow();
            boolean built = !chain.isEmpty() && chain.get(chain.size() - 1).getNameAsString().equals("build");
            if (built && root instanceof ObjectCreationExpr o
                    && o.getType().getNameAsString().equals("Resilience4JConfigBuilder")) {
                return builderChain(chain.subList(0, chain.size() - 1), file, ctx, at(file, arg));
            }
        }
        return unknown(at(file, arg) + ": " + ctx + " is not a Resilience4JConfigBuilder chain");
    }

    /** {@code builder -> builder.circuitBreakerConfig(...).timeLimiterConfig(...)}. */
    private ProgrammaticConfig configure(Expression arg, String file, String ctx) {
        if (arg instanceof LambdaExpr l && l.getParameters().size() == 1 && l.getExpressionBody().isPresent()) {
            String param = l.getParameter(0).getNameAsString();
            List<MethodCallExpr> chain = chain(l.getExpressionBody().get());
            if (!chain.isEmpty() && isName(chain.get(0).getScope(), param)) {
                return builderChain(chain, file, ctx, at(file, arg));
            }
        }
        return unknown(at(file, arg) + ": " + ctx + " is not a Resilience4JConfigBuilder chain");
    }

    private static Optional<Expression> lambdaResult(Expression arg) {
        if (!(arg instanceof LambdaExpr l) || l.getParameters().size() != 1) {
            return Optional.empty();
        }
        if (l.getExpressionBody().isPresent()) {
            return l.getExpressionBody();
        }
        BlockStmt b = (BlockStmt) l.getBody();
        return b.getStatements().size() == 1 && b.getStatement(0) instanceof ReturnStmt r
                ? r.getExpression() : Optional.empty();
    }

    /** @param ctx e.g. {@code configureDefault of @Bean defaultCustomizer}; @param at where the builder is */
    private ProgrammaticConfig builderChain(List<MethodCallExpr> calls, String file, String ctx, String at) {
        BreakerConfig breaker = breakerDefaults(at + ": no CircuitBreakerConfig in " + ctx
                + ", so Resilience4JConfigBuilder's CircuitBreakerConfig.ofDefaults() applies");
        Val<Duration> timeLimit = kb.defaultOr(TL_KB, "timeoutDuration", CustomizerScanner::kbDuration,
                at + ": no TimeLimiterConfig in " + ctx
                        + ", so Resilience4JConfigBuilder's TimeLimiterConfig.ofDefaults() applies");
        for (MethodCallExpr c : calls) {
            String name = c.getNameAsString();
            if (name.equals("circuitBreakerConfig") && c.getArguments().size() == 1) {
                breaker = breakerConfig(c.getArgument(0), file, ctx);
            } else if (name.equals("timeLimiterConfig") && c.getArguments().size() == 1) {
                timeLimit = timeLimiterConfig(c.getArgument(0), file, ctx);
            } else {
                return unknown(at(file, c) + ": " + name + " in " + ctx + " is not read");
            }
        }
        return new ProgrammaticConfig(breaker, timeLimit, at + " (" + ctx + ")");
    }

    // ---- CircuitBreakerConfig ---------------------------------------------

    private BreakerConfig breakerConfig(Expression e, String file, String ctx) {
        String at = at(file, e);
        if (isStaticCall(e, "CircuitBreakerConfig", "ofDefaults")) {
            return breakerDefaults(at + ": CircuitBreakerConfig.ofDefaults() in " + ctx);
        }
        List<MethodCallExpr> chain = chain(e);
        if (chain.size() < 2 || !isStaticCall(chain.get(0), "CircuitBreakerConfig", "custom")
                || !chain.get(chain.size() - 1).getNameAsString().equals("build")) {
            return unknownBreaker(at + ": " + e + " in " + ctx + " is not CircuitBreakerConfig.ofDefaults() or a "
                    + "custom()...build() chain");
        }
        BreakerConfig c = breakerDefaults(at + ": not set in the CircuitBreakerConfig.custom() of " + ctx
                + ", which starts from the defaults");
        for (MethodCallExpr call : chain.subList(1, chain.size() - 1)) {
            String in = " in " + ctx;
            switch (call.getNameAsString()) {
                case "failureRateThreshold" -> c = new BreakerConfig(convert(number(call, 0, file, in), Number::doubleValue),
                        c.slidingWindowType(), c.slidingWindowSize(), c.minimumNumberOfCalls(), c.waitDurationInOpenState());
                case "slidingWindowType" -> c = new BreakerConfig(c.failureRateThreshold(), windowType(call, 0, file, in),
                        c.slidingWindowSize(), c.minimumNumberOfCalls(), c.waitDurationInOpenState());
                case "slidingWindowSize" -> c = new BreakerConfig(c.failureRateThreshold(), c.slidingWindowType(),
                        convert(number(call, 0, file, in), Number::intValue), c.minimumNumberOfCalls(), c.waitDurationInOpenState());
                case "minimumNumberOfCalls" -> c = new BreakerConfig(c.failureRateThreshold(), c.slidingWindowType(),
                        c.slidingWindowSize(), convert(number(call, 0, file, in), Number::intValue), c.waitDurationInOpenState());
                case "slidingWindow" -> c = new BreakerConfig(c.failureRateThreshold(), windowType(call, 2, file, in),
                        convert(number(call, 0, file, in), Number::intValue), convert(number(call, 1, file, in), Number::intValue),
                        c.waitDurationInOpenState());
                case "waitDurationInOpenState" -> c = new BreakerConfig(c.failureRateThreshold(), c.slidingWindowType(),
                        c.slidingWindowSize(), c.minimumNumberOfCalls(), duration(call, file, in));
                case "waitIntervalFunctionInOpenState" -> c = new BreakerConfig(c.failureRateThreshold(),
                        c.slidingWindowType(), c.slidingWindowSize(), c.minimumNumberOfCalls(),
                        Val.unknown(at(file, call) + ": an interval function sets the wait in the open state" + in));
                default -> { } // settings ADR 0015 does not model
            }
        }
        return c;
    }

    private BreakerConfig breakerDefaults(String why) {
        return new BreakerConfig(
                kb.defaultOr(CB_KB, "failureRateThreshold", Double::valueOf, why),
                kb.defaultOr(CB_KB, "slidingWindowType", WindowType::valueOf, why),
                kb.defaultOr(CB_KB, "slidingWindowSize", Integer::valueOf, why),
                kb.defaultOr(CB_KB, "minimumNumberOfCalls", Integer::valueOf, why),
                kb.defaultOr(CB_KB, "waitDurationInOpenState", CustomizerScanner::kbDuration, why));
    }

    private static BreakerConfig unknownBreaker(String why) {
        return new BreakerConfig(Val.unknown(why), Val.unknown(why), Val.unknown(why), Val.unknown(why),
                Val.unknown(why));
    }

    private static ProgrammaticConfig unknown(String why) {
        return new ProgrammaticConfig(unknownBreaker(why), Val.unknown(why), why);
    }

    // ---- TimeLimiterConfig ------------------------------------------------

    private Val<Duration> timeLimiterConfig(Expression e, String file, String ctx) {
        String at = at(file, e);
        if (isStaticCall(e, "TimeLimiterConfig", "ofDefaults")) {
            return kb.defaultOr(TL_KB, "timeoutDuration", CustomizerScanner::kbDuration,
                    at + ": TimeLimiterConfig.ofDefaults() in " + ctx);
        }
        List<MethodCallExpr> chain = chain(e);
        if (chain.size() < 2 || !isStaticCall(chain.get(0), "TimeLimiterConfig", "custom")
                || !chain.get(chain.size() - 1).getNameAsString().equals("build")) {
            return Val.unknown(at + ": " + e + " in " + ctx + " is not TimeLimiterConfig.ofDefaults() or a "
                    + "custom()...build() chain");
        }
        Val<Duration> timeout = kb.defaultOr(TL_KB, "timeoutDuration", CustomizerScanner::kbDuration,
                at + ": timeoutDuration not set in the TimeLimiterConfig.custom() of " + ctx);
        for (MethodCallExpr call : chain.subList(1, chain.size() - 1)) {
            if (call.getNameAsString().equals("timeoutDuration")) {
                timeout = duration(call, file, " in " + ctx);
            }
        }
        return timeout;
    }

    // ---- literals ----------------------------------------------------------

    private static Val<Number> number(MethodCallExpr call, int index, String file, String in) {
        if (call.getArguments().size() <= index) {
            return Val.unknown(at(file, call) + ": " + call.getNameAsString() + " is not read" + in);
        }
        Expression a = call.getArgument(index);
        String at = at(file, a);
        try {
            if (a instanceof IntegerLiteralExpr i) {
                return Val.explicit(i.asNumber(), at + ": " + call.getNameAsString() + "(" + a + ")" + in);
            }
            if (a instanceof LongLiteralExpr l) {
                return Val.explicit(l.asNumber(), at + ": " + call.getNameAsString() + "(" + a + ")" + in);
            }
            if (a instanceof DoubleLiteralExpr d) {
                return Val.explicit(d.asDouble(), at + ": " + call.getNameAsString() + "(" + a + ")" + in);
            }
        } catch (NumberFormatException ex) {
            // falls through to UNKNOWN
        }
        return Val.unknown(at + ": " + call.getNameAsString() + "(" + a + ") is not a literal" + in);
    }

    private static Val<WindowType> windowType(MethodCallExpr call, int index, String file, String in) {
        if (call.getArguments().size() > index) {
            Expression a = call.getArgument(index);
            String constant = a instanceof FieldAccessExpr f ? f.getNameAsString()
                    : a instanceof NameExpr n ? n.getNameAsString() : null;
            if (constant != null) {
                try {
                    return Val.explicit(WindowType.valueOf(constant), at(file, a) + ": " + call.getNameAsString()
                            + "(... " + a + ")" + in);
                } catch (IllegalArgumentException ex) {
                    // falls through to UNKNOWN
                }
            }
            return Val.unknown(at(file, a) + ": " + a + " is not a SlidingWindowType constant" + in);
        }
        return Val.unknown(at(file, call) + ": " + call.getNameAsString() + " is not read" + in);
    }

    /** {@code Duration.ofMillis(n)} and its kin with a literal, {@code Duration.ZERO} or {@code Duration.parse("...")}. */
    private static Val<Duration> duration(MethodCallExpr call, String file, String in) {
        if (call.getArguments().size() != 1) {
            return Val.unknown(at(file, call) + ": " + call.getNameAsString() + " is not read" + in);
        }
        Expression a = call.getArgument(0);
        String at = at(file, a);
        String said = at + ": " + call.getNameAsString() + "(" + a + ")" + in;
        if (a instanceof FieldAccessExpr f && f.getNameAsString().equals("ZERO") && isName(Optional.of(f.getScope()), "Duration")) {
            return Val.explicit(Duration.ZERO, said);
        }
        if (a instanceof MethodCallExpr m && isName(m.getScope(), "Duration") && m.getArguments().size() == 1) {
            Expression v = m.getArgument(0);
            try {
                if (m.getNameAsString().equals("parse") && v instanceof StringLiteralExpr s) {
                    return Val.explicit(Duration.parse(s.asString()), said);
                }
                Long n = v instanceof IntegerLiteralExpr i ? Long.valueOf(i.asNumber().longValue())
                        : v instanceof LongLiteralExpr l ? Long.valueOf(l.asNumber().longValue()) : null;
                if (n != null) {
                    Duration d = switch (m.getNameAsString()) {
                        case "ofNanos" -> Duration.ofNanos(n);
                        case "ofMillis" -> Duration.ofMillis(n);
                        case "ofSeconds" -> Duration.ofSeconds(n);
                        case "ofMinutes" -> Duration.ofMinutes(n);
                        case "ofHours" -> Duration.ofHours(n);
                        case "ofDays" -> Duration.ofDays(n);
                        default -> null;
                    };
                    if (d != null) {
                        return Val.explicit(d, said);
                    }
                }
            } catch (RuntimeException ex) {
                // falls through to UNKNOWN
            }
        }
        return Val.unknown(at + ": " + call.getNameAsString() + "(" + a + ") is not a literal duration" + in);
    }

    // ---- helpers -----------------------------------------------------------

    /** The calls of {@code a.b().c()}, innermost first; the scope of the first is the chain's root. */
    private static List<MethodCallExpr> chain(Expression e) {
        List<MethodCallExpr> calls = new ArrayList<>();
        Expression cur = e;
        while (cur instanceof MethodCallExpr m) {
            calls.add(0, m);
            if (m.getScope().isEmpty()) {
                break;
            }
            cur = m.getScope().get();
        }
        return calls;
    }

    private static <A, B> Val<B> convert(Val<A> v, java.util.function.Function<A, B> f) {
        return v.isKnown() ? new Val<>(f.apply(v.value()), v.source(), v.evidence()) : Val.unknown(v.evidence());
    }

    private static boolean isStaticCall(Expression e, String type, String method) {
        return e instanceof MethodCallExpr m && m.getNameAsString().equals(method) && m.getArguments().isEmpty()
                && m.getScope().filter(s -> s instanceof NameExpr n && n.getNameAsString().equals(type)
                        || s instanceof FieldAccessExpr f && f.getNameAsString().equals(type)).isPresent();
    }

    private static boolean isName(Optional<Expression> scope, String name) {
        return scope.filter(s -> s instanceof NameExpr n && n.getNameAsString().equals(name)).isPresent();
    }

    private static String at(String file, Node n) {
        return file + ":" + n.getBegin().map(p -> p.line).orElse(1);
    }

    private static Duration kbDuration(String text) {
        return SpringDurations.parse(text).orElseThrow(() ->
                new IllegalStateException("knowledge base duration '" + text + "' is not valid"));
    }
}
