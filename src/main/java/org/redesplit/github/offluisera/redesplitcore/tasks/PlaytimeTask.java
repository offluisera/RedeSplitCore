package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlaytimeTask extends BukkitRunnable {

    @Override
    public void run() {
        // Salva a cada 5 minutos (6000 ticks) ou quando quiser
        // Mas para contar tempo, é melhor somar 1 minuto a cada minuto que passa

        long now = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                // Adiciona 1 minuto (60000ms) para todos online
                // Uma query única para otimizar
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    StringBuilder sql = new StringBuilder("UPDATE rs_players SET playtime = playtime + 60000 WHERE name IN (");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sql.append("'").append(p.getName()).append("',");
                    }
                    // Remove a última vírgula e fecha
                    String finalSql = sql.substring(0, sql.length() - 1) + ")";

                    try (PreparedStatement ps = conn.prepareStatement(finalSql)) {
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}