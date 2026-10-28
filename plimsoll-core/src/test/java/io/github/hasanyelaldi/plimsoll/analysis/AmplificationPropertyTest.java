package io.github.hasanyelaldi.plimsoll.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class AmplificationPropertyTest {

    @Property
    void chainAmplificationIsTheProductOfItsAttempts(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 6) Integer> attempts) {
        AnalysisResult r = Specs.analyze(Specs.chain(attempts.stream().mapToInt(Integer::intValue).toArray()));

        BigInteger product = BigInteger.ONE;
        for (int i = 0; i < attempts.size(); i++) {
            product = product.multiply(BigInteger.valueOf(attempts.get(i)));
            assertThat(Specs.load(r, "s" + (i + 1))).isEqualTo(product);
        }
    }

    /** A DAG on s0..s5 from edge codes: code c is the edge s(c/6) -> s(c%6) when c/6 < c%6. */
    private static List<int[]> dag(List<Integer> codes) {
        List<int[]> edges = new ArrayList<>();
        for (int k = 0; k < codes.size(); k++) {
            int from = codes.get(k) / 6;
            int to = codes.get(k) % 6;
            if (from < to) {
                edges.add(new int[] {from, to, 1 + k % 3});
            }
        }
        return edges;
    }

    private static String spec(List<int[]> edges, boolean choices) {
        StringBuilder y = new StringBuilder("services:\n  s0: { entry: true }\n");
        for (int i = 1; i < 6; i++) {
            y.append("  s").append(i).append(": {}\n");
        }
        y.append(edges.isEmpty() ? "edges: []\n" : "edges:\n");
        for (int k = 0; k < edges.size(); k++) {
            int[] e = edges.get(k);
            y.append("  - { from: s").append(e[0]).append(", to: s").append(e[1]).append(", operation: op").append(k)
                    .append(choices ? ", choice: routes" : "")
                    .append(", layers: [ { kind: generic, totalAttempts: ").append(e[2]).append(" } ] }\n");
        }
        return y.toString();
    }

    /** Sum over every path from s0 to {@code target} of the product of its attempts, by enumeration. */
    private static BigInteger pathSum(List<int[]> edges, int at, int target) {
        if (at == target) {
            return BigInteger.ONE;
        }
        BigInteger sum = BigInteger.ZERO;
        for (int[] e : edges) {
            if (e[0] == at) {
                sum = sum.add(BigInteger.valueOf(e[2]).multiply(pathSum(edges, e[1], target)));
            }
        }
        return sum;
    }

    @Property
    void withoutChoicesTheLoadIsTheSumOverAllPaths(
            @ForAll @Size(max = 14) List<@IntRange(min = 0, max = 35) Integer> codes) {
        List<int[]> edges = dag(codes);
        AnalysisResult r = Specs.analyze(spec(edges, false));
        for (var a : r.amplification()) {
            int target = Integer.parseInt(a.service().substring(1));
            assertThat(a.value()).isEqualTo(pathSum(edges, 0, target));
        }
    }

    @Property
    void choicesNeverRaiseALoadAndKeepAtLeastTheBestSinglePath(
            @ForAll @Size(max = 14) List<@IntRange(min = 0, max = 35) Integer> codes) {
        List<int[]> edges = dag(codes);
        AnalysisResult sum = Specs.analyze(spec(edges, false));
        AnalysisResult max = Specs.analyze(spec(edges, true));
        for (var a : max.amplification()) {
            assertThat(a.value()).isLessThanOrEqualTo(Specs.load(sum, a.service()));
            assertThat(a.value()).isPositive();
        }
    }
}
