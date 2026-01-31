package dev.aerune1.staffm.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.aerune1.staffm.StaffM;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture; // FIXED IMPORT
import java.util.concurrent.TimeUnit;

public class StaffMCommand implements SimpleCommand {
    private final StaffM plugin;

    public StaffMCommand(StaffM plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Default Help
        if (args.length == 0) {
            source.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /staffm <reload|stats>"));
            return;
        }

        // --- RELOAD ---
        if (args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission("staffm.reload")) {
                source.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.no-permission")));
                return;
            }
            plugin.reloadConfig();
            source.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.reload-success")));
            return;
        } 
        
        // --- STATS ---
        if (args[0].equalsIgnoreCase("stats")) {
             // 1. Check Self Stats
            if (args.length == 1) {
                if (source instanceof Player player) {
                    if (!player.hasPermission("staffm.stats.self")) {
                         source.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.no-permission")));
                         return;
                    }
                    showStats(source, player.getUsername(), player.getUniqueId());
                } else {
                    source.sendMessage(Component.text("Console must specify a player: /staffm stats <name>"));
                }
            } 
            // 2. Check Other Stats
            else {
                if (!source.hasPermission("staffm.stats.others")) {
                    source.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.no-permission")));
                    return;
                }
                String targetName = args[1];
                plugin.getProxy().getPlayer(targetName).ifPresentOrElse(
                    target -> showStats(source, target.getUsername(), target.getUniqueId()),
                    () -> source.sendMessage(Component.text("Player not found online."))
                );
            }
        }
    }

    private void showStats(CommandSource viewer, String name, UUID uuid) {
        long now = System.currentTimeMillis();
        long day = now - TimeUnit.DAYS.toMillis(1);
        long week = now - TimeUnit.DAYS.toMillis(7);
        long month = now - TimeUnit.DAYS.toMillis(30);

        // Fetch all asynchronously
        var dayFuture = plugin.getDatabaseManager().getPlaytime(uuid, day);
        var weekFuture = plugin.getDatabaseManager().getPlaytime(uuid, week);
        var monthFuture = plugin.getDatabaseManager().getPlaytime(uuid, month);
        // "all time" could be passed as 0L
        var allTimeFuture = plugin.getDatabaseManager().getPlaytime(uuid, 0L);

        // Combine futures to send one clean message block
        CompletableFuture.allOf(dayFuture, weekFuture, monthFuture, allTimeFuture).thenRun(() -> {
            try {
                long d = dayFuture.get();
                long w = weekFuture.get();
                long m = monthFuture.get();
                long a = allTimeFuture.get();

                MiniMessage mm = MiniMessage.miniMessage();
                
                viewer.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.stats-header").replace("%player%", name)));
                viewer.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.stats-entry")
                        .replace("%period%", "Last 24h").replace("%time%", formatMinutes(d))));
                viewer.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.stats-entry")
                        .replace("%period%", "Last 7d").replace("%time%", formatMinutes(w))));
                viewer.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.stats-entry")
                        .replace("%period%", "Last 30d").replace("%time%", formatMinutes(m))));
                viewer.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.stats-entry")
                        .replace("%period%", "All Time").replace("%time%", formatMinutes(a))));
            
            } catch (Exception e) {
                viewer.sendMessage(Component.text("Error retrieving stats."));
                e.printStackTrace();
            }
        });
    }
    
    private String formatMinutes(long millis) {
        return String.valueOf(TimeUnit.MILLISECONDS.toMinutes(millis));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("reload", "stats");
        }
        return List.of();
    }
}