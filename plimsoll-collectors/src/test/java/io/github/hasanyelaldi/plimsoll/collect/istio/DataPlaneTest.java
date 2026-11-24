package io.github.hasanyelaldi.plimsoll.collect.istio;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.istio.DataPlane.Mode;
import io.github.hasanyelaldi.plimsoll.collect.istio.DataPlane.Result;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR 0017, #27 part 1: data plane mode and waypoints from namespace, workload and Service labels. */
class DataPlaneTest {

    @TempDir
    Path dir;

    private final List<Path> files = new ArrayList<>();

    private void write(String file, String text) throws IOException {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        files.add(p);
    }

    private Result collect(String... services) {
        return DataPlane.collect(dir, files, List.of(services), DefaultsKb.classpath());
    }

    private static String namespace(String name, String labels) {
        return """
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: %s
                  labels: { %s }
                ---
                """.formatted(name, labels);
    }

    private static String deployment(String name, String namespace, String podMetadata) {
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  template:
                    metadata: { %s }
                ---
                """.formatted(name, namespace, podMetadata);
    }

    @Test
    void aNamespaceInjectionLabelGivesItsWorkloadsASidecar() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "istio-injection: enabled")
                + deployment("orders", "shop", "labels: { app: orders }"));
        Val<Mode> mode = collect("orders").mode("orders");
        assertThat(mode).isEqualTo(Val.explicit(Mode.SIDECAR, "k8s/shop.yaml:5 istio-injection (enabled)"));
    }

    @Test
    void disabledOnThePodWinsOverEnabledOnTheNamespaceAndRevisionsAndKubeInjectCount() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "istio.io/rev: canary")
                + deployment("orders", "shop", "annotations: { sidecar.istio.io/inject: \"false\" }")
                + deployment("payments", "shop", "labels: { app: payments }")
                + deployment("ledger", "other", "annotations: { sidecar.istio.io/status: '{}' }"));
        Result r = collect("orders", "payments", "ledger");
        assertThat(r.mode("orders").value()).isEqualTo(Mode.NONE);
        assertThat(r.mode("orders").evidence()).startsWith("k8s/shop.yaml:14 sidecar.istio.io/inject (false); ");
        assertThat(r.mode("payments")).isEqualTo(Val.explicit(Mode.SIDECAR, "k8s/shop.yaml:5 istio.io/rev (canary)"));
        assertThat(r.mode("ledger")).isEqualTo(Val.explicit(Mode.SIDECAR,
                "k8s/shop.yaml:32 sidecar.istio.io/status (injected into the manifest)"));
    }

    @Test
    void ambientComesFromTheNamespaceOrThePodAndASidecarWins() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "istio.io/dataplane-mode: ambient")
                + deployment("orders", "shop", "labels: { app: orders }")
                + deployment("payments", "shop", "labels: { istio.io/dataplane-mode: none }")
                + deployment("ledger", "shop", "labels: { sidecar.istio.io/inject: \"true\" }"));
        Result r = collect("orders", "payments", "ledger");
        assertThat(r.mode("orders")).isEqualTo(Val.explicit(Mode.AMBIENT,
                "k8s/shop.yaml:5 istio.io/dataplane-mode (ambient)"));
        assertThat(r.mode("payments").value()).isEqualTo(Mode.NONE);
        assertThat(r.mode("payments").source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(r.mode("payments").evidence()).contains("istio.io/dataplane-mode (none)");
        assertThat(r.mode("ledger").value()).isEqualTo(Mode.SIDECAR);
    }

    @Test
    void noLabelWithTheNamespaceReadIsTheDocumentedDefaultAndWithoutItUnknown() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "team: payments")
                + deployment("orders", "shop", "labels: { app: orders }")
                + deployment("payments", "elsewhere", "labels: { app: payments }"));
        write("k8s/ledger.yaml", """
                apiVersion: apps/v1
                kind: Deployment
                metadata: { name: ledger }
                """);
        Result r = collect("orders", "payments", "ledger", "billing");
        assertThat(r.mode("orders").value()).isEqualTo(Mode.NONE);
        assertThat(r.mode("orders").source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(r.mode("orders").evidence())
                .contains("no injection label on k8s/shop.yaml orders or namespace shop (k8s/shop.yaml)")
                .contains("sidecar-injection");
        assertThat(r.mode("payments")).isEqualTo(Val.unknown("no manifest for namespace elsewhere of k8s/shop.yaml "
                + "payments"));
        assertThat(r.mode("ledger")).isEqualTo(Val.unknown("k8s/ledger.yaml ledger has no metadata.namespace"));
        assertThat(r.mode("billing")).isEqualTo(Val.unknown("no workload manifest for billing"));
        assertThat(r.files()).containsExactly("k8s/ledger.yaml", "k8s/shop.yaml");
    }

    @Test
    void workloadsOfOneServiceThatDifferLeaveItsModeUnknown() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "istio-injection: enabled")
                + deployment("orders-v1", "shop", "labels: { app: orders }")
                + deployment("orders-v2", "shop", "labels: { app: orders, sidecar.istio.io/inject: \"false\" }"));
        Val<Mode> mode = collect("orders").mode("orders");
        assertThat(mode.isKnown()).isFalse();
        assertThat(mode.evidence()).startsWith("its workloads differ: sidecar (k8s/shop.yaml:5 istio-injection "
                + "(enabled)), none (");
    }

    @Test
    void aWaypointComesFromTheServiceElseItsNamespace() throws IOException {
        write("k8s/shop.yaml", namespace("shop", "istio.io/use-waypoint: shop-waypoint")
                + deployment("orders", "shop", "labels: { app: orders }")
                + deployment("payments", "shop", "labels: { app: payments }")
                + """
                apiVersion: v1
                kind: Service
                metadata:
                  name: payments
                  namespace: shop
                  labels: { istio.io/use-waypoint: none }
                ---
                apiVersion: v1
                kind: Namespace
                metadata: { name: plain }
                ---
                apiVersion: v1
                kind: Service
                metadata: { name: ledger, namespace: plain }
                """);
        Result r = collect("orders", "payments", "ledger", "billing");
        assertThat(r.waypoint("orders")).isEqualTo(Val.explicit(true,
                "k8s/shop.yaml:5 istio.io/use-waypoint (shop-waypoint)"));
        assertThat(r.waypoint("payments")).isEqualTo(Val.explicit(false, "k8s/shop.yaml:30 istio.io/use-waypoint (none)"));
        assertThat(r.waypoint("ledger").value()).isFalse();
        assertThat(r.waypoint("ledger").source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(r.waypoint("ledger").evidence()).startsWith("no istio.io/use-waypoint label on Service ledger "
                + "(k8s/shop.yaml) or namespace plain (k8s/shop.yaml); ");
        assertThat(r.waypoint("billing")).isEqualTo(Val.unknown("the namespace of billing is not known"));
    }

    @Test
    void aManifestThatCannotBeParsedIsNotedOnlyWhenItHasDataPlaneLabels() throws IOException {
        write("chart/templates/ns.yaml", "metadata:\n  labels: { istio-injection: {{ .Values.inject }} }\n  - [\n");
        write("chart/templates/other.yaml", "metadata: {{ .Values.name }\n  - [\n");
        Result r = collect("orders");
        assertThat(r.notes()).singleElement().satisfies(n -> assertThat(n)
                .startsWith("chart/templates/ns.yaml: invalid YAML: ")
                .endsWith("; its data plane labels are not read"));
    }
}
