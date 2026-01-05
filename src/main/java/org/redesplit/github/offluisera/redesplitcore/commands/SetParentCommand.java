package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SetParentCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.admin")) return true;

        if (args.length != 2) {
            sender.sendMessage("§cUso: /setparent <cargo_filho> <cargo_pai_para_adicionar>");
            return true;
        }

        String child = args[0];
        String parent = args[1];

        if (child.equalsIgnoreCase(parent)) {
            sender.sendMessage("§cUm cargo não pode herdar dele mesmo.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Verifica se ambos existem
                PreparedStatement check = conn.prepareStatement("SELECT rank_id FROM rs_ranks WHERE rank_id IN (?, ?)");
                check.setString(1, child);
                check.setString(2, parent);
                ResultSet rs = check.executeQuery();
                int count = 0;
                while(rs.next()) count++;

                if (count < 2) {
                    sender.sendMessage("§cErro: Um dos cargos não existe. Use /ranks para ver a lista.");
                    return;
                }

                // 2. Insere a herança (IGNORE para não duplicar se já existir)
                PreparedStatement st = conn.prepareStatement("INSERT IGNORE INTO rs_ranks_inheritance (child_rank, parent_rank) VALUES (?, ?)");
                st.setString(1, child);
                st.setString(2, parent);
                int rows = st.executeUpdate();

                if (rows > 0) {
                    sender.sendMessage("§aSucesso! O cargo §f" + child + "§a agora herda permissões de §f" + parent + "§a.");
                    // Notifica Redis para atualizar
                    RedeSplitCore.getInstance().getRedisManager().publish("redesplit:channel", "PERM_UPDATE|ALL|ALL_RANKS");
                } else {
                    sender.sendMessage("§eO cargo " + child + " já herdava de " + parent + ".");
                }

                rs.close();
                check.close();
                st.close();

            } catch (Exception e) {
                sender.sendMessage("§cErro ao definir herança.");
                e.printStackTrace();
            }
        });

        return true;
    }
}