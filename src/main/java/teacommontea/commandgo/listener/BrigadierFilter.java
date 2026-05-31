package teacommontea.commandgo.listener;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import teacommontea.commandgo.Commandgo;
import teacommontea.commandgo.Config;
import teacommontea.commandgo.rule.Action;
import teacommontea.commandgo.rule.Rule;
import teacommontea.commandgo.rule.RuleStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BrigadierFilter implements Listener {

    private final Commandgo plugin;
    private final RuleStore store;
    private final Config cfg;

    public BrigadierFilter(Commandgo plugin, RuleStore store, Config cfg) {
        this.plugin = plugin;
        this.store = store;
        this.cfg = cfg;
    }

    @EventHandler
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onSendCommands(AsyncPlayerSendCommandsEvent<?> event) {
        Player p = event.getPlayer();
        if (cfg.opBypass() && p.isOp()) return;

        RootCommandNode<?> root = event.getCommandNode();
        List<CommandNode<?>> keep = new ArrayList<>();
        int dropped = 0;
        for (CommandNode<?> child : root.getChildren()) {
            String name = child.getName();
            if (isCommandgoCommand(name)) { keep.add(child); continue; }
            Optional<Rule> match = store.findFirstMatch(p, name);
            if (match.isEmpty()) { keep.add(child); continue; }
            Rule r = match.get();
            if (r.action() == Action.ALLOW) {
                keep.add(child);
            } else {
                dropped++;
                if (cfg.debug()) {
                    plugin.getLogger().info("[brigadier] " + p.getName() + " hide '" + name + "' via rule #" + r.id());
                }
            }
        }

        if (dropped == 0) return;

        root.getChildren().clear();
        RootCommandNode raw = root;
        for (CommandNode<?> node : keep) raw.addChild(node);
    }

    private static boolean isCommandgoCommand(String name) {
        String owner = RuleStore.ownerOf(name);
        return "Commandgo".equalsIgnoreCase(owner);
    }
}
