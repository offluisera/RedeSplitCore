package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public class ServerStatsTask extends BukkitRunnable {

    @Override
    public void run() {
        // 1. SEGURANÇA: Se o plugin estiver desativando, cancela e para.
        if (!RedeSplitCore.getInstance().isEnabled()) {
            this.cancel();
            return;
        }

        // 2. Coleta os dados
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        int online = players.size();
        int maxPlayers = Bukkit.getMaxPlayers();

        double tps = getTPS();
        if (tps > 20.0) tps = 20.0;

        // RAM
        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long maxMem = Runtime.getRuntime().maxMemory();

        double usedRamMb = (totalMem - freeMem) / 1024.0 / 1024.0;
        long usedRamLong = (totalMem - freeMem) / 1048576L;
        long maxRamLong = maxMem / 1048576L;

        double finalTps = tps;

        // 3. Constrói a lista de nomes JSON
        StringBuilder playerListJson = new StringBuilder("[");
        int count = 0;
        for (Player p : players) {
            if (count > 0) playerListJson.append(",");
            playerListJson.append("\"").append(p.getName()).append("\"");
            count++;
        }
        playerListJson.append("]");

        // 4. Verificação Dupla antes de agendar Async
        if (!RedeSplitCore.getInstance().isEnabled()) return;

        try {
            Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
                if (!RedeSplitCore.getInstance().isEnabled()) return; // Tripla checagem (segurança máxima)

                // A. MySQL
                try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO rs_server_stats (online_players, tps, ram_usage) VALUES (?, ?, ?)");
                    ps.setInt(1, online);
                    ps.setDouble(2, finalTps);
                    ps.setDouble(3, usedRamMb);
                    ps.executeUpdate();

                    // Limpeza (opcional, pode deixar para fazer só no onDisable se preferir economizar recurso)
                    // conn.createStatement().executeUpdate("DELETE FROM rs_server_stats WHERE date < NOW() - INTERVAL 7 DAY");
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // B. REDIS
                try {
                    String serverId = RedeSplitCore.getInstance().getServerId();

                    String json = String.format(
                            "{\"tps\": %.2f, \"ram_used\": %d, \"ram_max\": %d, \"online\": %d, \"max_players\": %d, \"list\": %s}",
                            finalTps, usedRamLong, maxRamLong, online, maxPlayers, playerListJson.toString()
                    );

                    String payload = "PERFORMANCE|" + serverId + "|" + json;

                    RedeSplitCore.getInstance().getRedisManager().publish("redesplit:channel", payload);

                } catch (Exception e) {
                    // Ignora erros de Redis
                }
            });
        } catch (Exception e) {
            // Se der erro ao agendar (plugin desativou no milissegundo exato), ignora.
        }
    }

    private double getTPS() {
        try {
            Object server = Bukkit.getServer();
            Method getServerMethod = server.getClass().getMethod("getServer");
            Object nmsServer = getServerMethod.invoke(server);
            Field recentTpsField = nmsServer.getClass().getField("recentTps");
            double[] recentTps = (double[]) recentTpsField.get(nmsServer);
            return recentTps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
}