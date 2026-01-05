package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

public class RestartListener implements Listener {

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        if (RedeSplitCore.getInstance().isRestarting()) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c§lSERVIDOR REINICIANDO\n\n" +
                            "§cO servidor já está em processo de reinicialização.\n" +
                            "§cAguarde alguns instantes para voltar."
            );
        }
    }
}