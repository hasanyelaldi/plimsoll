# Envoy config_dump fixture

`http_config.json` is copied unchanged from the Istio project:

- File: `istioctl/pkg/describe/testdata/describe/http_config.json`
- Repository: https://github.com/istio/istio
- Commit: `56245da4ce832744b2062aab045e39a5c033000b` (branch `release-1.31`; the file was last changed in `413dc50988`)
- SHA-256: `4395ab15fa2918a1ffcfaad9d23cd92911bf371bbf226e3497e8c0bf1c94a55f`
- License: Apache License 2.0, Copyright Istio Authors (https://github.com/istio/istio/blob/master/LICENSE)

It is the Envoy admin `config_dump` of an Istio ingress gateway from 2022: route configurations, clusters and
listeners. Its route `http.8080` sends five paths to `productpage` with Istio's default retry policy of that time
(`num_retries: 2`, `retriable_status_codes: [503]`) and no route timeout (`0s`). A sidecar's outbound routes have the
same shape, which is what `EnvoyRouteDump` reads (ADR 0016, #25 part 3).
