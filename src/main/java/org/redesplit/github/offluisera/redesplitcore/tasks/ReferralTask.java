package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class ReferralTask extends BukkitRunnable {

    @Override
    public void run() {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // Busca indicações PENDENTES
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT inviter_uuid, invited_uuid FROM rs_referrals WHERE status = 'PENDING'");
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String invitedUUIDStr = rs.getString("invited_uuid");
                    String inviterUUIDStr = rs.getString("inviter_uuid");
                    UUID invitedUUID = UUID.fromString(invitedUUIDStr);

                    // Verifica se o novato está online
                    Player novato = Bukkit.getPlayer(invitedUUID);
                    if (novato != null) {
                        // Verifica se bateu 1 hora (72.000 ticks)
                        if (novato.getStatistic(Statistic.PLAY_ONE_TICK) >= 72000) {

                            // CONCLUI A MISSÃO
                            completeReferral(conn, inviterUUIDStr, novato);
                        }
                    }
                }

            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void completeReferral(Connection conn, String inviterUUIDStr, Player novato) {
        try {
            // 1. Atualiza Status no Banco
            PreparedStatement up = conn.prepareStatement(
                    "UPDATE rs_referrals SET status = 'COMPLETED' WHERE invited_uuid = ?");
            up.setString(1, novato.getUniqueId().toString());
            up.executeUpdate();

            // 2. Entrega Prêmios (Volta pra thread principal para dar comando)
            Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                // Prêmio Novato
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate give key basica " + novato.getName() + " 1");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + novato.getName() + " 5000");
                novato.sendMessage("§a§lPARABÉNS! §eVocê completou 1 hora de jogo por indicação!");
                novato.sendMessage("§eVocê recebeu: §f1x Chave Básica e $5.000 Coins.");

                // Prêmio Veterano (Quem indicou)
                Player veterano = Bukkit.getPlayer(UUID.fromString(inviterUUIDStr));
                if (veterano != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate give key rara " + veterano.getName() + " 1");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cash give " + veterano.getName() + " 100");
                    veterano.sendMessage("§a§lREFERRAL! §eSeu amigo " + novato.getName() + " completou 1 hora!");
                    veterano.sendMessage("§eVocê recebeu: §f1x Chave Rara e 100 Cash.");
                } else {
                    // Se o veterano estiver offline, você teria que salvar para entregar depois
                    // (Implementação futura de 'recompensas pendentes')
                }
            });

        } catch (Exception e) { e.printStackTrace(); }
    }
}