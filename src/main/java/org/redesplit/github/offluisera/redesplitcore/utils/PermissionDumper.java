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

            // 1. Coleta todas as permissões registradas no servidor
            Set<String> perms = new HashSet<>();
            for (Permission p : Bukkit.getPluginManager().getPermissions()) {
                perms.add(p.getName());
            }

            // Adiciona algumas permissões padrões do Bukkit/Minecraft que as vezes não aparecem
            perms.add("minecraft.command.op");
            perms.add("minecraft.command.ban");
            // Adicione outras manuais se quiser

            if (perms.isEmpty()) return;

            // 2. Salva no Banco (Batch Insert para performance)
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // Limpa a tabela antiga para não ficar lixo de plugins removidos
                conn.createStatement().executeUpdate("TRUNCATE TABLE rs_known_permissions");

                // Prepara a inserção em massa
                PreparedStatement ps = conn.prepareStatement("INSERT INTO rs_known_permissions (permission) VALUES (?)");

                int count = 0;
                for (String perm : perms) {
                    ps.setString(1, perm);
                    ps.addBatch();
                    count++;

                    // Executa a cada 100 registros para não sobrecarregar
                    if (count % 100 == 0) ps.executeBatch();
                }
                ps.executeBatch(); // Executa o restante

                RedeSplitCore.getInstance().getLogger().info("Dump de permissões concluído: " + count + " sugestões salvas.");

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}