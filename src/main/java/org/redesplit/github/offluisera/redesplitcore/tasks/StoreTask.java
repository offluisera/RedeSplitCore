package org.redesplit.github.offluisera.redesplitcore.tasks; // Ajuste para seu pacote

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StoreTask extends BukkitRunnable {

    private final RedeSplitCore plugin;

    public StoreTask(RedeSplitCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Roda de forma ASSÍNCRONA para não travar o servidor (lag)
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = plugin.getMySQL().getConnection()) { // Pega conexão fresca
                    if (conn == null || conn.isClosed()) return;

                    // 1. Busca comandos pendentes
                    PreparedStatement st = conn.prepareStatement("SELECT id, command FROM rs_command_queue WHERE status = 'WAITING'");
                    ResultSet rs = st.executeQuery();

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String cmd = rs.getString("command");

                        // 2. Volta para a thread PRINCIPAL (Sync) para executar o comando
                        // Comandos Bukkit NÃO podem rodar em Async!
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                Bukkit.getLogger().info("§a[Loja] Comando executado: " + cmd);
                            }
                        }.runTask(plugin);

                        // 3. Deleta o comando da fila para não executar de novo
                        PreparedStatement del = conn.prepareStatement("DELETE FROM rs_command_queue WHERE id = ?");
                        del.setInt(1, id);
                        del.executeUpdate();
                        del.close();
                    }
                    rs.close();
                    st.close();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}