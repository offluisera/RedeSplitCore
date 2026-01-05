package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.managers.MuteManager;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        // 1. VERIFICAÇÃO DE MUTE (AGORA PELA MEMÓRIA RAM - INSTANTÂNEO)
        if (MuteManager.isMuted(player.getName())) {
            event.setCancelled(true);

            long expires = MuteManager.getExpiration(player.getName());
            String reason = MuteManager.getReason(player.getName());

            // Verifica se é permanente (ano > 2090)
            boolean isPerm = expires > 4102444800000L; // Data em 2100

            String timeString;
            if (isPerm) {
                timeString = "Permanente";
            } else {
                long diff = expires - System.currentTimeMillis();
                long minutes = diff / (60 * 1000);
                timeString = (minutes < 1) ? "menos de 1 minuto" : minutes + " minutos";
            }

            player.sendMessage("");
            player.sendMessage("§c§l[!] VOCÊ ESTÁ SILENCIADO!");
            player.sendMessage("§cMotivo: §f" + reason);
            player.sendMessage("§cExpira em: §f" + timeString);
            player.sendMessage("");
            return;
        }

        // 2. LOGAR MENSAGEM NO MYSQL
        logChatToDatabase(player.getName(), message);

        // 3. FORMATAÇÃO DE CHAT
        SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(player.getUniqueId());
        // Se o player ainda não carregou, evita erro
        if (sp == null) return;

        String rank = sp.getRankId().toLowerCase();
        String prefix = getPrefix(rank);
        String nameColor = getNameColor(rank);

        // Formatação
        String format = prefix + nameColor + "%1$s§f: %2$s";

        if (player.hasPermission("redesplit.chat.color")) {
            event.setMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        event.setFormat(format);
    }

    private void logChatToDatabase(String playerName, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO rs_chat_logs (player_name, message) VALUES (?, ?)");
                ps.setString(1, playerName);
                ps.setString(2, message);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private String getPrefix(String rank) {
        switch (rank) {
            case "master": return "§6[MASTER] ";
            case "administrador": return "§c[ADMIN] ";
            case "moderador": return "§2[MOD] ";
            case "ajudante": return "§e[HELPER] ";
            case "elite": return "§3[ELITE] ";
            case "mvp+": return "§e[MVP§b+§e] ";
            case "mvp": return "§e[MVP] ";
            case "vip+": return "§a[VIP§b+§a] ";
            case "vip": return "§a[VIP] ";
            default: return "§7";
        }
    }

    private String getNameColor(String rank) {
        switch (rank) {
            case "master": return "§6";
            case "administrador": return "§c";
            case "moderador": return "§2";
            case "ajudante": return "§e";
            case "elite": return "§3";
            case "mvp+": case "mvp": return "§e";
            case "vip+": case "vip": return "§a";
            default: return "§7";
        }
    }
}