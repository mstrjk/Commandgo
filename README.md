Commandgo is a Paper plugin that lets you control which commands players can see and run. Rules live in a database and are managed entirely with in game commands, so you never need to restart or reload to change behavior.

Every rule has two layers of enforcement. The first is the Brigadier tab tree, which means blocked commands vanish from autocomplete and help. The second is execution itself, which gets cancelled with a fake "Unknown or incomplete command" error that looks identical to vanilla. Players cannot tell the command exists. They just see what looks like a typo.

You can scope rules by world, by plugin owner, by LuckPerms group, by gamemode, by individual player, and combinations of all of those. Allowlists, blocklists, redirects, custom error messages, hide only mode, and per rule notes are all supported.

If your server has a builder world that should only allow building tools, a minigame world that should lock everything except /spawn, a Skript namespace you want hidden from regular players, or a few admin commands that paid ranks should bypass, Commandgo handles all of it with no code and no config file editing. 

The primary command is /commandgo. You can also use /cg, /cmdgo, or /cmgo. They all do the same thing.
The /cg add command appends a new rule. If a rule already exists with the same scope, it gets merged instead of duplicated.
/cg insert places a rule at a specific position. Lower numbers run first.
/cg remove deletes a rule by name. /cg enable and /cg disable toggle a rule without deleting it.
/cg list shows every rule. You can filter by world or plugin if the list gets long.
/cg reload reloads both the config file and the rules database from disk.
/cg dry-run tells you which rule (if any) would match if you typed a specific command. /cg simulate does the same thing but as a specific player, which is useful for debugging "why does CommonTea see this but Frizzy does not" situations.
/cg suggest lists every command a plugin owns and proposes a starter rule line. Good for figuring out what to block when you install something new.
/cg export prints a rule as a /cg add line so you can copy or share it.
/cg help shows the full subcommand and flag list in chat.

The first argument after add or insert is the command name to match. Patterns support wildcards.

Plain names like foo match that exact command. Leading slashes get stripped, so foo, /foo, and //foo all behave identically.

skript:* matches every command in the skript namespace. : matches every namespaced command across every plugin. * matches every command in existence, which you should use carefully.

The ? character matches a single character. Globs are case insensitive.

Most flags take comma separated lists for multi value scope.

--name lets you set a custom rule name. If you omit it, Commandgo generates a unique name from the pattern and scope, like _Builder or skript:_2.
--world limits the rule to specific worlds. Prefix any name with ! to exclude that world instead. So --world !Spawn means everywhere except Spawn.
--plugin matches only commands owned by listed plugins. Useful when you want to block "all of Skript" or "all of FAWE" without typing every command name.
--namespace is a shortcut. --namespace skript is equivalent to using the pattern skript:*.
--enforce is an allowlist. Players who have any permission or group in the list pass the rule unaffected. Players without any of them get the rule applied. So --enforce group.aether on a deny rule means "block everyone except group.aether".
--!enforce is the opposite. The rule applies only to players who have a listed permission or group. Everyone else passes. Useful for "block this specifically for group.member but let everyone else through".
--player and --!player work the same way but with player names or UUIDs instead of permissions.
--gamemode limits the rule by gamemode. Valid values are survival, creative, adventure, and spectator.
--action determines what happens when the rule matches. Defaults to deny if you do not pass it. Set it to allow to whitelist commands that an earlier rule would otherwise block.
--redirect rewrites the command instead of blocking it. The string $args gets replaced with whatever arguments the player typed. So --redirect "/warp spawn" silently turns /spawn into /warp spawn.
--message overrides the fake error per rule. Supports & color codes and the {command} placeholder.
--note is a comment that shows up in /cg list. Use it to remind future you why a rule exists.
--enable-override hides the command from tab completion but still lets players execute it. Tidies up tab without breaking anything.

For group entries specifically, Commandgo checks the player's primary LuckPerms group, not inherited permissions. So group.member matches a player whose primary group is member but not a player whose primary is admin, even if admin inherits from member. This is so that group tiers work the way most servers actually want them to.

!!! Rules are evaluated top to bottom. First match wins. When a rule matches a player and command, the action runs and no further rules are checked. Use /cg insert if you need to place a rule above existing ones, or just rely on /cg add to append and reorder later. !!!

If you run /cg add with the same scope as an existing rule (same pattern, same worlds, same plugins, same gamemodes), Commandgo updates that rule in place instead of creating a duplicate. List flags like --enforce, --player, and --gamemode get unioned with what was already there. Scalar flags like --action, --message, and --note overwrite. Flags you do not mention keep their old values.

This means adding one more group or player to a rule is a single command. Re running the exact same add is reported as "Unchanged" instead of creating clutter.

Rules persist in plugins/Commandgo/rules.mv.db, an embedded H2 database. The schema migrates automatically on startup. Manage rules with /commandgo commands rather than editing the database file directly.

LuckPerms is an optional soft dependency. If you have it installed, you get live group name autocomplete for --enforce and --!enforce, and group entries use primary group matching rather than inherited permission checks. If you do not have LuckPerms, Commandgo falls back to standard Bukkit permission checks and everything still works.

The only permission Commandgo defines is commandgo.admin, which is required to run /commandgo. Defaults to op. Grant it to your staff group if you want non op staff managing rules. 
