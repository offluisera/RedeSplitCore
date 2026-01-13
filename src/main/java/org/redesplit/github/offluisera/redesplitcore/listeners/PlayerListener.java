package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;

public class PlayerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Carregar dados do MySQL para o Cache
        // Isso inclui: rank, coins, cash, mute, e XP
        RedeSplitCore.getInstance().getPlayerManager().loadPlayer(
                player.getUniqueId(),
                player.getName()
        );

        // 2. Aplicar Tag, TabList e XP Bar com delay (aguarda o cache carregar)
        Bukkit.getScheduler().runTaskLater(RedeSplitCore.getInstance(), () -> {
            if (player.isOnline()) {
                // Atualiza tag e tablist
                TagManager.update(player);

                // ⭐ ATUALIZA BARRA DE XP VISUAL
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(player.getUniqueId());
                if (sp != null) {
                    RedeSplitCore.getInstance().getXPManager().updateXPBar(player, sp);

                }
            }
        }, 20L); // 1 segundo de segurança
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // 1. Remover o jogador do sistema de times para evitar fantasmas no Scoreboard
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (team.hasEntry(playerName)) {
                team.removeEntry(playerName);
            }
        }

        // 2. Salvar dados e remover do cache
        // Isso automaticamente salva o XP através do unloadPlayer()
        RedeSplitCore.getInstance().getPlayerManager().unloadPlayer(player.getUniqueId());
    }
}