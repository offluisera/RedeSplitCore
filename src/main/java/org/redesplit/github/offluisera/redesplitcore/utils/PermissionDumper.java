package org.redesplit.github.offluisera.redesplitcore.utils;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class PermissionDumper {

    public static void dump() {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {

            // === VERIFICAÇÃO ===
            if (RedeSplitCore.getInstance().isShuttingDown()) {
                return;
            }

            if (!RedeSplitCore.getInstance().getMySQL().isConnected()) {
                return;
            }

            // Coleta permissões
            Set<String> perms = new HashSet<>();
            for (Permission p : Bukkit.getPluginManager().getPermissions()) {
                perms.add(p.getName());
            }

            if (perms.isEmpty()) return;

            Connection conn = null;
            PreparedStatement ps = null;

            try {
                conn = RedeSplitCore.getInstance().getMySQL().getConnection();

                // Limpa tabela
                conn.createStatement().executeUpdate("TRUNCATE TABLE rs_known_permissions");

                // Insere permissões
                ps = conn.prepareStatement("INSERT INTO rs_known_permissions (permission) VALUES (?)");

                int count = 0;
                for (String perm : perms) {
                    ps.setString(1, perm);
                    ps.addBatch();
                    count++;

                    if (count % 100 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();

                RedeSplitCore.getInstance().getLogger().info(
                        "§a[Perm] Dump concluído: " + count + " permissões salvas."
                );

            } catch (SQLException e) {
                // Só loga se não estiver em shutdown
                if (!RedeSplitCore.getInstance().isShuttingDown()) {
                    e.printStackTrace();
                }
            } finally {
                try { if (ps != null) ps.close(); } catch (Exception e) {}
                try { if (conn != null) conn.close(); } catch (Exception e) {}
            }
        });
    }
}