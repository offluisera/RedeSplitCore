package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Importante para o Silenciamento
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConnectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST) // Prioridade máxima para garantir o silenciamento
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null); // Remove mensagem padrão

        org.redesplit.github.offluisera.redesplitcore.managers.MuteManager.loadMuteData(event.getPlayer().getName());
        RedeSplitCore.getInstance().getVanishManager().hideVanishedFrom(event.getPlayer());

        Player player = event.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();

        // 1. Carrega os dados do jogador
        RedeSplitCore.getInstance().getPlayerManager().loadPlayer(player.getUniqueId(), player.getName());

        // 2. Processamento Assíncrono (IP e Assinatura VIP)
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = MySQL.getConnection()) {

                // Atualiza o Last IP
                PreparedStatement psLast = conn.prepareStatement("UPDATE rs_players SET last_ip = ? WHERE uuid = ?");
                psLast.setString(1, ip);
                psLast.setString(2, player.getUniqueId().toString());
                psLast.executeUpdate();

                // Histórico de IP
                PreparedStatement psLog = conn.prepareStatement("INSERT INTO rs_ip_history (uuid, ip) VALUES (?, ?)");
                psLog.setString(1, player.getUniqueId().toString());
                psLog.setString(2, ip);
                psLog.executeUpdate();

                // --- ASSINATURA VIP (Lógica Atualizada) ---
                PreparedStatement psJoin = conn.prepareStatement("SELECT join_message, join_color, rank_id FROM rs_players WHERE uuid = ?");
                psJoin.setString(1, player.getUniqueId().toString());
                ResultSet rs = psJoin.executeQuery();

                if (rs.next()) {
                    String joinMsg = rs.getString("join_message");
                    String joinColor = rs.getString("join_color");
                    String rank = rs.getString("rank_id").toUpperCase(); // Padroniza em maiúsculo

                    // Só envia se tiver mensagem e não for membro
                    if (joinMsg != null && !joinMsg.isEmpty() && !rank.equalsIgnoreCase("MEMBRO")) {

                        if (joinColor == null || joinColor.isEmpty()) joinColor = "§b";

                        // Cores dos Ranks
                        String rankColor = "§7";
                        if (rank.equals("MASTER")) rankColor = "§6";
                        if (rank.equals("VIP")) rankColor = "§a";
                        if (rank.equals("STAFF")) rankColor = "§c";

                        // Formata o comando para o chat (rsanuncio)
                        String commandContent = "rsanuncio " + rankColor + rank + " " + rankColor + player.getName() + " " + joinColor + joinMsg;

                        // NOVO FORMATO REDIS: CMD;DESTINO;CONTEUDO
                        // Enviamos para 'lobby' (conforme sua solicitação) ou 'ALL' se preferir global
                        String redisPayload = "CMD;lobby;" + commandContent;

                        // Ou se quiser que apareça APENAS no servidor atual:
                        // String redisPayload = "CMD;" + RedeSplitCore.getInstance().getServerId() + ";" + commandContent;

                        RedeSplitCore.getInstance().getLogger().info("DEBUG: Enviando Redis: " + redisPayload);
                        RedeSplitCore.getInstance().getRedisManager().publish("redesplit:channel", redisPayload);

                    } else {
                        // Debug opcional para saber quem não tem mensagem
                        RedeSplitCore.getInstance().getLogger().info("DEBUG: Sem assinatura para " + player.getName());
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null); // Opcional: Remove mensagem de saída também

        Player p = event.getPlayer();
        if (p.hasPermission("redesplit.sc")) {
            Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
                try (Connection conn = MySQL.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM rs_staff_status WHERE player_name = ?");
                    ps.setString(1, p.getName());
                    ps.executeUpdate();
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
        RedeSplitCore.getInstance().getPlayerManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}