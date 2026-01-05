package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;

public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Carregar dados do MySQL para o Cache
        RedeSplitCore.getInstance().getPlayerManager().loadPlayer(
                player.getUniqueId(),
                player.getName()
        );

        // 2. Aplicar Tag e TabList com um pequeno delay (aguarda o cache carregar)
        Bukkit.getScheduler().runTaskLater(RedeSplitCore.getInstance(), () -> {
            if (player.isOnline()) {
                TagManager.update(player);
            }
        }, 20L); // 1 segundo de segurança
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();

        // 1. Remover o jogador do sistema de times para evitar fantasmas no Scoreboard
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (team.hasEntry(playerName)) {
                team.removeEntry(playerName);
            }
        }

        // 2. Salvar dados e remover do cache
        RedeSplitCore.getInstance().getPlayerManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}