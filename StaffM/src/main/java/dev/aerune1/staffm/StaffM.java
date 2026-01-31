package dev.aerune1.staffm;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.aerune1.staffm.commands.StaffMCommand;
import dev.aerune1.staffm.database.DatabaseManager;
import dev.aerune1.staffm.listeners.SessionListener;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "staffm", 
    name = "StaffM", 
    version = "1.0", 
    description = "Staff Activity Tracker", 
    authors = {"Aerune1"}
)
public class StaffM {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private SessionListener sessionListener;
    
    // Channel format: "namespace:channel"
    private final MinecraftChannelIdentifier AFK_CHANNEL = MinecraftChannelIdentifier.from("staffm:afk");

    @Inject
    public StaffM(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Loading StaffM by Aerune1...");

        // 1. Load Config
        this.configManager = new ConfigManager(dataDirectory, logger);

        // 2. Load Database
        this.databaseManager = new DatabaseManager(configManager, dataDirectory);

        // 3. Register Plugin Messaging Channel
        proxy.getChannelRegistrar().register(AFK_CHANNEL);

        // 4. Register Listeners
        this.sessionListener = new SessionListener(this);
        proxy.getEventManager().register(this, sessionListener);

        // 5. Register Commands
        proxy.getCommandManager().register("staffm", new StaffMCommand(this));

        logger.info("StaffM loaded successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void reloadConfig() {
        configManager.load();
    }

    public ConfigManager getConfig() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public MinecraftChannelIdentifier getAfkChannel() {
        return AFK_CHANNEL;
    }
}