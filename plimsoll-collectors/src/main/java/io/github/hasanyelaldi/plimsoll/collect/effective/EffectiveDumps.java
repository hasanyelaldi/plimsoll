package io.github.hasanyelaldi.plimsoll.collect.effective;

import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump.Dump;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a dump given as {@code --effective <service>=<file>} and recognises its format from the content (ADR 0016). */
public final class EffectiveDumps {

    private EffectiveDumps() {}

    /**
     * @param root the analysed directory; a dump inside it is named relative to it in evidence, one outside it as
     *             given
     */
    public static Dump read(Path root, String service, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        String name = absolute.startsWith(root) ? root.relativize(absolute).toString().replace('\\', '/')
                : file.toString().replace('\\', '/');
        String text;
        try {
            text = Files.readString(absolute, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidSpecException("--effective " + service + "=" + file + ": cannot read: " + e.getMessage(), e);
        }
        if (ConfigPropsDump.recognises(YamlTree.parse(text, name))) {
            return ConfigPropsDump.parse(text, name, service);
        }
        throw new InvalidSpecException("--effective " + service + "=" + file + ": not a dump Plimsoll reads; "
                + "expected the JSON of /actuator/configprops");
    }
}
