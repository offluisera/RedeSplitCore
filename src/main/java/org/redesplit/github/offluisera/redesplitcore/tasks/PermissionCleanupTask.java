package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class PermissionCleanupTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!RedeSplitCore.getInstance().isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Deleta permissões onde expiration < AGORA
                PreparedStatement st = conn.prepareStatement(
                        "DELETE FROM rs_ranks_permissions WHERE expiration IS NOT NULL AND expiration < NOW()"
                );
                int deleted = st.executeUpdate();
                st.close();

                // 2. Se apagou algo, avisa todos os servidores para recarregar permissões
                if (deleted > 0) {
                    RedeSplitCore.getInstance().getLogger().info("§e[Permissões] " + deleted + " permissões temporárias expiraram e foram removidas.");

                    // Envia comando via Redis para dar refresh geral
                    // PERM_UPDATE|ALL|* (O asterisco pode simbolizar 'todos os cargos' se seu sistema suportar, ou mande null)
                    // Na dúvida, se expirou algo, é melhor pedir refresh geral ou em quem estava online.
                    // Mas como é complexo saber qual rank expirou na query de delete simples, vamos avisar geral.

                    // Como seu RedisSubscriber espera um rank específico, você pode adaptar para aceitar "ALL"
                    // Ou fazer uma query SELECT antes do DELETE para saber quais ranks afetou.

                    // Para simplificar, vamos assumir que seu RedisSubscriber recarrega tudo se receber "ALL" no lugar do RankID
                    RedeSplitCore.getInstance().getRedisManager().publish("redesplit:channel", "PERM_UPDATE|ALL|ALL_RANKS");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}