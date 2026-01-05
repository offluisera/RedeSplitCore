package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUso: /check <nick>");
            return true;
        }

        String targetName = args[0];

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Busca dados do jogador (Assumindo tabela rs_players com coluna rank_id)
                PreparedStatement st = conn.prepareStatement(
                        "SELECT p.rank_id, r.display_name, r.prefix " +
                                "FROM rs_players p " +
                                "LEFT JOIN rs_ranks r ON p.rank_id = r.rank_id " +
                                "WHERE p.name = ?"
                );
                st.setString(1, targetName);
                ResultSet rs = st.executeQuery();

                if (rs.next()) {
                    String rankId = rs.getString("rank_id");
                    String display = rs.getString("display_name");
                    String prefix = rs.getString("prefix");
                    if(display == null) display = rankId; // Fallback

                    sender.sendMessage("");
                    sender.sendMessage("§e§l[PERFIL] §f" + targetName);
                    sender.sendMessage("§7Rank Atual: §f" + display + " §7(" + rankId + ")");
                    sender.sendMessage("§7Prefixo: §f" + (prefix != null ? prefix.replace("&", "§") : "Nenhum"));

                    // Opcional: Buscar permissões extras (user-specific) se você tiver essa tabela
                    // sender.sendMessage("§7Permissões Específicas: (Implementar query se houver)");

                } else {
                    sender.sendMessage("§cJogador §f" + targetName + "§c não encontrado no banco de dados.");
                }
                rs.close();
                st.close();

            } catch (Exception e) {
                sender.sendMessage("§cErro ao buscar dados.");
                e.printStackTrace();
            }
        });

        return true;
    }
}