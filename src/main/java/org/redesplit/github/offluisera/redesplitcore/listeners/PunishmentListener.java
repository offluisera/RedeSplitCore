package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PunishmentListener implements Listener {

    // 1. BLOQUEAR CHAT (MUTE) COM MENSAGEM CUSTOMIZADA
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(e.getPlayer().getUniqueId());

        if (sp != null && sp.isMuted()) {
            e.setCancelled(true);

            long expires = sp.getMuteExpires();
            // Verifica se é permanente (maior que 1 ano a partir de agora)
            boolean isPerm = expires > (System.currentTimeMillis() + 31536000000L);

            // Formata a data ou texto
            String duracao = isPerm ? "Permanente" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(expires));

            String motivo = sp.getMuteReason() != null ? sp.getMuteReason() : "Não informado";
            String autor = sp.getMuteOperator() != null ? sp.getMuteOperator() : "Console";

            // Mensagem formatada como você pediu
            e.getPlayer().sendMessage("");
            e.getPlayer().sendMessage("§c§l(!) §cVocê está mutado.");
            e.getPlayer().sendMessage("§cMotivo: §f" + motivo);
            e.getPlayer().sendMessage("§cDuração: §f" + duracao);
            e.getPlayer().sendMessage("§cAplicado por: §f" + autor);
            e.getPlayer().sendMessage("");
            e.getPlayer().sendMessage("§eSe acha que foi um erro, faça um appeal no site!");
            e.getPlayer().sendMessage("§ewww.redesplit.com.br");
            e.getPlayer().sendMessage("");
        }
    }

    // 2. BLOQUEAR ENTRADA (BAN) - Mantém igual
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM rs_punishments WHERE player_name = ? AND type = 'BAN' AND active = 1 AND expires > NOW() ORDER BY id DESC LIMIT 1"
            );
            ps.setString(1, e.getName());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String motivo = rs.getString("reason");
                String autor = rs.getString("operator");
                java.sql.Timestamp expires = rs.getTimestamp("expires");

                boolean isPerm = expires.getTime() > (System.currentTimeMillis() + 31536000000L);
                String tempo = isPerm ? "§4PERMANENTE" : "§c" + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(expires);

                String kickMsg = "\n§c§lREDE SPLIT - VOCÊ ESTÁ BANIDO\n\n" +
                        "§cMotivo: §f" + motivo + "\n" +
                        "§cAutor: §f" + autor + "\n" +
                        "§cExpira em: " + tempo + "\n\n" +
                        "§eAcha que foi erro? Solicite appeal no site.";

                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMsg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}