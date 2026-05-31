package teacommontea.commandgo.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlagParser {

    public static final Set<String> GREEDY_FLAGS = Set.of("--note", "--message", "--redirect");

    private FlagParser() {}

    public static Parsed parse(String[] args, int start, Set<String> known, Set<String> booleanFlags) {
        List<String> positional = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        int i = start;
        while (i < args.length) {
            String a = args[i];
            if (a.startsWith("--")) {
                if (!known.contains(a)) {
                    throw new IllegalArgumentException("Unknown flag: " + a);
                }
                if (booleanFlags.contains(a)) {
                    flags.put(a, "true");
                    i++;
                    continue;
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Flag " + a + " requires a value");
                }
                String val = args[i + 1];

                if (val.startsWith("\"")) {
                    StringBuilder sb = new StringBuilder();
                    int j = i + 1;
                    while (j < args.length) {
                        sb.append(args[j]);
                        if (args[j].endsWith("\"") && (j != i + 1 || args[j].length() > 1)) break;
                        sb.append(' ');
                        j++;
                    }
                    String quoted = sb.toString();
                    if (quoted.startsWith("\"")) quoted = quoted.substring(1);
                    if (quoted.endsWith("\"")) quoted = quoted.substring(0, quoted.length() - 1);
                    flags.put(a, quoted);
                    i = j + 1;
                    continue;
                }

                if (GREEDY_FLAGS.contains(a)) {
                    StringBuilder sb = new StringBuilder();
                    int j = i + 1;
                    while (j < args.length && !isKnownFlag(args[j], known)) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(args[j]);
                        j++;
                    }
                    flags.put(a, sb.toString());
                    i = j;
                    continue;
                }

                flags.put(a, val);
                i += 2;
            } else {
                positional.add(a);
                i++;
            }
        }
        return new Parsed(positional, flags);
    }

    private static boolean isKnownFlag(String token, Set<String> known) {
        return token.startsWith("--") && known.contains(token);
    }

    public static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) return out;
        for (String s : value.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public record Parsed(List<String> positional, Map<String, String> flags) {}
}
