package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeliveryTask extends BukkitRunnable {

    private final MySQL mysql;

    public DeliveryTask(MySQL mysql) {
        this.mysql = mysql;
    }

    @Override
    public void run() {
        try {
            Connection conn = mysql.getConnection();
            if (conn == null || conn.isClosed()) return;

            // 1. Busca comandos pendentes (LIMIT 10 para não lagar se tiver muitos)
            PreparedStatement st = conn.prepareStatement("SELECT * FROM rs_delivery_queue WHERE status = 'PENDING' LIMIT 10");
            ResultSet rs = st.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String player = rs.getString("player_name");
                String command = rs.getString("command");

                // 2. Executa o comando na Thread Principal (OBRIGATÓRIO para evitar crash)
                // O comando já vem pronto do PHP (ex: "give Steve diamond 64")
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SeuPluginMain"), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    Bukkit.getLogger().info("[RedeSplit] Executando entrega para " + player + ": " + command);
                });

                // 3. Marca como entregue para não repetir
                PreparedStatement update = conn.prepareStatement("UPDATE rs_delivery_queue SET status = 'DELIVERED' WHERE id = ?");
                update.setInt(1, id);
                update.executeUpdate();
                update.close();
            }

            rs.close();
            st.close();

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[RedeSplit] Erro ao buscar fila de entregas: " + e.getMessage());
        }
    }
}