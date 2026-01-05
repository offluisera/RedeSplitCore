package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AutoMessageTask extends BukkitRunnable {

    @Override
    public void run() {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                // Conta punições nas últimas 24h
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as total FROM rs_punishments WHERE date > NOW() - INTERVAL 1 DAY"
                );
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt("total");
                    String msg = "§e§lINFO: §fA nossa equipe baniu/mutou §c" + count + " §fusuários irregulares nas últimas 24h!";

                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage(msg);
                        Bukkit.broadcastMessage("");
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}