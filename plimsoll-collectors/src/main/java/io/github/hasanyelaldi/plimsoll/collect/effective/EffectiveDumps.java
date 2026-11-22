package io.github.hasanyelaldi.plimsoll.collect.effective;

import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump.Dump;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the dumps given as {@code --effective <service>=<file>} and recognises each format from the content (ADR
 * 0016): a {@code /actuator/configprops} document, or an Envoy {@code config_dump} with route configurations. A
 * service takes at most one dump of each format.
 */
public final class EffectiveDumps {

    /**
     * The dumps of one running service.
     *
     * @param configProps its {@code /actuator/configprops}, or null
     * @param routes      its sidecar's Envoy routes, or null
     */
    public record Effective(Dump configProps, EnvoyRouteDump.Dump routes) {
        /** The dump files, as named in evidence. */
        public List<String> files() {
            List<String> out = new ArrayList<>();
            if (configProps != null) {
                out.add(configProps.file());
            }
            if (routes != null) {
                out.add(routes.file());
            }
            return out;
        }

        public List<String> notes() {
            List<String> out = new ArrayList<>();
            if (configProps != null) {
                out.addAll(configProps.notes());
            }
            if (routes != null) {
                out.addAll(routes.notes());
            }
            return out;
        }
    }

    private EffectiveDumps() {}

    /**
     * @param root the analysed directory; a dump inside it is named relative to it in evidence, one outside it as
     *             given
     */
    public static Effective read(Path root, String service, List<Path> files, DefaultsKb kb) {
        Dump configProps = null;
        EnvoyRouteDump.Dump routes = null;
        for (Path file : files) {
            Path absolute = file.toAbsolutePath().normalize();
            String name = absolute.startsWith(root) ? root.relativize(absolute).toString().replace('\\', '/')
                    : file.toString().replace('\\', '/');
            String text;
            try {
                text = Files.readString(absolute, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidSpecException("--effective " + service + "=" + file + ": cannot read: "
                        + e.getMessage(), e);
            }
            Node tree = YamlTree.parse(text, name);
            if (ConfigPropsDump.recognises(tree)) {
                if (configProps != null) {
                    throw twice(service, "/actuator/configprops", configProps.file(), name);
                }
                configProps = ConfigPropsDump.parse(text, name, service);
            } else if (EnvoyRouteDump.recognises(tree)) {
                if (routes != null) {
                    throw twice(service, "Envoy config_dump", routes.file(), name);
                }
                routes = EnvoyRouteDump.parse(text, name, kb);
            } else {
                throw new InvalidSpecException("--effective " + service + "=" + file + ": not a dump Plimsoll reads; "
                        + "expected the JSON of /actuator/configprops or an Envoy config_dump with route "
                        + "configurations");
            }
        }
        return new Effective(configProps, routes);
    }

    private static InvalidSpecException twice(String service, String format, String first, String second) {
        return new InvalidSpecException("--effective " + service + ": two " + format + " dumps, " + first + " and "
                + second + "; give one per service");
    }
}
