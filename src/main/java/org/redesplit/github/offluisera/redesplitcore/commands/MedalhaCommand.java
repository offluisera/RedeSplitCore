package org.redesplit.github.offluisera.redesplitcore.commands;

import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MedalhaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Verifica permissão
        if (!sender.hasPermission("redesplit.admin")) {
            sender.sendMessage("§cVocê não tem permissão para usar este comando.");
            return true;
        }

        // 2. Verifica se tem os 3 argumentos: <dar/remover> <jogador> <id>
        if (args.length < 3) {
            sender.sendMessage("§c§lERRO: §eUse /medalha <dar/remover> <jogador> <id>");
            sender.sendMessage("§7Exemplo: /medalha dar offluisera primordio");
            return true;
        }

        String acao = args[0].toLowerCase();
        String target = args[1];
        String badgeId = args[2].toLowerCase();

        // 3. Execução Assíncrona para não travar o servidor
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = MySQL.getConnection()) {
                if (acao.equals("dar")) {
                    String sql = "INSERT INTO rs_player_badges (player_name, badge_id) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, target);
                        ps.setString(2, badgeId);
                        ps.executeUpdate();
                        sender.sendMessage("§a§lSUCESSO! §fMedalha §e" + badgeId + " §fentregue para §b" + target);
                    }
                } else if (acao.equals("remover")) {
                    String sql = "DELETE FROM rs_player_badges WHERE player_name = ? AND badge_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, target);
                        ps.setString(2, badgeId);
                        int rows = ps.executeUpdate();
                        if (rows > 0) {
                            sender.sendMessage("§e§lAVISO: §fMedalha §e" + badgeId + " §fremovida de §b" + target);
                        } else {
                            sender.sendMessage("§c§lERRO: §fO jogador não possui esta medalha.");
                        }
                    }
                } else {
                    sender.sendMessage("§c§lERRO: §fAção inválida. Use 'dar' ou 'remover'.");
                }
            } catch (SQLException e) {
                sender.sendMessage("§c§lERRO CRÍTICO: §fFalha ao acessar o banco de dados.");
                e.printStackTrace();
            }
        });

        return true; // Retornar true impede a mensagem branca de 'usage'
    }
}