package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class StaffChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.sc")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUse: /sc <mensagem>");
            return true;
        }

        String msg = String.join(" ", args);
        String user = sender.getName();

        // 1. Envia para o Redis (Instantâneo)
        // O RedisListener vai receber isso e mostrar para quem está online
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try {
                // Formato: STAFF_CHAT;Nick;Mensagem
                // Usamos um Jedis temporário aqui ou o Manager se tiver método publish público
                // Para simplificar, vou abrir uma conexão rápida:
                try (Jedis jedis = new Jedis("82.39.107.62", 6379)) { // Use o IP da VPS se for externo
                    jedis.auth("UHAFDjbnakfye@@jouiayhfiqwer903");
                    jedis.publish("redesplit:channel", "STAFF_CHAT;" + user + ";" + msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Salva no MySQL (Para o histórico do Site)
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO rs_staff_chat (username, message, source) VALUES (?, ?, 'GAME')");
                ps.setString(1, user);
                ps.setString(2, msg);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return true;
    }
}