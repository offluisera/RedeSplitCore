package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class RankExpirationTask extends BukkitRunnable {

    @Override
    public void run() {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. VERIFICA RANKS VENCIDOS
                PreparedStatement psRank = conn.prepareStatement(
                        "SELECT uuid, previous_rank FROM rs_temp_ranks WHERE expires < NOW()");
                ResultSet rsRank = psRank.executeQuery();

                while (rsRank.next()) {
                    String uuidStr = rsRank.getString("uuid");
                    String prevRank = rsRank.getString("previous_rank");

                    // Restaura o rank antigo na tabela principal
                    PreparedStatement psRestore = conn.prepareStatement("UPDATE rs_players SET rank_id = ? WHERE uuid = ?");
                    psRestore.setString(1, prevRank);
                    psRestore.setString(2, uuidStr);
                    psRestore.executeUpdate();

                    // Deleta da tabela temporária
                    PreparedStatement psDel = conn.prepareStatement("DELETE FROM rs_temp_ranks WHERE uuid = ?");
                    psDel.setString(1, uuidStr);
                    psDel.executeUpdate();

                    // Atualiza jogador se estiver online
                    updatePlayerOnline(uuidStr, prevRank);
                    RedeSplitCore.getInstance().getLogger().info("Rank temporario expirou para UUID: " + uuidStr);
                }

                // 2. VERIFICA PERMISSÕES VENCIDAS (Basta setar active=0)
                // O PlayerManager já filtra active=1 e datas validas, então aqui só limpamos
                PreparedStatement psPerm = conn.prepareStatement(
                        "UPDATE rs_user_permissions SET active = 0 WHERE expires < NOW() AND active = 1");
                int permsExpired = psPerm.executeUpdate();

                // Se expirou alguma permissão, idealmente recarregamos todos para garantir (ou fazemos uma query mais complexa para saber quem foi)
                if (permsExpired > 0) {
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                        }
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updatePlayerOnline(String uuidStr, String newRank) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(uuid);
                    if (sp != null) {
                        sp.setRankId(newRank);
                        TagManager.update(p);
                        RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                        p.sendMessage("§eSeu rank temporário expirou. Você voltou a ser " + newRank);
                    }
                }
            } catch (Exception e) {}
        });
    }
}