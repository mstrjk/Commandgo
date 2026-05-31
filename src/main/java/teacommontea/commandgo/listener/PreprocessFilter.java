package teacommontea.commandgo.listener;

import teacommontea.commandgo.Commandgo;
import teacommontea.commandgo.Config;
import teacommontea.commandgo.rule.Action;
import teacommontea.commandgo.rule.Rule;
import teacommontea.commandgo.rule.RuleStore;
import teacommontea.commandgo.util.FakeUnknown;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

public final class PreprocessFilter implements Listener {

    private final Commandgo plugin;
    private final RuleStore store;
    private final Config cfg;

    public PreprocessFilter(Commandgo plugin, RuleStore store, Config cfg) {
        this.plugin = plugin;
        this.store = store;
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (cfg.opBypass() && p.isOp()) return;

        String message = event.getMessage();
        String name = extractCommandName(message);
        if ("Commandgo".equalsIgnoreCase(RuleStore.ownerOf(name))) return;

        Optional<Rule> match = store.findFirstMatch(p, name);
        if (match.isEmpty()) return;

        Rule r = match.get();
        if (r.action() == Action.ALLOW) return;
        if (r.enableOverride()) return;

        if (r.redirect() != null) {
            String rewritten = applyRedirect(r.redirect(), message);
            event.setMessage(rewritten);
            if (cfg.debug()) plugin.getLogger().info("[preprocess] " + p.getName() + " redirect '" + name + "' -> '" + rewritten + "' via rule #" + r.id());
            return;
        }

        event.setCancelled(true);
        FakeUnknown.send(p, message, r.message(), cfg.defaultErrorMessage());
        if (cfg.debug()) plugin.getLogger().info("[preprocess] " + p.getName() + " block '" + name + "' via rule #" + r.id());
    }

    private static String extractCommandName(String message) {
        String body = message.startsWith("/") ? message.substring(1) : message;
        int space = body.indexOf(' ');
        return space < 0 ? body : body.substring(0, space);
    }

    private static String applyRedirect(String template, String original) {
        String body = original.startsWith("/") ? original.substring(1) : original;
        int space = body.indexOf(' ');
        String args = space < 0 ? "" : body.substring(space + 1);
        String out = template.replace("$args", args);
        if (!out.startsWith("/")) out = "/" + out;
        return out;
    }
}
