package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Statistic;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReferralCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length != 1) {
            p.sendMessage("§cUse: /indique <código>");
            return true;
        }

        String code = args[0];

        // Regra: Jogadores antigos não podem usar (Ex: mais de 30min de jogo)
        // 1 tick = 1/20 segundos. 30 min = 36000 ticks.
        if (p.getStatistic(Statistic.PLAY_ONE_TICK) > 36000) {
            p.sendMessage("§cVocê já joga há muito tempo para ser indicado.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Verifica se já foi indicado
                PreparedStatement check = conn.prepareStatement("SELECT id FROM rs_referrals WHERE invited_uuid = ?");
                check.setString(1, p.getUniqueId().toString());
                if (check.executeQuery().next()) {
                    p.sendMessage("§cVocê já usou um código de indicação!");
                    return;
                }

                // 2. Procura quem é o dono do código
                PreparedStatement find = conn.prepareStatement("SELECT uuid FROM rs_referral_codes WHERE code = ?");
                find.setString(1, code);
                ResultSet rs = find.executeQuery();

                if (rs.next()) {
                    String inviterUUID = rs.getString("uuid");

                    if (inviterUUID.equals(p.getUniqueId().toString())) {
                        p.sendMessage("§cVocê não pode indicar a si mesmo.");
                        return;
                    }

                    // 3. Registra a indicação
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO rs_referrals (inviter_uuid, invited_uuid) VALUES (?, ?)");
                    insert.setString(1, inviterUUID);
                    insert.setString(2, p.getUniqueId().toString());
                    insert.executeUpdate();

                    p.sendMessage("§a§lSUCESSO! §aVocê vinculou a indicação de " + code + ".");
                    p.sendMessage("§eJogue por 1 hora para receber recompensas!");

                } else {
                    p.sendMessage("§cCódigo inválido.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                p.sendMessage("§cErro ao processar indicação.");
            }
        });

        return true;
    }
}