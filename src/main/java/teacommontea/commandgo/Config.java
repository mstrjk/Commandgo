package teacommontea.commandgo;

import org.bukkit.configuration.file.FileConfiguration;

public final class Config {

    private boolean debug;
    private boolean opBypass;
    private String defaultErrorMessage;

    public void load(FileConfiguration cfg) {
        this.debug = cfg.getBoolean("debug", false);
        this.opBypass = cfg.getBoolean("op-bypass", true);
        this.defaultErrorMessage = cfg.getString("default-error-message",
            "&cUnknown or incomplete command. See below for error                                  &n{command}&r&c&o<--[HERE]");
    }

    public boolean debug() { return debug; }
    public boolean opBypass() { return opBypass; }
    public String defaultErrorMessage() { return defaultErrorMessage; }
}
