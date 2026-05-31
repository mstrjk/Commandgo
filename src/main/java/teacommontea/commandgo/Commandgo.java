package teacommontea.commandgo;

import teacommontea.commandgo.command.CmdRouteCommand;
import teacommontea.commandgo.listener.BrigadierFilter;
import teacommontea.commandgo.listener.PreprocessFilter;
import teacommontea.commandgo.rule.RuleStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Commandgo extends JavaPlugin {

    private RuleStore store;
    private Config cfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new Config();
        cfg.load(getConfig());

        store = new RuleStore(this);
        store.load();

        getServer().getPluginManager().registerEvents(new BrigadierFilter(this, store, cfg), this);
        getServer().getPluginManager().registerEvents(new PreprocessFilter(this, store, cfg), this);

        PluginCommand cmd = getCommand("commandgo");
        if (cmd != null) {
            CmdRouteCommand handler = new CmdRouteCommand(this, store, cfg);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("Commandgo enabled. " + store.rules().size() + " rules loaded.");
    }

    public RuleStore store() { return store; }
    public Config cfg() { return cfg; }

    public void reloadAll() {
        reloadConfig();
        cfg.load(getConfig());
        store.load();
    }
}
