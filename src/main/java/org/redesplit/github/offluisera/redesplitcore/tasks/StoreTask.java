package org.redesplit.github.offluisera.redesplitcore.tasks;

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
        // === VERIFICAÇÃO CRÍTICA ===
        if (plugin.isShuttingDown()) {
            return; // Para silenciosamente durante shutdown
        }

        if (!plugin.isEnabled()) {
            return;
        }

        // Executa assíncrono
        new BukkitRunnable() {
            @Override
            public void run() {
                // Verifica novamente antes de pegar conexão
                if (plugin.isShuttingDown()) {
                    return;
                }

                Connection conn = null;
                PreparedStatement st = null;
                ResultSet rs = null;

                try {
                    // Verifica se MySQL está conectado
                    if (!plugin.getMySQL().isConnected()) {
                        return;
                    }

                    conn = plugin.getMySQL().getConnection();
                    if (conn == null || conn.isClosed()) {
                        return;
                    }

                    // Busca comandos pendentes
                    st = conn.prepareStatement(
                            "SELECT id, command FROM rs_command_queue WHERE status = 'WAITING'"
                    );
                    rs = st.executeQuery();

                    while (rs.next()) {
                        final int id = rs.getInt("id");
                        final String cmd = rs.getString("command");

                        // Executa comando na thread principal
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                plugin.getLogger().info("§a[Loja] Comando executado: " + cmd);
                            }
                        }.runTask(plugin);

                        // Remove da fila
                        try (PreparedStatement del = conn.prepareStatement(
                                "DELETE FROM rs_command_queue WHERE id = ?"
                        )) {
                            del.setInt(1, id);
                            del.executeUpdate();
                        }
                    }

                } catch (SQLException e) {
                    // Só loga se não estiver em shutdown
                    if (!plugin.isShuttingDown()) {
                        plugin.getLogger().warning("§c[Loja] Erro ao buscar fila: " + e.getMessage());
                    }
                } finally {
                    // Fecha recursos
                    try { if (rs != null) rs.close(); } catch (Exception e) {}
                    try { if (st != null) st.close(); } catch (Exception e) {}
                    try { if (conn != null) conn.close(); } catch (Exception e) {}
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}