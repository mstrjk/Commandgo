package teacommontea.commandgo.rule;

public enum Action {
    ALLOW,
    DENY;

    public static Action parse(String s) {
        if (s == null) return DENY;
        return switch (s.toLowerCase()) {
            case "allow" -> ALLOW;
            case "deny" -> DENY;
            default -> throw new IllegalArgumentException("Unknown action: " + s);
        };
    }
}
