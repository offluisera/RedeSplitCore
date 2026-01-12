package org.redesplit.github.offluisera.redesplitcore.system;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.utils.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Sistema de Recuperação de Senha via Discord
 * Versão 3.0 - Com confirmação em duas etapas
 */
public class PasswordRecovery {

    private final RedeSplitCore plugin;

    // Códigos de recuperação pendentes aguardando confirmação
    private final Map<UUID, PendingRecovery> pendingRecoveries = new HashMap<>();

    // Cooldown para evitar spam (5 minutos)
    private final long COOLDOWN_MS = 300000L;

    public PasswordRecovery(RedeSplitCore plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Solicita recuperação de senha (PASSO 1)
     */
    public void requestRecovery(Player player) {
        UUID uuid = player.getUniqueId();

        // Verifica se tem Discord vinculado
        if (!RedeSplitCore.getInstance().getDiscordLinkManager().isLinked(uuid)) {
            player.sendMessage("");
            player.sendMessage("§c§l❌ DISCORD NÃO VINCULADO!");
            player.sendMessage("");
            player.sendMessage("§cPara recuperar sua senha, você precisa");
            player.sendMessage("§cvincular sua conta ao Discord primeiro.");
            player.sendMessage("");
            player.sendMessage("§e§lComo vincular:");
            player.sendMessage("§71. Use o comando: §f/vinculardc");
            player.sendMessage("§72. Copie o código gerado");
            player.sendMessage("§73. Abra o Discord e envie DM para o bot");
            player.sendMessage("§74. Use: §f/vinculardc <codigo>");
            player.sendMessage("");
            return;
        }

        // Verifica cooldown
        PendingRecovery existing = pendingRecoveries.get(uuid);
        if (existing != null && !existing.isExpired()) {
            long remaining = (existing.expiresAt - System.currentTimeMillis()) / 1000;
            player.sendMessage("§c§lERRO: §cVocê já solicitou recuperação!");
            player.sendMessage("§eAguarde §f" + remaining + " segundos §epara tentar novamente.");
            return;
        }

        // Gera código de 6 dígitos
        String code = generateCode();

        // Salva na memória (aguardando confirmação)
        PendingRecovery recovery = new PendingRecovery(code, System.currentTimeMillis() + COOLDOWN_MS);
        pendingRecoveries.put(uuid, recovery);

        // Mensagem de instruções
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("       §e§lRECUPERAÇÃO DE SENHA");
        player.sendMessage("");
        player.sendMessage("     §fPara recuperar sua senha, siga:");
        player.sendMessage("");
        player.sendMessage("     §71. Confirme a solicitação:");
        player.sendMessage("        §e/recovery confirmar");
        player.sendMessage("");
        player.sendMessage("     §72. Aguarde o código chegar no Discord");
        player.sendMessage("");
        player.sendMessage("     §73. Use o código aqui:");
        player.sendMessage("        §e/recovery <codigo> <nova_senha>");
        player.sendMessage("");
        player.sendMessage("     §c§l⚠ §cVocê tem 5 minutos para confirmar!");
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        plugin.getLogger().info("§e[Auth] " + player.getName() + " solicitou recuperação de senha.");
    }

    /**
     * Confirma e envia o código via Discord (PASSO 2)
     */
    public void confirmRecovery(Player player) {
        UUID uuid = player.getUniqueId();

        PendingRecovery recovery = pendingRecoveries.get(uuid);
        if (recovery == null) {
            player.sendMessage("§c§lERRO: §cVocê não solicitou recuperação!");
            player.sendMessage("§eUse: §f/recovery request");
            return;
        }

        if (recovery.isExpired()) {
            pendingRecoveries.remove(uuid);
            player.sendMessage("§c§lERRO: §cSua solicitação expirou!");
            player.sendMessage("§eSolicite novamente: §f/recovery request");
            return;
        }

        String discordId = RedeSplitCore.getInstance().getDiscordLinkManager().getDiscordId(uuid);
        if (discordId == null) {
            player.sendMessage("§c§lERRO: §cDiscord não vinculado!");
            return;
        }

        // Envia código para o banco para o bot Discord processar
        String code = recovery.code;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_discord_recovery_queue (discord_id, player_name, recovery_code) " +
                                "VALUES (?, ?, ?)"
                );
                ps.setString(1, discordId);
                ps.setString(2, player.getName());
                ps.setString(3, code);
                ps.executeUpdate();

                plugin.getLogger().info("§a[Auth] Código de recuperação enviado para Discord de " + player.getName());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        player.sendMessage("");
        player.sendMessage("§a§l✔ CÓDIGO ENVIADO!");
        player.sendMessage("§aUm código de recuperação foi enviado para seu Discord.");
        player.sendMessage("");
        player.sendMessage("§eVerifique suas mensagens privadas do bot!");
        player.sendMessage("");
        player.sendMessage("§7Use: §f/recovery <codigo> <nova_senha>");
        player.sendMessage("§7Exemplo: §f/recovery 123456 MinhaSenh@123");
        player.sendMessage("");
    }

    /**
     * Completa a recuperação com o código (PASSO 3)
     */
    public void completeRecovery(Player player, String code, String newPassword) {
        UUID uuid = player.getUniqueId();

        PendingRecovery recovery = pendingRecoveries.get(uuid);
        if (recovery == null) {
            player.sendMessage("§c§lERRO: §cVocê não solicitou recuperação!");
            player.sendMessage("§eUse: §f/recovery request");
            return;
        }

        if (recovery.isExpired()) {
            pendingRecoveries.remove(uuid);
            player.sendMessage("§c§lERRO: §cCódigo expirado!");
            player.sendMessage("§eSolicite um novo código.");
            return;
        }

        if (!recovery.code.equals(code)) {
            player.sendMessage("§c§lERRO: §cCódigo incorreto!");
            return;
        }

        if (!PasswordHasher.isStrongPassword(newPassword)) {
            player.sendMessage("§c§lERRO: §cSenha fraca!");
            player.sendMessage("§eUse pelo menos 8 caracteres com letras, números e símbolos.");
            return;
        }

        // Remove da memória
        pendingRecoveries.remove(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Atualiza a senha
                String hashedPassword = PasswordHasher.hash(newPassword);
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rs_auth_accounts SET password_hash = ? WHERE uuid = ?"
                );
                ps.setString(1, hashedPassword);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§a§l✔ SENHA ALTERADA!");
                    player.sendMessage("§aSua senha foi redefinida com sucesso.");
                    player.sendMessage("§eGuarde-a em um local seguro!");
                    player.sendMessage("");
                    player.sendMessage("§7Faça login novamente: §f/login <nova_senha>");
                    player.sendMessage("");
                });

            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage("§c§lERRO: §cFalha ao alterar senha.");
            }
        });
    }

    /**
     * Gera código de 6 dígitos
     */
    private String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    /**
     * Limpa códigos expirados a cada 10 minutos
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            pendingRecoveries.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 12000L, 12000L); // 10 minutos
    }

    /**
     * Classe interna para armazenar códigos temporários
     */
    private static class PendingRecovery {
        final String code;
        final long expiresAt;

        PendingRecovery(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}