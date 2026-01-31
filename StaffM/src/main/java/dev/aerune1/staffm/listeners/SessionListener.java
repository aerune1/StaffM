package dev.aerune1.staffm.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection; // FIXED IMPORT
import dev.aerune1.staffm.StaffM;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionListener {
    
    private final StaffM plugin;
    // Maps UUID -> Login Time (Epoch MS)
    private final Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();
    // Maps UUID -> Total AFK time accumuated for current session
    private final Map<UUID, Long> sessionAfkTime = new ConcurrentHashMap<>();
    // Maps UUID -> Timestamp when they went AFK (or null if active)
    private final Map<UUID, Long> currentAfkStart = new ConcurrentHashMap<>();

    public SessionListener(StaffM plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        // Only track if they have the permission
        if (player.hasPermission(plugin.getConfig().getString("general.staff-permission"))) {
            loginTimes.put(player.getUniqueId(), System.currentTimeMillis());
            sessionAfkTime.put(player.getUniqueId(), 0L);
        }
    }

    @Subscribe
    public void onQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (loginTimes.containsKey(uuid)) {
            long start = loginTimes.remove(uuid);
            long end = System.currentTimeMillis();
            
            // If they were AFK when they quit, add that final chunk
            if (currentAfkStart.containsKey(uuid)) {
                long afkStart = currentAfkStart.remove(uuid);
                long afkDuration = end - afkStart;
                sessionAfkTime.merge(uuid, afkDuration, Long::sum);
            }

            long totalAfk = sessionAfkTime.getOrDefault(uuid, 0L);
            sessionAfkTime.remove(uuid);
            
            // Log to DB Async
            plugin.getDatabaseManager().logSession(uuid, start, end, totalAfk);
        }
    }
    
    // --- Plugin Messaging for AFK ---
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Ensure it's our channel
        if (!event.getIdentifier().equals(plugin.getAfkChannel())) return;
        
        // Ensure source is a backend server
        if (!(event.getSource() instanceof ServerConnection)) return;

        // Protocol expected from Backend:
        // [UTF String: UUID]
        // [Boolean: isAfk] (true = went afk, false = came back)
        
        ByteArrayDataInput in = event.dataAsDataStream();
        try {
            String uuidStr = in.readUTF();
            boolean isAfk = in.readBoolean();
            UUID uuid = UUID.fromString(uuidStr);

            // Only process if we are tracking this player
            if (!loginTimes.containsKey(uuid)) return;

            if (isAfk) {
                // Player went AFK
                currentAfkStart.put(uuid, System.currentTimeMillis());
            } else {
                // Player returned from AFK
                Long afkStart = currentAfkStart.remove(uuid);
                if (afkStart != null) {
                    long duration = System.currentTimeMillis() - afkStart;
                    sessionAfkTime.merge(uuid, duration, Long::sum);
                }
            }
        } catch (Exception e) {
            // Packet formatting error, ignore
        }
    }
}