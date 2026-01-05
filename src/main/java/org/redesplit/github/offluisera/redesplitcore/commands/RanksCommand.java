package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RanksCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.admin")) return true;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                List<String> ranks = new ArrayList<>();
                ResultSet rs = conn.createStatement().executeQuery("SELECT rank_id, display_name, color FROM rs_ranks ORDER BY rank_id ASC");

                while (rs.next()) {
                    String id = rs.getString("rank_id");
                    String name = rs.getString("display_name");
                    String color = rs.getString("color"); // Ex: "c" para vermelho
                    ranks.add("§" + color + name + " §7(" + id + ")");
                }

                sender.sendMessage("");
                sender.sendMessage("§e§lLISTA DE CARGOS DISPONÍVEIS:");
                for (String r : ranks) {
                    sender.sendMessage(" §8- " + r);
                }
                sender.sendMessage("");

                rs.close();

            } catch (Exception e) {
                sender.sendMessage("§cErro ao listar cargos.");
            }
        });

        return true;
    }
}