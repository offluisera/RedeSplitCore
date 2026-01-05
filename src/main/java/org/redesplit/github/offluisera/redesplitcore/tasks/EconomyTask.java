package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class EconomyTask implements Runnable {

    private final RedeSplitCore plugin;

    public EconomyTask(RedeSplitCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                // 1. Soma tudo da rs_players
                double totalCoins = 0;
                double totalCash = 0;

                try (PreparedStatement ps = conn.prepareStatement("SELECT SUM(coins) as tCoins, SUM(cash) as tCash FROM rs_players")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        totalCoins = rs.getDouble("tCoins");
                        totalCash = rs.getDouble("tCash");
                    }
                }

                // 2. Salva no histórico (Usa ON DUPLICATE para atualizar se rodar no mesmo dia)
                String sql = "INSERT INTO rs_economy_history (total_coins, total_cash, date) VALUES (?, ?, CURDATE()) " +
                        "ON DUPLICATE KEY UPDATE total_coins = ?, total_cash = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDouble(1, totalCoins);
                    ps.setDouble(2, totalCash);
                    ps.setDouble(3, totalCoins);
                    ps.setDouble(4, totalCash);
                    ps.executeUpdate();
                }

                plugin.getLogger().info("Snapshop da economia salvo com sucesso!");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}