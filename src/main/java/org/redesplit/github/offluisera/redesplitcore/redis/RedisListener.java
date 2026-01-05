package org.redesplit.github.offluisera.redesplitcore.redis;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.managers.MuteManager; // Importante: Importando o Manager
import redis.clients.jedis.JedisPubSub;
import java.util.Date;

public class RedisListener extends JedisPubSub {

    @Override
    public void onMessage(String channel, String message) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> processMessage(message));
    }

    private void processMessage(String message) {
        try {
            String[] parts = message.split(";", 3);
            if (parts.length < 2) return;

            String action = parts[0];
            String nick = parts[1];
            String rawData = parts.length > 2 ? parts[2] : "";

            String durationStr = "";
            String reason = "Sem motivo";

            // Lógica para separar tempo e motivo (usado em punições)
            if (rawData.contains("|")) {
                String[] dataParts = rawData.split("\\|", 2);
                durationStr = dataParts[0];
                reason = dataParts.length > 1 ? dataParts[1] : "Sem motivo";
            } else {
                reason = rawData;
            }

            Player target = Bukkit.getPlayer(nick);
            long minutes = 0;
            try { minutes = Long.parseLong(durationStr); } catch (Exception ignored) {}

            // --- PUNIÇÕES ---

            if (action.equalsIgnoreCase("MUTE")) {
                // 1. Calcula o tempo de expiração em Milissegundos
                long expiryTime;
                if (minutes <= 0) {
                    expiryTime = 4102444800000L; // Data distante (Ano 2100) para permanente
                } else {
                    expiryTime = System.currentTimeMillis() + (minutes * 60 * 1000);
                }

                // 2. ATUALIZA A MEMÓRIA (MuteManager)
                // Isso faz o ChatListener bloquear o jogador imediatamente, sem precisar salvar no banco de novo
                MuteManager.setMute(nick, expiryTime, reason);

                // 3. Avisa o Jogador
                if (target != null) {
                    target.sendMessage("");
                    target.sendMessage("§c§l[!] VOCÊ FOI SILENCIADO!");
                    target.sendMessage("§eMotivo: §f" + reason);
                    target.sendMessage("§eDuração: §f" + (minutes <= 0 ? "Permanente" : minutes + " minutos"));
                    target.sendMessage("");
                    playSound(target);
                }
                log("Mute aplicado via Redis em " + nick);
            }

            else if (action.equalsIgnoreCase("UNMUTE")) {
                // Remove da memória para liberar o chat na hora
                MuteManager.unmute(nick);

                // Opcional: Executa comando para limpar registros antigos de outros plugins se houver
                execute("unmute " + nick);

                if (target != null) {
                    target.sendMessage("§a§l[!] §aVocê foi desmutado via Painel.");
                    playSound(target);
                }
            }

            else if (action.equalsIgnoreCase("BAN")) {
                Date expires = (minutes > 0) ? new Date(System.currentTimeMillis() + (minutes * 60 * 1000)) : null;
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(nick, reason, expires, "Painel Web");

                if (target != null) {
                    target.kickPlayer("§cVocê foi banido!\n\n§eMotivo: §f" + reason);
                }
                Bukkit.broadcastMessage("§cO jogador " + nick + " foi banido via Painel.");
            }

            else if (action.equalsIgnoreCase("KICK")) {
                if (target != null) {
                    target.kickPlayer("§cVocê foi expulso!\n\n§eMotivo: §f" + reason);
                }
            }

            else if (action.equalsIgnoreCase("UNBAN")) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(nick);
            }

            // --- SISTEMA DE TICKETS ---

            else if (action.equalsIgnoreCase("TICKET_OPEN")) {
                // Avisa toda a Staff online
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("redesplit.ticket")) {
                        p.sendMessage("");
                        p.sendMessage("§a§l[TICKET] §aUm novo ticket foi aberto por §f" + nick + "§a!");
                        p.sendMessage("§aVerifique no painel: §fhttps://redesplit.com.br/panel/tickets.php");
                        p.sendMessage("");
                        playSound(p);
                    }
                }
                log("Novo ticket aberto por " + nick);
            }

            else if (action.equalsIgnoreCase("TICKET_REPLY")) {
                // Avisa o jogador que o ticket dele foi respondido
                if (target != null && target.isOnline()) {
                    target.sendMessage("");
                    target.sendMessage("§e§l[SUPORTE] §eSeu ticket foi respondido pela equipe!");
                    target.sendMessage("§eVerifique no painel: §fhttps://redesplit.com.br/panel/tickets.php");
                    target.sendMessage("");
                    playSound(target);
                }
            }

            // --- SISTEMA DE BROADCAST (NOVO) ---

            else if (action.equalsIgnoreCase("BROADCAST")) {
                // Formato recebido: TIPO|Mensagem
                String type = "INFO";
                String text = rawData;

                if (rawData.contains("|")) {
                    String[] partsCast = rawData.split("\\|", 2);
                    type = partsCast[0];
                    text = partsCast.length > 1 ? partsCast[1] : "";
                }

                // Configuração visual baseada no tipo
                String prefix = "";
                String titleColor = "";
                String subTitle = text.replace("&", "§");
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
                    default: // INFO
                        prefix = "§a§l[INFO] §a";
                        titleColor = "§a§lINFORMAÇÃO";
                        sound = getSound("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
                        break;
                }

                // Envia para todos
                String finalMsg = prefix + subTitle;
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(finalMsg);
                Bukkit.broadcastMessage("");

                // Envia Title e Som para quem está online
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Title (Título Grande, Subtítulo menor)
                    // sendTitle(titulo, subtitulo, fadeIn, stay, fadeOut)
                    p.sendTitle(titleColor, "§f" + subTitle, 10, 70, 20);

                    if (sound != null) {
                        p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                    }
                }

                log("Broadcast enviado por " + nick + ": " + text);
            }

            // --- CHAT DA STAFF (Web <-> Jogo) ---

            else if (action.equalsIgnoreCase("STAFF_CHAT")) {
                String chatMsg = rawData;
                // rawData já é a mensagem pura "Ola equipe"

                // Formatação: [SC] (Web) Admin: Mensagem
                String prefix = "§e[SC] ";
                String sourceDisplay = "§7(Game) "; // Padrão

                // Se quisermos diferenciar Web de Game, poderíamos mandar no pacote,
                // mas vamos assumir que se o nick for "AdminWeb" ou similar, a gente sabe.
                // Ou podemos simplificar:

                String finalFmt = "§e[SC] §7" + nick + "§f: " + chatMsg;

                // Envia para todos os Staffs online
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("redesplit.sc")) {
                        p.sendMessage(finalFmt);
                    }
                }
                // Opcional: Log no console
                // log("[SC] " + nick + ": " + chatMsg);
            }

            // --- CONSOLE REMOTO (RCON) ---

            else if (action.equalsIgnoreCase("CONSOLE")) {
                // Formato recebido: CONSOLE;Admin;Comando
                // "reason" aqui conterá o comando, pois pegamos do "parts[2]" (rawData)
                String commandToRun = rawData;
                String adminName = nick; // O PHP manda o nome do admin na posição do nick

                // Log de Segurança no Console Real
                RedeSplitCore.getInstance().getLogger().warning("§c[WebRCON] Admin " + adminName + " executou: /" + commandToRun);

                // Executa o comando na Thread Principal
                Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
                });

                // (Opcional) Avisar no StaffChat que alguém usou o console
                // enviarAvisoStaff("§7[Console] §c" + adminName + " executou /" + commandToRun);
            }


            // --- SISTEMA DE ENQUETE ---
            else if (action.equalsIgnoreCase("POLL_START")) {
                // Formato: POLL_START;ID_SQL;Pergunta|Opcao1|Opcao2
                try {
                    String[] data = rawData.split("\\|", 2); // Separa Pergunta das Opções
                    String question = data[0];
                    String[] optionsArray = data[1].split("\\|");

                    int sqlId = Integer.parseInt(nick); // Usamos o campo 'nick' para passar o ID do SQL

                    java.util.List<String> optionsList = java.util.Arrays.asList(optionsArray);

                    org.redesplit.github.offluisera.redesplitcore.managers.PollManager.startPoll(sqlId, question, optionsList);

                } catch (Exception e) {
                    RedeSplitCore.getInstance().getLogger().severe("Erro ao iniciar enquete: " + e.getMessage());
                }
            }
            else if (action.equalsIgnoreCase("POLL_STOP")) {
                org.redesplit.github.offluisera.redesplitcore.managers.PollManager.stopPoll();
            }

            // --- MOTD DINÂMICO ---
            else if (action.equalsIgnoreCase("UPDATE_MOTD")) {
                // Formato: UPDATE_MOTD;Admin;Linha1<br>Linha2
                String[] lines = rawData.split("<br>");
                if (lines.length >= 2) {
                    org.redesplit.github.offluisera.redesplitcore.managers.MotdManager.setMotd(lines[0], lines[1]);
                    log("MOTD atualizado via Web!");
                }
            }


            // --- EXECUÇÃO DE COMANDOS (CONSOLE) ---
            else if (action.equalsIgnoreCase("EXECUTE_CONSOLE")) {
                // Formato: EXECUTE_CONSOLE;comando aqui
                // Exemplo: EXECUTE_CONSOLE;unban Jogador
                if (rawData.length() > 0) {
                    String command = rawData; // O resto da mensagem é o comando

                    // Roda na Thread Principal (Obrigatório para comandos Bukkit)
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        RedeSplitCore.getInstance().getLogger().info("[Redis] Executando comando remoto: " + command);
                    });
                }
            }






        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void execute(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("  ", " ").trim());
    }

    private void log(String msg) {
        RedeSplitCore.getInstance().getLogger().info("§a[Redis] " + msg);
    }

    // Método auxiliar de som (funciona na 1.8 e versões novas)
    private void playSound(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf("BLOCK_NOTE_BLOCK_PLING"), 1f, 1.5f);
        } catch (IllegalArgumentException e) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf("NOTE_PLING"), 1f, 1.5f);
            } catch (Exception ignored) {}
        }
    }
    // Método auxiliar para pegar som compatível com qualquer versão
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