package teacommontea.commandgo.rule;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import teacommontea.commandgo.util.LuckPermsBridge;

import java.util.List;
import java.util.Set;

public final class Rule {

    private final int id;
    private final String name;
    private final boolean enabled;
    private final GlobPattern pattern;
    private final List<String> worlds;
    private final List<String> plugins;
    private final List<String> enforce;
    private final List<String> enforceNot;
    private final List<String> players;
    private final List<String> playersNot;
    private final List<GameMode> gamemodes;
    private final Action action;
    private final String redirect;
    private final String message;
    private final String note;
    private final boolean enableOverride;

    public Rule(int id, String name, boolean enabled, GlobPattern pattern, List<String> worlds,
                List<String> plugins, List<String> enforce, List<String> enforceNot,
                List<String> players, List<String> playersNot,
                List<GameMode> gamemodes, Action action, String redirect, String message, String note,
                boolean enableOverride) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.pattern = pattern;
        this.worlds = worlds == null ? List.of() : worlds;
        this.plugins = plugins == null ? List.of() : plugins;
        this.enforce = enforce == null ? List.of() : enforce;
        this.enforceNot = enforceNot == null ? List.of() : enforceNot;
        this.players = players == null ? List.of() : players;
        this.playersNot = playersNot == null ? List.of() : playersNot;
        this.gamemodes = gamemodes == null ? List.of() : gamemodes;
        this.action = action == null ? Action.DENY : action;
        this.redirect = redirect;
        this.message = message;
        this.note = note;
        this.enableOverride = enableOverride;
    }

    public int id() { return id; }
    public String name() { return name; }
    public boolean enabled() { return enabled; }
    public GlobPattern pattern() { return pattern; }
    public List<String> worlds() { return worlds; }
    public List<String> plugins() { return plugins; }
    public List<String> enforce() { return enforce; }
    public List<String> enforceNot() { return enforceNot; }
    public List<String> players() { return players; }
    public List<String> playersNot() { return playersNot; }
    public List<GameMode> gamemodes() { return gamemodes; }
    public Action action() { return action; }
    public String redirect() { return redirect; }
    public String message() { return message; }
    public String note() { return note; }
    public boolean enableOverride() { return enableOverride; }

    public boolean matches(Player player, String commandName, String owningPlugin) {
        if (!enabled) return false;
        if (!pattern.matches(commandName)) return false;
        if (!worldMatches(player.getWorld().getName())) return false;
        if (!pluginMatches(owningPlugin)) return false;
        if (!gamemodeMatches(player.getGameMode())) return false;
        if (!playerMatches(player)) return false;
        if (!playerNotMatches(player)) return false;
        if (!enforceNotMatches(player)) return false;
        if (!enforceMatches(player)) return false;
        return true;
    }

    private boolean worldMatches(String world) {
        if (worlds.isEmpty()) return true;
        boolean anyPositive = worlds.stream().anyMatch(w -> !w.startsWith("!"));
        for (String w : worlds) {
            if (w.startsWith("!")) {
                if (w.substring(1).equalsIgnoreCase(world)) return false;
            } else {
                if (w.equalsIgnoreCase(world)) return true;
            }
        }
        return !anyPositive;
    }

    private boolean pluginMatches(String owningPlugin) {
        if (plugins.isEmpty()) return true;
        if (owningPlugin == null) return false;
        return plugins.stream().anyMatch(p -> p.equalsIgnoreCase(owningPlugin));
    }

    private boolean gamemodeMatches(GameMode gm) {
        if (gamemodes.isEmpty()) return true;
        return gamemodes.contains(gm);
    }

    private boolean playerMatches(Player player) {
        if (players.isEmpty()) return true;
        String pname = player.getName();
        String puuid = player.getUniqueId().toString();
        return players.stream().anyMatch(s -> s.equalsIgnoreCase(pname) || s.equalsIgnoreCase(puuid));
    }

    private boolean playerNotMatches(Player player) {
        if (playersNot.isEmpty()) return true;
        String pname = player.getName();
        String puuid = player.getUniqueId().toString();
        return playersNot.stream().noneMatch(s -> s.equalsIgnoreCase(pname) || s.equalsIgnoreCase(puuid));
    }

    private boolean enforceMatches(Player player) {
        if (enforce.isEmpty()) return true;
        return enforce.stream().noneMatch(p -> playerHas(player, p));
    }

    private boolean enforceNotMatches(Player player) {
        if (enforceNot.isEmpty()) return true;
        return enforceNot.stream().anyMatch(p -> playerHas(player, p));
    }

    private static boolean playerHas(Player player, String entry) {
        if (entry.toLowerCase().startsWith("group.")) {
            String groupName = entry.substring(6);
            String primary = LuckPermsBridge.primaryGroup(player);
            if (primary != null) return primary.equalsIgnoreCase(groupName);
        }
        return player.hasPermission(entry);
    }

    public boolean equivalent(Rule other) {
        if (!pattern.raw().equalsIgnoreCase(other.pattern.raw())) return false;
        if (!sameList(worlds, other.worlds)) return false;
        if (!sameList(plugins, other.plugins)) return false;
        if (!sameList(enforce, other.enforce)) return false;
        if (!sameList(enforceNot, other.enforceNot)) return false;
        if (!sameList(players, other.players)) return false;
        if (!sameList(playersNot, other.playersNot)) return false;
        if (!gamemodes.equals(other.gamemodes)) return false;
        if (action != other.action) return false;
        if (!java.util.Objects.equals(redirect, other.redirect)) return false;
        if (!java.util.Objects.equals(message, other.message)) return false;
        if (enableOverride != other.enableOverride) return false;
        return true;
    }

    private static boolean sameList(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        Set<String> as = new java.util.HashSet<>();
        Set<String> bs = new java.util.HashSet<>();
        for (String s : a) as.add(s.toLowerCase());
        for (String s : b) bs.add(s.toLowerCase());
        return as.equals(bs);
    }

    public static final Set<String> KNOWN_FLAGS = Set.of(
        "--name", "--world", "--plugin", "--namespace", "--enforce", "--!enforce",
        "--player", "--!player",
        "--gamemode", "--action", "--redirect", "--message", "--note",
        "--enable-override"
    );
}
