package teacommontea.commandgo.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public final class LuckPermsBridge {

    private LuckPermsBridge() {}

    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public static Set<String> groupNames() {
        if (!available()) return Set.of();
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (net.luckperms.api.model.group.Group g : lp.getGroupManager().getLoadedGroups()) {
                out.add(g.getName());
            }
            return out;
        } catch (Throwable t) {
            return Set.of();
        }
    }

    public static Set<String> groupEnforceSuggestions() {
        Set<String> out = new LinkedHashSet<>();
        for (String g : groupNames()) out.add("group." + g);
        if (out.isEmpty()) out.add("group.");
        return out;
    }

    public static String primaryGroup(Player player) {
        if (!available()) return null;
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            return user == null ? null : user.getPrimaryGroup();
        } catch (Throwable t) {
            return null;
        }
    }
}
