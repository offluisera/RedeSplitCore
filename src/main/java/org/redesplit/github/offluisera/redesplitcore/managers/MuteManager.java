package org.redesplit.github.offluisera.redesplitcore.managers;

import org.bukkit.Bukkit;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MuteManager {

    // Armazena: Nome -> Data de Expiração (Timestamp)
    private static final Map<String, Long> mutedPlayers = new HashMap<>();
    // Armazena: Nome -> Motivo
    private static final Map<String, String> muteReasons = new HashMap<>();

    // Adiciona um mute na memória (Chamado pelo Redis ou ao entrar no server)
    public static void setMute(String playerName, long expireTimestamp, String reason) {
        mutedPlayers.put(playerName.toLowerCase(), expireTimestamp);
        muteReasons.put(playerName.toLowerCase(), reason);
    }

    // Remove mute da memória
    public static void unmute(String playerName) {
        mutedPlayers.remove(playerName.toLowerCase());
        muteReasons.remove(playerName.toLowerCase());
    }

    // Verifica se está mutado
    public static boolean isMuted(String playerName) {
        if (!mutedPlayers.containsKey(playerName.toLowerCase())) return false;

        long expire = mutedPlayers.get(playerName.toLowerCase());

        // Se expire for muito longe (tipo ano 2099) ou maior que agora, está mutado
        if (expire > System.currentTimeMillis()) {
            return true;
        } else {
            // Se já venceu, remove da memória pra limpar
            unmute(playerName);
            return false;
        }
    }

    public static String getReason(String playerName) {
        return muteReasons.getOrDefault(playerName.toLowerCase(), "Sem motivo");
    }

    public static long getExpiration(String playerName) {
        return mutedPlayers.getOrDefault(playerName.toLowerCase(), 0L);
    }

    // Carrega mutes do Banco de Dados (Usado quando o player entra)
    public static void loadMuteData(String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT expires, reason FROM rs_punishments WHERE player_name = ? AND type = 'MUTE' AND active = 1");
                ps.setString(1, playerName);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    long expireTime = rs.getTimestamp("expires").getTime();
                    // Se ainda é válido
                    if (expireTime > System.currentTimeMillis()) {
                        setMute(playerName, expireTime, rs.getString("reason"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}