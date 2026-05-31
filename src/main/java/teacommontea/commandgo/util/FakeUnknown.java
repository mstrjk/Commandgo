package teacommontea.commandgo.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public final class FakeUnknown {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private FakeUnknown() {}

    public static void send(Player p, String fullCommand, String overrideMessage, String defaultTemplate) {
        String shown = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
        String template = (overrideMessage != null && !overrideMessage.isEmpty()) ? overrideMessage : defaultTemplate;
        String resolved = template.replace("{command}", shown);
        p.sendMessage(LEGACY.deserialize(resolved));
    }
}
