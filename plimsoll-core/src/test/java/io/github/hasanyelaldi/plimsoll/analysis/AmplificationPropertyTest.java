package io.github.hasanyelaldi.plimsoll.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
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
}
