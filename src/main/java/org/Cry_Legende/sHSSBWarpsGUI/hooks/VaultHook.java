package org.Cry_Legende.sHSSBWarpsGUI.hooks;

import net.milkbowl.vault.economy.Economy;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private final SHSSBWarpsGUI plugin;
    private Economy economy = null;

    public VaultHook(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) { // Utiliser plugin.getServer()
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (economy == null) {
            // Si l'économie n'est pas disponible et que le coût est > 0, considérer que le joueur n'a pas assez.
            // Sauf si vous voulez que la fonctionnalité soit gratuite en l'absence d'économie.
            return amount <= 0;
        }
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) { // Ne pas retirer si pas d'éco ou montant nul/négatif
            return amount <= 0; // Succès si le montant est nul ou négatif (rien à retirer)
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}