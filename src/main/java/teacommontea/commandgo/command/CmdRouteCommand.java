package teacommontea.commandgo.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import teacommontea.commandgo.Commandgo;
import teacommontea.commandgo.Config;
import teacommontea.commandgo.rule.Action;
import teacommontea.commandgo.rule.GlobPattern;
import teacommontea.commandgo.rule.Rule;
import teacommontea.commandgo.rule.RuleStore;
import teacommontea.commandgo.util.LuckPermsBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CmdRouteCommand implements TabExecutor {

    private static final Set<String> SUBCOMMANDS = Set.of(
        "add", "insert", "remove", "enable", "disable", "list", "reload",
        "dry-run", "simulate", "suggest", "export", "help"
    );

    private static final Set<String> BOOLEAN_FLAGS = Set.of("--enable-override");
    private static final Set<String> GLOB_HINTS = new LinkedHashSet<>(List.of("*", "*:*", "skript:*"));
    private static final Set<String> GAMEMODES = Set.of("survival", "creative", "adventure", "spectator");

    private static final String PFX = "&e[&6Commandgo&e] ";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final Set<String> PROXY_COMMANDS = Set.of(
        "backup","dailytime","fallback","fallbackserver","fav","favorite","find","friend","friends",
        "fs","fsv","hub","ignore","join","leaveq","joinq","leave","leavequeue","joinqueue","lobby",
        "luckpermsvelocity","m","message","mh","mhc","minehut","msg","r","reply","setting","settings",
        "tell","velocity","vsa","whisper","pm","dm","socialspy","spy","msgtoggle","togglemsg",
        "server","servers","send","l","whereis","glist","listplayers","playerlist","joinserver",
        "switch","connect","f","fr","friendadd","friendremove","friendaccept","frienddeny",
        "friendlist","msgfriend","priorityqueue","jq","lq","network","proxy","g","global",
        "report","reports","ticket","support","mail","mailbox"
    );

    private final Commandgo plugin;
    private final RuleStore store;
    private final Config cfg;

    public CmdRouteCommand(Commandgo plugin, RuleStore store, Config cfg) {
        this.plugin = plugin;
        this.store = store;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("commandgo.admin")) {
            send(sender, PFX + "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0) { help(sender); return true; }
        try {
            switch (args[0].toLowerCase()) {
                case "add" -> handleAdd(sender, args, -1);
                case "insert" -> handleInsert(sender, args);
                case "remove" -> handleRemove(sender, args);
                case "enable" -> handleEnable(sender, args, true);
                case "disable" -> handleEnable(sender, args, false);
                case "list" -> handleList(sender, args);
                case "reload" -> { plugin.reloadAll(); send(sender, PFX + "&aReloaded config + rules. &7&o" + store.rules().size() + " &r&arules loaded."); }
                case "dry-run" -> handleDryRun(sender, args);
                case "simulate" -> handleSimulate(sender, args);
                case "suggest" -> handleSuggest(sender, args);
                case "export" -> handleExport(sender, args);
                case "help" -> help(sender);
                default -> send(sender, PFX + "&cUnknown subcommand: &7&o" + args[0]);
            }
        } catch (IllegalArgumentException e) {
            send(sender, PFX + "&c" + e.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender) {
        send(sender, PFX + "&b/commandgo &7subcommands:");
        for (String line : List.of(
            "&7  add <pattern> [flags]            &8append rule (use --name <id> for custom name)",
            "&7  insert <pos> <pattern> [flags]   &8insert at position",
            "&7  remove <name>                    &8delete rule by name",
            "&7  enable <name> | disable <name>   &8toggle without deleting",
            "&7  list [--world X] [--plugin X]    &8show rules",
            "&7  reload                           &8reload config + rules",
            "&7  dry-run <command>                &8show matching rules (you)",
            "&7  simulate <player> <command>      &8show what would happen for that player",
            "&7  suggest --plugin <name>          &8propose rules for plugin's commands",
            "&7  export <name>                    &8print rule as /commandgo add line",
            "&7Flags: &8--name --world --plugin --namespace --enforce --!enforce",
            "&7       &8--player --!player --gamemode --action --redirect --message",
            "&7       &8--note --enable-override"
        )) {
            send(sender, PFX + line);
        }
    }

    private void handleAdd(CommandSender sender, String[] args, int insertPos) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /commandgo add <pattern> [flags]");
        String pattern = args[1];
        FlagParser.Parsed parsed = FlagParser.parse(args, 2, Rule.KNOWN_FLAGS, BOOLEAN_FLAGS);
        Rule template = buildRule(pattern, parsed);
        String requestedName = parsed.flags().get("--name");
        warnIfProxyManaged(sender, template);

        String verb;
        Rule added;
        if (insertPos < 0) {
            RuleStore.AddResult result = store.add(requestedName, template, parsed.flags().keySet());
            added = result.rule();
            verb = switch (result.status()) {
                case ADDED -> "Added";
                case EDITED -> "Edited";
                case UNCHANGED -> "Unchanged";
            };
        } else {
            added = store.insert(insertPos, requestedName, template);
            verb = "Inserted";
        }

        StringBuilder sb = new StringBuilder(PFX);
        sb.append("&a").append(verb).append(" rule: &r&7&o").append(added.pattern().raw());
        if (added.name() != null && !added.name().equalsIgnoreCase(added.pattern().raw())) {
            sb.append(" &r&8(&7").append(added.name()).append("&8)");
        }
        if (!added.worlds().isEmpty()) sb.append(" &r&aWorlds: &7&o").append(String.join(", ", added.worlds()));
        if (!added.plugins().isEmpty()) sb.append(" &r&aPlugins: &7&o").append(String.join(", ", added.plugins()));
        if (!added.enforce().isEmpty()) {
            sb.append(" &r&aEnforcing on: &7&o").append(String.join(", ", added.enforce()));
            if (hasLuckPermsGroups(added.enforce())) sb.append(" (LuckPerms)");
        }
        if (!added.enforceNot().isEmpty()) {
            sb.append(" &r&aExcluding: &7&o").append(String.join(", ", added.enforceNot()));
            if (hasLuckPermsGroups(added.enforceNot())) sb.append(" (LuckPerms)");
        }
        if (!added.players().isEmpty()) sb.append(" &r&aPlayers: &7&o").append(String.join(", ", added.players()));
        if (!added.playersNot().isEmpty()) sb.append(" &r&aExcluding players: &7&o").append(String.join(", ", added.playersNot()));
        if (!added.gamemodes().isEmpty()) {
            sb.append(" &r&aGamemodes: &7&o").append(added.gamemodes().stream().map(g -> g.name().toLowerCase()).collect(Collectors.joining(", ")));
        }
        sb.append(" &r&aAction: &7&o").append(added.action().name().toLowerCase());
        if (added.enableOverride()) sb.append(" &r&8(hide-only)");
        if (added.redirect() != null) sb.append(" &r&aRedirects to: &7&o").append(added.redirect());
        if (added.note() != null) sb.append(" &r&aWith note: &7&o").append(added.note());
        sb.append(" &r&aActor: &7").append(sender.getName());
        send(sender, sb.toString());
    }

    private static boolean hasLuckPermsGroups(List<String> perms) {
        return LuckPermsBridge.available() && perms.stream().anyMatch(p -> p.toLowerCase().startsWith("group."));
    }

    private static void warnIfProxyManaged(CommandSender sender, Rule template) {
        List<String> matches = new ArrayList<>();
        for (String c : PROXY_COMMANDS) {
            if (template.pattern().matches(c)) matches.add(c);
        }
        if (matches.isEmpty()) return;
        if (matches.size() > 8) {
            send(sender, PFX + "&cWarning! &bCommandgo &lmay not be able&r &bto unregister some commands because they may be registered by a proxy or another service outside this server. If some commands are not unregistered, this is not a bug but a limitation of the server environment.");
            return;
        }
        java.util.Collections.sort(matches);
        send(sender, PFX + "&cWarning! &bCommandgo &lmay not be able&r &bto unregister &n" + String.join(", ", matches) + "&r &bbecause they may be registered by a proxy or another service outside this server. If the command is not unregistered, this is not a bug but a limitation of the server environment.");
    }

    private static void send(CommandSender s, String legacy) {
        s.sendMessage(LEGACY.deserialize(legacy));
    }

    private void handleInsert(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /commandgo insert <pos> <pattern> [flags]");
        int pos;
        try { pos = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Position must be a number"); }
        String[] shifted = new String[args.length - 1];
        shifted[0] = "add";
        shifted[1] = args[2];
        System.arraycopy(args, 3, shifted, 2, args.length - 3);
        handleAdd(sender, shifted, pos);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /commandgo remove <name>");
        String name = args[1];
        if (store.removeByName(name)) {
            send(sender, PFX + "&aRemoved rule: &7&o" + name + " &r&aActor: &7" + sender.getName());
        } else {
            send(sender, PFX + "&cNo rule named &7&o" + name);
        }
    }

    private void handleEnable(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /commandgo " + (enable ? "enable" : "disable") + " <name>");
        String name = args[1];
        if (store.setEnabledByName(name, enable)) {
            send(sender, PFX + "&a" + (enable ? "Enabled" : "Disabled") + " rule: &7&o" + name + " &r&aActor: &7" + sender.getName());
        } else {
            send(sender, PFX + "&eNo change for rule &7&o" + name);
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        FlagParser.Parsed parsed = FlagParser.parse(args, 1, Set.of("--world", "--plugin"), Set.of());
        String worldFilter = parsed.flags().get("--world");
        String pluginFilter = parsed.flags().get("--plugin");
        List<Rule> rules = store.rules();
        if (rules.isEmpty()) {
            send(sender, PFX + "&7No rules.");
            return;
        }
        send(sender, PFX + "&aRules &7&o(" + rules.size() + ")&r&a:");
        for (Rule r : rules) {
            if (worldFilter != null && !r.worlds().contains(worldFilter)) continue;
            if (pluginFilter != null && !r.plugins().contains(pluginFilter)) continue;
            String color = r.enabled() ? (r.action() == Action.ALLOW ? "&a" : "&c") : "&8";
            send(sender, PFX + color + formatRuleSummary(r));
        }
    }

    private void handleDryRun(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            send(sender, PFX + "&cdry-run requires a player. Use &7&o/commandgo simulate <player> <command> &r&cfrom console.");
            return;
        }
        if (args.length < 2) throw new IllegalArgumentException("Usage: /commandgo dry-run <command>");
        String cmd = stripSlash(args[1]);
        showMatches(sender, p, cmd);
    }

    private void handleSimulate(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /commandgo simulate <player> <command>");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, PFX + "&cPlayer &7&o" + args[1] + " &r&cis not online.");
            return;
        }
        String cmd = stripSlash(args[2]);
        showMatches(sender, target, cmd);
    }

    private void showMatches(CommandSender sender, Player p, String commandName) {
        String owner = RuleStore.ownerOf(commandName);
        send(sender, PFX + "&aEvaluating &7&o" + commandName + " &r&afor &7&o" + p.getName() + " &r&7&o(owner: " + (owner == null ? "vanilla" : owner) + ")");
        Optional<Rule> first = store.findFirstMatch(p, commandName);
        if (first.isEmpty()) {
            send(sender, PFX + "&7No matching rule. Command passes through.");
            return;
        }
        Rule r = first.get();
        String verdict = r.action() == Action.ALLOW ? "ALLOW" : (r.enableOverride() ? "HIDE-ONLY (executable)" : "BLOCK");
        send(sender, PFX + "&aFirst match: &7&o" + r.name() + " &r&a-> &7&o" + verdict);
        send(sender, PFX + "&8  " + formatRuleSummary(r));
    }

    private void handleSuggest(CommandSender sender, String[] args) {
        FlagParser.Parsed parsed = FlagParser.parse(args, 1, Set.of("--plugin"), Set.of());
        String pluginName = parsed.flags().get("--plugin");
        if (pluginName == null) throw new IllegalArgumentException("Usage: /commandgo suggest --plugin <name>");
        Plugin pl = Bukkit.getPluginManager().getPlugin(pluginName);
        if (pl == null) throw new IllegalArgumentException("Plugin not loaded: " + pluginName);
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, Command> e : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            if (e.getValue() instanceof PluginIdentifiableCommand pic && pic.getPlugin().equals(pl)) {
                hits.add(e.getKey());
            }
        }
        if (hits.isEmpty()) {
            send(sender, PFX + "&eNo commands found for &7&o" + pluginName);
            return;
        }
        send(sender, PFX + "&aCommands owned by &7&o" + pluginName + " &r&7&o(" + hits.size() + ")&r&a:");
        for (String h : hits) send(sender, PFX + "&8  " + h);
        send(sender, PFX + "&aSuggested rule:");
        send(sender, PFX + "&7&o  /commandgo add * --plugin " + pluginName + " --action deny");
    }

    private void handleExport(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /commandgo export <name>");
        String name = args[1];
        Rule r = store.findByName(name).orElseThrow(() -> new IllegalArgumentException("No rule named '" + name + "'."));
        send(sender, PFX + "&7&o" + exportLine(r));
    }

    private static String exportLine(Rule r) {
        StringBuilder sb = new StringBuilder("/commandgo add \"").append(r.pattern().raw()).append("\"");
        if (r.name() != null) sb.append(" --name ").append(r.name());
        if (!r.worlds().isEmpty()) sb.append(" --world ").append(String.join(",", r.worlds()));
        if (!r.plugins().isEmpty()) sb.append(" --plugin ").append(String.join(",", r.plugins()));
        if (!r.enforce().isEmpty()) sb.append(" --enforce ").append(String.join(",", r.enforce()));
        if (!r.enforceNot().isEmpty()) sb.append(" --!enforce ").append(String.join(",", r.enforceNot()));
        if (!r.players().isEmpty()) sb.append(" --player ").append(String.join(",", r.players()));
        if (!r.playersNot().isEmpty()) sb.append(" --!player ").append(String.join(",", r.playersNot()));
        if (!r.gamemodes().isEmpty()) {
            sb.append(" --gamemode ").append(r.gamemodes().stream().map(g -> g.name().toLowerCase()).collect(Collectors.joining(",")));
        }
        sb.append(" --action ").append(r.action().name().toLowerCase());
        if (r.redirect() != null) sb.append(" --redirect \"").append(r.redirect()).append("\"");
        if (r.message() != null) sb.append(" --message \"").append(r.message()).append("\"");
        if (r.note() != null) sb.append(" --note \"").append(r.note()).append("\"");
        if (r.enableOverride()) sb.append(" --enable-override");
        return sb.toString();
    }

    private static String formatRuleSummary(Rule r) {
        StringBuilder sb = new StringBuilder();
        sb.append("'").append(r.name() == null ? "?" : r.name()).append("'").append(r.enabled() ? "" : " [DISABLED]").append(" ");
        sb.append(r.pattern().raw());
        sb.append(" [").append(r.action().name().toLowerCase());
        if (r.enableOverride()) sb.append("/hide-only");
        sb.append("]");
        if (!r.worlds().isEmpty()) sb.append(" worlds=").append(r.worlds());
        if (!r.plugins().isEmpty()) sb.append(" plugins=").append(r.plugins());
        if (!r.players().isEmpty()) sb.append(" players=").append(r.players());
        if (!r.enforce().isEmpty()) sb.append(" enforce=").append(r.enforce());
        if (r.note() != null) sb.append(" // ").append(r.note());
        return sb.toString();
    }

    private Rule buildRule(String pattern, FlagParser.Parsed parsed) {
        Map<String, String> f = parsed.flags();
        String ns = f.get("--namespace");
        if (ns != null) pattern = ns + ":*";
        Action action = Action.parse(f.getOrDefault("--action", "deny"));
        List<GameMode> gms = new ArrayList<>();
        for (String g : FlagParser.splitCsv(f.get("--gamemode"))) gms.add(GameMode.valueOf(g.toUpperCase()));
        return new Rule(
            0, null, true,
            new GlobPattern(pattern),
            FlagParser.splitCsv(f.get("--world")),
            FlagParser.splitCsv(f.get("--plugin")),
            FlagParser.splitCsv(f.get("--enforce")),
            FlagParser.splitCsv(f.get("--!enforce")),
            FlagParser.splitCsv(f.get("--player")),
            FlagParser.splitCsv(f.get("--!player")),
            gms,
            action,
            f.get("--redirect"),
            f.get("--message"),
            f.get("--note"),
            f.containsKey("--enable-override")
        );
    }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], SUBCOMMANDS);

        String sub = args[0].toLowerCase();
        String current = args[args.length - 1];
        String prev = args.length >= 2 ? args[args.length - 2] : "";

        if (args.length == 2) {
            return switch (sub) {
                case "remove", "enable", "disable", "export" -> filter(current, ruleNames());
                case "simulate" -> filter(current,
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(toSet()));
                case "dry-run" -> commandSuggestions(current);
                case "add" -> patternSuggestions(current);
                case "insert" -> filter(current,
                    java.util.stream.IntStream.rangeClosed(1, Math.max(1, store.rules().size() + 1))
                        .mapToObj(String::valueOf).collect(toSet()));
                default -> List.of();
            };
        }

        if (args.length == 3) {
            if (sub.equals("simulate")) return commandSuggestions(current);
            if (sub.equals("insert")) return patternSuggestions(current);
        }

        // Value completion after a known flag
        List<String> valueSuggestions = valueSuggestionsFor(prev, current);
        if (valueSuggestions != null) return valueSuggestions;

        // Otherwise we're in flag-name territory — also fires when current is empty (post-space)
        if (current.isEmpty() || current.startsWith("--")) {
            Set<String> remaining = new LinkedHashSet<>(Rule.KNOWN_FLAGS);
            if (sub.equals("list") || sub.equals("suggest")) {
                remaining.retainAll(Set.of("--world", "--plugin"));
            }
            for (int i = 0; i < args.length - 1; i++) remaining.remove(args[i]);
            return filter(current, remaining);
        }

        return List.of();
    }

    private List<String> valueSuggestionsFor(String prev, String current) {
        return switch (prev) {
            case "--world" -> csvComplete(current, Bukkit.getWorlds().stream().map(w -> w.getName()).collect(toSet()));
            case "--plugin" -> csvComplete(current, Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Plugin::getName).collect(toSet()));
            case "--gamemode" -> csvComplete(current, GAMEMODES);
            case "--action" -> filter(current, Set.of("allow", "deny"));
            case "--namespace" -> namespaceSuggestions(current);
            case "--player", "--!player" -> csvComplete(current,
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(toSet()));
            case "--enforce", "--!enforce" -> csvComplete(current, LuckPermsBridge.groupEnforceSuggestions());
            case "--redirect" -> commandSuggestions(current);
            case "--name", "--message", "--note" -> List.of();
            default -> null;
        };
    }

    private Set<String> ruleNames() {
        Set<String> out = new LinkedHashSet<>();
        for (Rule r : store.rules()) if (r.name() != null) out.add(r.name());
        return out;
    }

    private static List<String> commandSuggestions(String current) {
        Set<String> all = new LinkedHashSet<>();
        for (String k : Bukkit.getCommandMap().getKnownCommands().keySet()) {
            if (k.contains(":")) continue;
            all.add(k.startsWith("/") ? k.substring(1) : k);
        }
        return filter(current, all);
    }

    private static List<String> patternSuggestions(String current) {
        Set<String> all = new LinkedHashSet<>(GLOB_HINTS);
        for (String k : Bukkit.getCommandMap().getKnownCommands().keySet()) {
            if (k.contains(":")) continue;
            all.add(k.startsWith("/") ? k.substring(1) : k);
        }
        return filter(current, all);
    }

    private static List<String> namespaceSuggestions(String current) {
        return filter(current, Arrays.stream(Bukkit.getPluginManager().getPlugins())
            .map(p -> p.getName().toLowerCase()).collect(toSet()));
    }

    private static List<String> csvComplete(String current, Set<String> options) {
        int comma = current.lastIndexOf(',');
        String prefix = comma < 0 ? "" : current.substring(0, comma + 1);
        String partial = comma < 0 ? current : current.substring(comma + 1);
        String low = partial.toLowerCase();
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(low))
            .map(s -> prefix + s)
            .sorted()
            .toList();
    }

    private static List<String> filter(String input, Set<String> options) {
        String low = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(low)).sorted().toList();
    }

    private static <T> java.util.stream.Collector<T, ?, Set<T>> toSet() {
        return Collectors.toSet();
    }
}
