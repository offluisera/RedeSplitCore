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
 * Sistema de Recuperação de Senha via Email
 * ✅ CORRIGIDO PARA JAVA 8 (sem String.repeat)
 */
public class PasswordRecovery {

    private final RedeSplitCore plugin;

    // Códigos de recuperação temporários (UUID -> Código)
    private final Map<UUID, RecoveryCode> pendingRecoveries = new HashMap<>();

    // Cooldown para evitar spam (5 minutos)
    private final long COOLDOWN_MS = 300000L;

    public PasswordRecovery(RedeSplitCore plugin) {
        this.plugin = plugin;
        createTable();
        startCleanupTask();
    }

    /**
     * Cria tabela de logs de recuperação
     */
    private void createTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                conn.createStatement().executeUpdate(
                        "CREATE TABLE IF NOT EXISTS rs_auth_recovery_logs (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY," +
                                "uuid VARCHAR(36) NOT NULL," +
                                "email VARCHAR(100) NOT NULL," +
                                "code VARCHAR(6) NOT NULL," +
                                "status VARCHAR(20) DEFAULT 'PENDING'," +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                "used_at TIMESTAMP NULL," +
                                "INDEX (uuid, status)" +
                                ")"
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Solicita recuperação de senha
     */
    public void requestRecovery(Player player, String email) {
        UUID uuid = player.getUniqueId();

        // Verifica cooldown
        RecoveryCode existing = pendingRecoveries.get(uuid);
        if (existing != null && !existing.isExpired()) {
            long remaining = (existing.expiresAt - System.currentTimeMillis()) / 1000;
            player.sendMessage("§c§lERRO: §cVocê já solicitou recuperação!");
            player.sendMessage("§eAguarde §f" + remaining + " segundos §epara tentar novamente.");
            return;
        }

        player.sendMessage("§e§lPROCESSANDO... §eVerificando dados...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Verifica se a conta existe e tem email
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT email FROM rs_auth_accounts WHERE uuid = ?"
                );
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    player.sendMessage("§c§lERRO: §cVocê não possui uma conta registrada!");
                    return;
                }

                String storedEmail = rs.getString("email");
                if (storedEmail == null || storedEmail.isEmpty()) {
                    player.sendMessage("§c§lERRO: §cSua conta não possui email cadastrado!");
                    player.sendMessage("§eContate um administrador para recuperar sua conta.");
                    return;
                }

                // Verifica se o email fornecido corresponde
                if (!storedEmail.equalsIgnoreCase(email)) {
                    player.sendMessage("§c§lERRO: §cO email não corresponde ao cadastrado!");
                    logFailedAttempt(uuid, email);
                    return;
                }

                // Gera código de 6 dígitos
                String code = generateCode();

                // Salva no banco
                PreparedStatement psLog = conn.prepareStatement(
                        "INSERT INTO rs_auth_recovery_logs (uuid, email, code) VALUES (?, ?, ?)"
                );
                psLog.setString(1, uuid.toString());
                psLog.setString(2, email);
                psLog.setString(3, code);
                psLog.executeUpdate();

                // Salva na memória
                RecoveryCode recovery = new RecoveryCode(code, System.currentTimeMillis() + COOLDOWN_MS);
                pendingRecoveries.put(uuid, recovery);

                // Envia email (você precisa implementar o envio real)
                sendRecoveryEmail(email, code, player.getName());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§a§l✔ CÓDIGO ENVIADO!");
                    player.sendMessage("§aUm código de recuperação foi enviado para:");
                    player.sendMessage("§f" + censorEmail(email));
                    player.sendMessage("");
                    player.sendMessage("§eUse: §f/recovery <codigo> <nova_senha>");
                    player.sendMessage("§7O código expira em 5 minutos.");
                    player.sendMessage("");
                });

            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage("§c§lERRO: §cFalha ao processar solicitação.");
            }
        });
    }

    /**
     * Completa a recuperação com o código
     */
    public void completeRecovery(Player player, String code, String newPassword) {
        UUID uuid = player.getUniqueId();

        RecoveryCode recovery = pendingRecoveries.get(uuid);
        if (recovery == null) {
            player.sendMessage("§c§lERRO: §cVocê não solicitou recuperação!");
            player.sendMessage("§eUse: §f/recovery request <email>");
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

                // Marca código como usado
                PreparedStatement psLog = conn.prepareStatement(
                        "UPDATE rs_auth_recovery_logs SET status = 'USED', used_at = NOW() " +
                                "WHERE uuid = ? AND code = ?"
                );
                psLog.setString(1, uuid.toString());
                psLog.setString(2, code);
                psLog.executeUpdate();

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
     * Censura email (mostra apenas primeiros 3 caracteres)
     * ✅ CORRIGIDO PARA JAVA 8 (sem String.repeat)
     */
    private String censorEmail(String email) {
        String[] parts = email.split("@");
        if (parts.length != 2) return email;

        String user = parts[0];
        String domain = parts[1];

        if (user.length() <= 3) return email;

        String visible = user.substring(0, 3);

        // ✅ SUBSTITUIÇÃO DO .repeat() POR LOOP
        int censorCount = user.length() - 3;
        StringBuilder censored = new StringBuilder();
        for (int i = 0; i < censorCount; i++) {
            censored.append("*");
        }

        return visible + censored.toString() + "@" + domain;
    }

    /**
     * Envia email de recuperação (MOCK - implementar com JavaMail)
     */
    private void sendRecoveryEmail(String email, String code, String playerName) {
        // TODO: Implementar com JavaMail ou serviço de email
        plugin.getLogger().info("§e[Auth] Código de recuperação para " + playerName + ": " + code);

        // Exemplo de integração com Discord Webhook
        String message = "**Recuperação de Senha**\n" +
                "Jogador: " + playerName + "\n" +
                "Email: " + email + "\n" +
                "Código: `" + code + "`";

        // Descomente se tiver o DiscordWebhook implementado:
        // DiscordWebhook.send("Sistema de Recuperação", message);
    }

    /**
     * Registra tentativa falhada
     */
    private void logFailedAttempt(UUID uuid, String email) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_auth_recovery_logs (uuid, email, code, status) " +
                                "VALUES (?, ?, 'FAILED', 'FAILED')"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, email);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Limpa códigos expirados a cada 10 minutos
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Remove códigos expirados da memória
            pendingRecoveries.entrySet().removeIf(entry -> entry.getValue().isExpired());

            // Atualiza status no banco
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rs_auth_recovery_logs SET status = 'EXPIRED' " +
                                "WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL 5 MINUTE"
                );
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 12000L, 12000L); // 10 minutos
    }

    /**
     * Classe interna para armazenar códigos temporários
     */
    private static class RecoveryCode {
        final String code;
        final long expiresAt;

        RecoveryCode(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}