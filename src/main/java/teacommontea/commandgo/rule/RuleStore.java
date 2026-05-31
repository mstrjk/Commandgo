package teacommontea.commandgo.rule;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class RuleStore {

    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private final List<Rule> cache = new ArrayList<>();

    public RuleStore(JavaPlugin plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File dbFile = new File(dir, "rules");
        this.jdbcUrl = "jdbc:h2:" + dbFile.getAbsolutePath().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
        try {
            Class.forName("teacommontea.commandgo.lib.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Shaded H2 driver missing from jar", e);
        }
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public synchronized List<Rule> rules() {
        return List.copyOf(cache);
    }

    public synchronized void load() {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rules (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(64),
                    position INT NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    pattern VARCHAR(256) NOT NULL,
                    worlds TEXT NOT NULL DEFAULT '',
                    plugins TEXT NOT NULL DEFAULT '',
                    enforce TEXT NOT NULL DEFAULT '',
                    enforce_not TEXT NOT NULL DEFAULT '',
                    players TEXT NOT NULL DEFAULT '',
                    players_not TEXT NOT NULL DEFAULT '',
                    gamemodes TEXT NOT NULL DEFAULT '',
                    action VARCHAR(16) NOT NULL DEFAULT 'deny',
                    redirect TEXT,
                    message TEXT,
                    note TEXT,
                    enable_override BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
            st.executeUpdate("ALTER TABLE rules ADD COLUMN IF NOT EXISTS name VARCHAR(64)");
            st.executeUpdate("ALTER TABLE rules ADD COLUMN IF NOT EXISTS players TEXT NOT NULL DEFAULT ''");
            st.executeUpdate("ALTER TABLE rules ADD COLUMN IF NOT EXISTS players_not TEXT NOT NULL DEFAULT ''");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS rules_position ON rules(position)");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS rules_name ON rules(name)");
            refreshCache(c);
            backfillNames(c);
            pushTreeRefresh();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load rules from H2", e);
        }
    }

    private void backfillNames(Connection c) throws SQLException {
        Set<String> taken = new HashSet<>();
        for (Rule r : cache) if (r.name() != null) taken.add(r.name().toLowerCase());
        boolean any = false;
        for (Rule r : cache) {
            if (r.name() != null) continue;
            String generated = uniqueName(r.pattern().raw(), taken);
            taken.add(generated.toLowerCase());
            try (PreparedStatement ps = c.prepareStatement("UPDATE rules SET name = ? WHERE id = ?")) {
                ps.setString(1, generated);
                ps.setInt(2, r.id());
                ps.executeUpdate();
            }
            any = true;
        }
        if (any) refreshCache(c);
    }

    private String uniqueName(String base, Set<String> taken) {
        return uniqueNameForScope(base, null, taken);
    }

    private String uniqueNameForScope(String base, Rule template, Set<String> taken) {
        String stripped = base;
        while (stripped.startsWith("/")) stripped = stripped.substring(1);
        if (stripped.isEmpty()) stripped = "rule";

        String suffix = "";
        if (template != null) {
            if (!template.worlds().isEmpty()) suffix = "-" + template.worlds().get(0);
            else if (!template.plugins().isEmpty()) suffix = "-" + template.plugins().get(0);
            else if (!template.gamemodes().isEmpty()) suffix = "-" + template.gamemodes().get(0).name().toLowerCase();
        }

        String candidate = stripped + suffix;
        int n = 2;
        while (taken.contains(candidate.toLowerCase())) {
            candidate = stripped + suffix + "-" + n;
            n++;
        }
        return candidate;
    }

    public enum AddStatus { ADDED, EDITED, UNCHANGED }
    public record AddResult(Rule rule, AddStatus status) {}

    public synchronized AddResult add(String requestedName, Rule template, Set<String> explicit) {
        Optional<Rule> existing = cache.stream()
            .filter(r -> sameScope(r, template))
            .findFirst();

        if (existing.isPresent()) {
            Rule old = existing.get();
            Rule merged = merge(old, template, requestedName, explicit);
            if (merged.equivalent(old)
                && Objects.equals(nullSafe(merged.name()), nullSafe(old.name()))
                && Objects.equals(nullSafe(merged.note()), nullSafe(old.note()))) {
                return new AddResult(old, AddStatus.UNCHANGED);
            }
            try (Connection c = conn()) {
                updateRow(c, old.id(), merged);
                refreshCache(c);
                pushTreeRefresh();
                return new AddResult(findById(old.id()).orElseThrow(), AddStatus.EDITED);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to edit rule", e);
            }
        }

        try (Connection c = conn()) {
            String name = resolveName(requestedName, template);
            int nextPos = nextPosition(c);
            int id = insertRow(c, name, nextPos, template);
            refreshCache(c);
            pushTreeRefresh();
            return new AddResult(findById(id).orElseThrow(), AddStatus.ADDED);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add rule", e);
        }
    }

    public synchronized Rule insert(int position, String requestedName, Rule template) {
        try (Connection c = conn()) {
            String name = resolveName(requestedName, template);
            int size = cache.size();
            int pos = Math.max(1, Math.min(size + 1, position));
            shiftPositionsUp(c, pos);
            int id = insertRow(c, name, pos, template);
            refreshCache(c);
            pushTreeRefresh();
            return findById(id).orElseThrow();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert rule", e);
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static boolean sameScope(Rule a, Rule b) {
        if (!a.pattern().raw().equalsIgnoreCase(b.pattern().raw())) return false;
        if (!sameStringSet(a.worlds(), b.worlds())) return false;
        if (!sameStringSet(a.plugins(), b.plugins())) return false;
        if (!a.gamemodes().equals(b.gamemodes())) return false;
        return true;
    }

    private static boolean sameStringSet(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        Set<String> as = new HashSet<>();
        Set<String> bs = new HashSet<>();
        for (String s : a) as.add(s.toLowerCase());
        for (String s : b) bs.add(s.toLowerCase());
        return as.equals(bs);
    }

    private Rule merge(Rule old, Rule template, String requestedName, Set<String> explicit) {
        return new Rule(
            old.id(),
            (explicit.contains("--name") && requestedName != null && !requestedName.isEmpty()) ? requestedName : old.name(),
            old.enabled(),
            template.pattern(),
            explicit.contains("--world") ? union(old.worlds(), template.worlds()) : old.worlds(),
            explicit.contains("--plugin") ? union(old.plugins(), template.plugins()) : old.plugins(),
            explicit.contains("--enforce") ? union(old.enforce(), template.enforce()) : old.enforce(),
            explicit.contains("--!enforce") ? union(old.enforceNot(), template.enforceNot()) : old.enforceNot(),
            explicit.contains("--player") ? union(old.players(), template.players()) : old.players(),
            explicit.contains("--!player") ? union(old.playersNot(), template.playersNot()) : old.playersNot(),
            explicit.contains("--gamemode") ? union(old.gamemodes(), template.gamemodes()) : old.gamemodes(),
            explicit.contains("--action") ? template.action() : old.action(),
            explicit.contains("--redirect") ? template.redirect() : old.redirect(),
            explicit.contains("--message") ? template.message() : old.message(),
            explicit.contains("--note") ? template.note() : old.note(),
            explicit.contains("--enable-override") ? template.enableOverride() : old.enableOverride()
        );
    }

    private static <T> List<T> union(List<T> a, List<T> b) {
        LinkedHashSet<T> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    private void updateRow(Connection c, int id, Rule t) throws SQLException {
        String sql = """
            UPDATE rules SET name=?, enabled=?, pattern=?, worlds=?, plugins=?, enforce=?, enforce_not=?,
                players=?, players_not=?, gamemodes=?, action=?, redirect=?, message=?, note=?, enable_override=?
            WHERE id=?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.name());
            ps.setBoolean(2, t.enabled());
            ps.setString(3, t.pattern().raw());
            ps.setString(4, joinCsv(t.worlds()));
            ps.setString(5, joinCsv(t.plugins()));
            ps.setString(6, joinCsv(t.enforce()));
            ps.setString(7, joinCsv(t.enforceNot()));
            ps.setString(8, joinCsv(t.players()));
            ps.setString(9, joinCsv(t.playersNot()));
            ps.setString(10, t.gamemodes().stream().map(g -> g.name().toLowerCase()).collect(Collectors.joining(",")));
            ps.setString(11, t.action().name().toLowerCase());
            ps.setString(12, t.redirect());
            ps.setString(13, t.message());
            ps.setString(14, t.note());
            ps.setBoolean(15, t.enableOverride());
            ps.setInt(16, id);
            ps.executeUpdate();
        }
    }

    public synchronized boolean removeByName(String name) {
        Optional<Rule> r = findByName(name);
        if (r.isEmpty()) return false;
        return removeInternal(r.get().id());
    }

    public synchronized boolean setEnabledByName(String name, boolean enabled) {
        Optional<Rule> r = findByName(name);
        if (r.isEmpty()) return false;
        return setEnabledInternal(r.get().id(), enabled);
    }

    private boolean removeInternal(int id) {
        try (Connection c = conn()) {
            Integer pos = positionOf(c, id);
            if (pos == null) return false;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM rules WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE rules SET position = position - 1 WHERE position > ?")) {
                ps.setInt(1, pos);
                ps.executeUpdate();
            }
            refreshCache(c);
            pushTreeRefresh();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove rule", e);
        }
    }

    private boolean setEnabledInternal(int id, boolean enabled) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE rules SET enabled = ? WHERE id = ? AND enabled <> ?")) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, id);
            ps.setBoolean(3, enabled);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (Connection c2 = conn()) { refreshCache(c2); }
                pushTreeRefresh();
            }
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to toggle rule", e);
        }
    }

    private String resolveName(String requested, Rule template) throws SQLException {
        Set<String> taken = new HashSet<>();
        for (Rule r : cache) if (r.name() != null) taken.add(r.name().toLowerCase());
        if (requested != null && !requested.isEmpty()) {
            if (taken.contains(requested.toLowerCase())) {
                throw new IllegalArgumentException("A rule named '" + requested + "' already exists.");
            }
            return requested;
        }
        return uniqueNameForScope(template.pattern().raw(), template, taken);
    }

    private void pushTreeRefresh() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
        });
    }

    public Optional<Rule> findFirstMatch(Player player, String commandName) {
        String owner = ownerOf(commandName);
        for (Rule r : rules()) {
            if (r.matches(player, commandName, owner)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public Optional<Rule> findById(int id) {
        return cache.stream().filter(r -> r.id() == id).findFirst();
    }

    public Optional<Rule> findByName(String name) {
        if (name == null) return Optional.empty();
        return cache.stream().filter(r -> name.equalsIgnoreCase(r.name())).findFirst();
    }

    public static String ownerOf(String commandName) {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();

        Command cmd = lookupKnown(known, commandName);
        if (cmd instanceof PluginIdentifiableCommand pic) return pic.getPlugin().getName();

        String name = commandName.startsWith("/") ? commandName.substring(1) : commandName;

        int colon = name.indexOf(':');
        if (colon > 0) {
            String prefix = name.substring(0, colon);
            Plugin byPrefix = findPluginIgnoreCase(prefix);
            if (byPrefix != null) return byPrefix.getName();
        }

        cmd = lookupKnown(known, name);
        if (cmd == null) cmd = lookupKnown(known, "/" + name);
        if (cmd == null && colon > 0) {
            cmd = lookupKnown(known, name.substring(colon + 1));
        }
        if (cmd instanceof PluginIdentifiableCommand pic) {
            return pic.getPlugin().getName();
        }
        return null;
    }

    private static Command lookupKnown(Map<String, Command> known, String key) {
        Command c = known.get(key);
        if (c != null) return c;
        return known.get(key.toLowerCase());
    }

    private static Plugin findPluginIgnoreCase(String name) {
        Plugin direct = Bukkit.getPluginManager().getPlugin(name);
        if (direct != null) return direct;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private int nextPosition(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(position), 0) + 1 FROM rules")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Integer positionOf(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT position FROM rules WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private void shiftPositionsUp(Connection c, int fromPos) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE rules SET position = position + 1 WHERE position >= ?")) {
            ps.setInt(1, fromPos);
            ps.executeUpdate();
        }
    }

    private int insertRow(Connection c, String name, int position, Rule t) throws SQLException {
        String sql = """
            INSERT INTO rules (name, position, enabled, pattern, worlds, plugins, enforce, enforce_not,
                players, players_not, gamemodes, action, redirect, message, note, enable_override)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, position);
            ps.setBoolean(3, t.enabled());
            ps.setString(4, t.pattern().raw());
            ps.setString(5, joinCsv(t.worlds()));
            ps.setString(6, joinCsv(t.plugins()));
            ps.setString(7, joinCsv(t.enforce()));
            ps.setString(8, joinCsv(t.enforceNot()));
            ps.setString(9, joinCsv(t.players()));
            ps.setString(10, joinCsv(t.playersNot()));
            ps.setString(11, t.gamemodes().stream().map(g -> g.name().toLowerCase()).collect(Collectors.joining(",")));
            ps.setString(12, t.action().name().toLowerCase());
            ps.setString(13, t.redirect());
            ps.setString(14, t.message());
            ps.setString(15, t.note());
            ps.setBoolean(16, t.enableOverride());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void refreshCache(Connection c) throws SQLException {
        cache.clear();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rules ORDER BY position ASC")) {
            while (rs.next()) cache.add(rowToRule(rs));
        }
    }

    private static Rule rowToRule(ResultSet rs) throws SQLException {
        List<GameMode> gms = new ArrayList<>();
        for (String g : splitCsv(rs.getString("gamemodes"))) gms.add(GameMode.valueOf(g.toUpperCase()));
        return new Rule(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getBoolean("enabled"),
            new GlobPattern(rs.getString("pattern")),
            splitCsv(rs.getString("worlds")),
            splitCsv(rs.getString("plugins")),
            splitCsv(rs.getString("enforce")),
            splitCsv(rs.getString("enforce_not")),
            splitCsv(rs.getString("players")),
            splitCsv(rs.getString("players_not")),
            gms,
            Action.parse(rs.getString("action")),
            rs.getString("redirect"),
            rs.getString("message"),
            rs.getString("note"),
            rs.getBoolean("enable_override")
        );
    }

    private static String joinCsv(List<String> in) {
        return in == null ? "" : String.join(",", in);
    }

    private static List<String> splitCsv(String s) {
        if (s == null || s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
