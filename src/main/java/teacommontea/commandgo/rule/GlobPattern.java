package teacommontea.commandgo.rule;

import java.util.regex.Pattern;

public final class GlobPattern {

    private final String raw;
    private final Pattern compiled;

    public GlobPattern(String raw) {
        this.raw = raw;
        String normalized = stripLeadingSlashes(raw);
        StringBuilder sb = new StringBuilder("^");
        for (char c : normalized.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                    sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        this.compiled = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    public boolean matches(String commandName) {
        return compiled.matcher(stripLeadingSlashes(commandName)).matches();
    }

    private static String stripLeadingSlashes(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '/') i++;
        return s.substring(i);
    }

    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }
}
