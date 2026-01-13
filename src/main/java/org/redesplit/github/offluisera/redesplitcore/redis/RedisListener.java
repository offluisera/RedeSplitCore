package org.redesplit.github.offluisera.redesplitcore.redis;

import com.cryptomorin.xseries.XSound;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.managers.MuteManager;
import org.redesplit.github.offluisera.redesplitcore.managers.XPManager;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import redis.clients.jedis.JedisPubSub;
import java.util.Date;

public class RedisListener extends JedisPubSub {

    @Override
    public void onMessage(String channel, String message) {
        // Log da mensagem recebida (debug)
        RedeSplitCore.getInstance().getLogger().info("§e[Redis] << " + message);

        // Processa na thread principal do Bukkit
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> processMessage(message));
    }

    private void processMessage(String message) {
        try {
            // ⭐ MUDANÇA CRÍTICA: Agora usa | como separador (padrão do painel)
            String[] parts = message.split("\\|");
            if (parts.length < 1) return;

            String action = parts[0];

            // ===== SISTEMA DE ENQUETES =====
            if (action.equalsIgnoreCase("POLL_START")) {
                handlePollStart(parts);
            } else if (action.equalsIgnoreCase("POLL_STOP")) {
                handlePollStop(parts);
            }

            // ===== SISTEMA DE RANKS =====
            else if (action.equalsIgnoreCase("RANK_UPDATE")) {
                handleRankUpdate(parts);
            }

            // ===== SISTEMA DE PERMISSÕES =====
            else if (action.equalsIgnoreCase("PERM_UPDATE")) {
                handlePermUpdate(parts);
            }

            else if (action.equalsIgnoreCase("XP_UPDATE")) {
                handleXPUpdate(parts);
            }

            // ===== PUNIÇÕES (mantém lógica antiga com ; para compatibilidade) =====
            else if (action.equalsIgnoreCase("MUTE") || action.equalsIgnoreCase("BAN") ||
                    action.equalsIgnoreCase("KICK") || action.equalsIgnoreCase("UNMUTE") ||
                    action.equalsIgnoreCase("UNBAN")) {
                handlePunishment(message); // Chama método separado para punições
            }

            // ===== TICKETS =====
            else if (action.equalsIgnoreCase("TICKET_OPEN") || action.equalsIgnoreCase("TICKET_REPLY")) {
                handleTicket(parts);
            }

            // ===== BROADCAST =====
            else if (action.equalsIgnoreCase("BROADCAST")) {
                handleBroadcast(parts);
            }

            // ===== STAFF CHAT =====
            else if (action.equalsIgnoreCase("STAFF_CHAT")) {
                handleStaffChat(parts);
            }

            // ===== CONSOLE =====
            else if (action.equalsIgnoreCase("CONSOLE") || action.equalsIgnoreCase("EXECUTE_CONSOLE")) {
                handleConsole(parts);
            }

            // ===== MOTD =====
            else if (action.equalsIgnoreCase("UPDATE_MOTD")) {
                handleMotd(parts);
            } else {
                log("§cComando desconhecido: " + action);
            }

        } catch (Exception e) {
            RedeSplitCore.getInstance().getLogger().severe("§c[Redis] Erro ao processar: " + message);
            e.printStackTrace();
        }
    }

    // ========================================
    // HANDLERS ESPECÍFICOS
    // ========================================

    /**
     * Formato: POLL_START|{pollId}|{question}|{option1}|{option2}
     * Exemplo: POLL_START|7|Qual servidor lançar?|Bedwars|SkyWars
     */
    private void handlePollStart(String[] parts) {
        if (parts.length < 5) {
            log("§cPOLL_START com formato inválido (esperado 5 partes, recebido " + parts.length + ")");
            return;
        }

        try {
            int pollId = Integer.parseInt(parts[1]);
            String question = parts[2];
            String option1 = parts[3];
            String option2 = parts[4];

            java.util.List<String> options = java.util.Arrays.asList(option1, option2);

            org.redesplit.github.offluisera.redesplitcore.managers.PollManager.startPoll(pollId, question, options);

            log("§aEnquete iniciada: #" + pollId + " - " + question);

        } catch (NumberFormatException e) {
            log("§cErro: ID da enquete inválido -> " + parts[1]);
        } catch (Exception e) {
            log("§cErro ao iniciar enquete: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Formato: POLL_STOP|{admin}
     * Exemplo: POLL_STOP|offluisera
     */
    private void handlePollStop(String[] parts) {
        String admin = parts.length > 1 ? parts[1] : "Admin";

        org.redesplit.github.offluisera.redesplitcore.managers.PollManager.stopPoll();

        log("§aEnquete encerrada por: " + admin);
    }

    /**
     * Formato: RANK_UPDATE|{playerName}|{newRank}
     * Exemplo: RANK_UPDATE|offluisera|vip
     */
    private void handleRankUpdate(String[] parts) {
        if (parts.length < 3) return;

        String playerName = parts[1];
        String newRank = parts[2];

        // TODO: Implementar lógica de atualização de rank
        // Se usa LuckPerms, recarregar permissões do jogador
        // Se usa sistema próprio, atualizar cache

        log("§eRank atualizado: " + playerName + " -> " + newRank);

        Player target = Bukkit.getPlayer(playerName);
        if (target != null) {
            target.sendMessage("§a§l[!] §aSeu cargo foi atualizado para: §e" + newRank.toUpperCase());
            playSound(target);
        }
    }

    /**
     * Formato: PERM_UPDATE|ALL|{rankId}
     * Exemplo: PERM_UPDATE|ALL|master
     */
    private void handlePermUpdate(String[] parts) {
        if (parts.length < 3) return;

        String scope = parts[1];
        String rankId = parts[2];

        // TODO: Recarregar permissões do rank
        log("§ePermissões atualizadas para rank: " + rankId);
    }

    /**
     * Formato ANTIGO (mantido para compatibilidade): MUTE;player;duration|reason
     * Exemplo: MUTE;offluisera;60|Flood
     */
    private void handlePunishment(String message) {
        String[] parts = message.split(";", 3);
        if (parts.length < 2) return;

        String action = parts[0];
        String nick = parts[1];
        String rawData = parts.length > 2 ? parts[2] : "";

        String durationStr = "";
        String reason = "Sem motivo";

        if (rawData.contains("|")) {
            String[] dataParts = rawData.split("\\|", 2);
            durationStr = dataParts[0];
            reason = dataParts.length > 1 ? dataParts[1] : "Sem motivo";
        } else {
            reason = rawData;
        }

        Player target = Bukkit.getPlayer(nick);
        long minutes = 0;
        try {
            minutes = Long.parseLong(durationStr);
        } catch (Exception ignored) {
        }

        if (action.equalsIgnoreCase("MUTE")) {
            long expiryTime = (minutes <= 0) ? 4102444800000L : System.currentTimeMillis() + (minutes * 60 * 1000);
            MuteManager.setMute(nick, expiryTime, reason);

            if (target != null) {
                target.sendMessage("");
                target.sendMessage("§c§l[!] VOCÊ FOI SILENCIADO!");
                target.sendMessage("§eMotivo: §f" + reason);
                target.sendMessage("§eDuração: §f" + (minutes <= 0 ? "Permanente" : minutes + " minutos"));
                target.sendMessage("");
                playSound(target);
            }
            log("Mute aplicado em " + nick);
        } else if (action.equalsIgnoreCase("UNMUTE")) {
            MuteManager.unmute(nick);
            if (target != null) {
                target.sendMessage("§a§l[!] §aVocê foi desmutado via Painel.");
                playSound(target);
            }
        } else if (action.equalsIgnoreCase("BAN")) {
            Date expires = (minutes > 0) ? new Date(System.currentTimeMillis() + (minutes * 60 * 1000)) : null;
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(nick, reason, expires, "Painel Web");

            if (target != null) {
                target.kickPlayer("§cVocê foi banido!\n\n§eMotivo: §f" + reason);
            }
            Bukkit.broadcastMessage("§cO jogador " + nick + " foi banido via Painel.");
        } else if (action.equalsIgnoreCase("KICK")) {
            if (target != null) {
                target.kickPlayer("§cVocê foi expulso!\n\n§eMotivo: §f" + reason);
            }
        } else if (action.equalsIgnoreCase("UNBAN")) {
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(nick);
        }
    }

    /**
     * Formato: TICKET_OPEN|{player}|{message}
     */
    private void handleTicket(String[] parts) {
        if (parts.length < 2) return;

        String action = parts[0];
        String nick = parts[1];

        if (action.equalsIgnoreCase("TICKET_OPEN")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("redesplit.ticket")) {
                    p.sendMessage("");
                    p.sendMessage("§a§l[TICKET] §aUm novo ticket foi aberto por §f" + nick + "§a!");
                    p.sendMessage("§aVerifique no painel");
                    p.sendMessage("");
                    playSound(p);
                }
            }
        } else if (action.equalsIgnoreCase("TICKET_REPLY")) {
            Player target = Bukkit.getPlayer(nick);
            if (target != null) {
                target.sendMessage("");
                target.sendMessage("§e§l[SUPORTE] §eSeu ticket foi respondido!");
                target.sendMessage("§eVerifique no painel");
                target.sendMessage("");
                playSound(target);
            }
        }
    }

    /**
     * Formato: BROADCAST|{type}|{message}
     * Exemplo: BROADCAST|EVENTO|Evento de Drop iniciado!
     */
    private void handleBroadcast(String[] parts) {
        if (parts.length < 3) return;

        String type = parts[1];
        String text = parts[2].replace("&", "§");

        String prefix = "";
        String titleColor = "";
        Sound sound = null;

        switch (type.toUpperCase()) {
            case "AVISO":
                prefix = "§e§l[AVISO] §e";
                titleColor = "§e§lAVISO";
                sound = getSound("BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");
                break;
            case "EVENTO":
                prefix = "§b§l[EVENTO] §b";
                titleColor = "§b§lEVENTO";
                sound = getSound("ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
                break;
            case "MANUTENCAO":
                prefix = "§c§l[MANUTENÇÃO] §c";
                titleColor = "§c§lATENÇÃO";
                sound = getSound("BLOCK_ANVIL_LAND", "ANVIL_LAND");
                break;
            default:
                prefix = "§a§l[INFO] §a";
                titleColor = "§a§lINFORMAÇÃO";
                sound = getSound("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
                break;
        }

        String finalMsg = prefix + text;
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(finalMsg);
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(titleColor, "§f" + text, 10, 70, 20);
            if (sound != null) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Formato: STAFF_CHAT|{nick}|{message}
     */
    private void handleStaffChat(String[] parts) {
        if (parts.length < 3) return;

        String nick = parts[1];
        String chatMsg = parts[2];

        String finalFmt = "§e[SC] §7" + nick + "§f: " + chatMsg;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("redesplit.sc")) {
                p.sendMessage(finalFmt);
            }
        }
    }

    /**
     * Formato: CONSOLE|{admin}|{command}
     */
    private void handleConsole(String[] parts) {
        if (parts.length < 3) return;

        String admin = parts[1];
        String command = parts[2];

        log("§c[WebRCON] " + admin + " executou: /" + command);

        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }

    /**
     * Formato: UPDATE_MOTD|{admin}|{line1}|{line2}
     */
    private void handleMotd(String[] parts) {
        if (parts.length < 4) return;

        String line1 = parts[2];
        String line2 = parts[3];

        try {
            org.redesplit.github.offluisera.redesplitcore.managers.MotdManager.setMotd(line1, line2);
            log("MOTD atualizado via Web!");
        } catch (Exception e) {
            log("§cErro ao atualizar MOTD: " + e.getMessage());
        }
    }

    // Adicione este método na classe RedisListener.java ou RedisSubscriber.java

    /**
     * Handler para notificações de XP vindas do painel web
     * Formato: XP_UPDATE|PLAYER|ACTION|AMOUNT|NEW_XP|NEW_LEVEL|REASON
     * Exemplo: XP_UPDATE|offluisera|ADD|1000|5500|4|Recompensa de evento
     */
    private void handleXPUpdate(String[] parts) {
        if (parts.length < 7) {
            log("§c[Redis] XP_UPDATE com formato inválido (esperado 7 partes, recebido " + parts.length + ")");
            return;
        }

        try {
            String playerName = parts[1];
            String action = parts[2];
            int amount = Integer.parseInt(parts[3]);
            long newXP = Long.parseLong(parts[4]);
            int newLevel = Integer.parseInt(parts[5]);
            String reason = parts[6];

            Player target = Bukkit.getPlayer(playerName);

            if (target != null && target.isOnline()) {
                // ⭐ ATUALIZA XP DIRETO DO BANCO (Garante sincronia)
                RedeSplitCore.getInstance().getPlayerManager().refreshXP(target.getUniqueId());

                // Aguarda um pouco para garantir que os dados foram carregados
                Bukkit.getScheduler().runTaskLater(RedeSplitCore.getInstance(), () -> {
                    SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(target.getUniqueId());
                    if (sp == null) return;

                    // Mensagem personalizada baseada na ação
                    String actionIcon = "";
                    String actionColor = "";
                    String actionText = "";
                    String amountFormatted = String.format("%,d", Math.abs(amount));

                    switch (action.toUpperCase()) {
                        case "ADD":
                            actionIcon = "§a§l↑";
                            actionColor = "§a";
                            actionText = "§aVocê recebeu §f" + amountFormatted + " XP§a!";

                            // Som usando XSound (compatível com todas as versões)
                            XSound.ENTITY_PLAYER_LEVELUP.play(target, 1.0f, 1.0f);

                            // Efeito de partículas (compatível com 1.8+)
                            try {
                                target.getWorld().spawnParticle(
                                        org.bukkit.Particle.VILLAGER_HAPPY,
                                        target.getLocation().add(0, 1, 0),
                                        20, 0.5, 0.5, 0.5, 0.1
                                );
                            } catch (Exception e) {
                                // Fallback para 1.8 usando deprecated API
                                target.playEffect(target.getLocation(), org.bukkit.Effect.HAPPY_VILLAGER, null);
                            }
                            break;

                        case "REMOVE":
                            actionIcon = "§c§l↓";
                            actionColor = "§c";
                            actionText = "§cForam removidos §f" + amountFormatted + " XP §cde você!";
                            XSound.ENTITY_ITEM_BREAK.play(target, 1.0f, 1.0f);
                            break;

                        case "SET":
                            actionIcon = "§e§l⚙";
                            actionColor = "§e";
                            actionText = "§eSeu XP foi definido para §f" + amountFormatted + "§e!";
                            XSound.BLOCK_NOTE_BLOCK_PLING.play(target, 1.0f, 1.5f);
                            break;
                    }

                    // Envia mensagem ao jogador
                    target.sendMessage("");
                    target.sendMessage("§6§l✦ ════════════════════════════ ✦");
                    target.sendMessage("");
                    target.sendMessage("  " + actionIcon + " §e§lSISTEMA DE XP");
                    target.sendMessage("");
                    target.sendMessage("  " + actionText);
                    target.sendMessage("  §7Motivo: §f" + reason);
                    target.sendMessage("");
                    target.sendMessage("  §eNível Atual: " + XPManager.getLevelBadge(newLevel));
                    target.sendMessage("  §eXP Total: §f" + String.format("%,d", newXP));
                    target.sendMessage("");
                    target.sendMessage("§6§l✦ ════════════════════════════ ✦");
                    target.sendMessage("");

                    // Title e Subtitle
                    String titleText = actionColor + "XP " + action.toUpperCase();
                    String subtitleText = actionColor + (action.equals("REMOVE") ? "-" : "+") + amountFormatted + " XP";

                    try {
                        target.sendTitle(titleText, subtitleText, 10, 40, 10);
                    } catch (Exception e) {
                        // Fallback para versões antigas que não suportam sendTitle
                        target.sendMessage(titleText + " " + subtitleText);
                    }

                    log("§a[XP] Atualização aplicada para " + playerName + ": " + action + " " + amount + " XP");

                }, 20L); // Aguarda 1 segundo para garantir que refreshXP() completou

            } else {
                log("§e[XP] Jogador " + playerName + " não está online, XP será atualizado no próximo login");
            }

        } catch (NumberFormatException e) {
            log("§c[Redis] Erro ao processar valores numéricos do XP_UPDATE: " + e.getMessage());
        } catch (Exception e) {
            log("§c[Redis] Erro ao processar XP_UPDATE: " + e.getMessage());
            e.printStackTrace();
        }
    }



    // ========================================
    // MÉTODOS AUXILIARES
    // ========================================

    private void log(String msg) {
        RedeSplitCore.getInstance().getLogger().info("§a[Redis] " + msg);
    }

    private void playSound(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf("BLOCK_NOTE_BLOCK_PLING"), 1f, 1.5f);
        } catch (IllegalArgumentException e) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf("NOTE_PLING"), 1f, 1.5f);
            } catch (Exception ignored) {}
        }
    }

    private Sound getSound(String modern, String legacy) {
        try {
            return Sound.valueOf(modern);
        } catch (IllegalArgumentException e) {
            try {
                return Sound.valueOf(legacy);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}