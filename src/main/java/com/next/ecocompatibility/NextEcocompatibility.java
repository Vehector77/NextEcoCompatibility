package com.next.ecocompatibility;

import org.bukkit.plugin.java.JavaPlugin;
import com.next.ecocompatibility.listeners.EnchantListener;

public final class NextEcocompatibility extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        getLogger().info("NextEcocompatibility has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NextEcocompatibility has been disabled!");
    }
}
