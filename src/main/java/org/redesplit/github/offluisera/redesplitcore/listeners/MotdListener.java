package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.redesplit.github.offluisera.redesplitcore.managers.MotdManager;

public class MotdListener implements Listener {

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        e.setMotd(MotdManager.getFormattedMotd());

        // Dica Extra: Você pode mudar o número de players aqui também se quiser!
        // e.setMaxPlayers(e.getNumPlayers() + 1); // Mostra sempre "Lotado"
    }
}