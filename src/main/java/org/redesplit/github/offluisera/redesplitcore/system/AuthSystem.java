package org.redesplit.github.offluisera.redesplitcore.system;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de Autenticação RedeSplit
 * Inspirado no nLogin, mas com recursos modernos
 */
public class AuthSystem {

    private final RedeSplitCore plugin;

    // Jogadores autenticados na sessão
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();

    // Sessões ativas (UUID -> Tempo de expiração)
    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();

    // Tempo de sessão em milissegundos (30 minutos padrão)
    private final long SESSION_DURATION = 1800000L;

    // Tentativas de login falhadas (UUID -> Contador)
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final int MAX_ATTEMPTS = 3;

    public AuthSystem(RedeSplitCore plugin) {
        this.plugin = plugin;
        createTables();
        startSessionCleaner();
    }

    /**
     * Cria as tabelas necessárias no banco de dados
     */
    private void createTables() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Tabela principal de contas
                Statement st = conn.createStatement();
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS rs_auth_accounts (" +
                                "uuid VARCHAR(36) PRIMARY KEY," +
                                "username VARCHAR(16) NOT NULL," +
                                "password_hash VARCHAR(64) NOT NULL," +
                                "email VARCHAR(100) DEFAULT NULL," +
                                "last_ip VARCHAR(45)," +
                                "registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                                "premium TINYINT(1) DEFAULT 0," + // Para contas originais
                                "INDEX (username)" +
                                ")"
                );

                // Tabela de sessões ativas
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS rs_auth_sessions (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY," +
                                "uuid VARCHAR(36) NOT NULL," +
                                "ip_address VARCHAR(45) NOT NULL," +
                                "expires_at TIMESTAMP NOT NULL," +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                "INDEX (uuid, ip_address)" +
                                ")"
                );

                // Tabela de logs de autenticação
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS rs_auth_logs (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY," +
                                "uuid VARCHAR(36) NOT NULL," +
                                "username VARCHAR(16) NOT NULL," +
                                "action VARCHAR(20) NOT NULL," + // LOGIN, REGISTER, LOGOUT, FAILED_LOGIN
                                "ip_address VARCHAR(45)," +
                                "success TINYINT(1) DEFAULT 1," +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                "INDEX (uuid), INDEX (created_at)" +
                                ")"
                );

                plugin.getLogger().info("§a[Auth] Tabelas de autenticação carregadas!");

            } catch (SQLException e) {
                plugin.getLogger().severe("§c[Auth] Erro ao criar tabelas: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Verifica se o jogador está autenticado
     */
    public boolean isAuthenticated(UUID uuid) {
        return authenticatedPlayers.contains(uuid);
    }

    /**
     * Verifica se o jogador está registrado
     */
    public boolean isRegistered(UUID uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = plugin.getMySQL().getConnection();
            ps = conn.prepareStatement("SELECT uuid FROM rs_auth_accounts WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().warning("§c[Auth] Erro ao verificar registro de " + uuid + ": " + e.getMessage());
            return false;
        } finally {
            // IMPORTANTE: Fecha recursos para não vazar conexões
            try { if (rs != null) rs.close(); } catch (Exception e) {}
            try { if (ps != null) ps.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }

    /**
     * Registra um novo jogador
     */
    public boolean register(Player player, String password, String email) {
        if (password.length() < 6) {
            player.sendMessage("§c§lERRO: §cA senha deve ter no mínimo 6 caracteres!");
            return false;
        }

        String hashedPassword = hashPassword(password);
        String ip = player.getAddress().getAddress().getHostAddress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Verifica se já existe
                PreparedStatement check = conn.prepareStatement(
                        "SELECT uuid FROM rs_auth_accounts WHERE uuid = ?"
                );
                check.setString(1, player.getUniqueId().toString());
                if (check.executeQuery().next()) {
                    player.sendMessage("§c§lERRO: §cVocê já está registrado!");
                    return;
                }

                // Insere novo registro
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_auth_accounts (uuid, username, password_hash, email, last_ip) " +
                                "VALUES (?, ?, ?, ?, ?)"
                );
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, hashedPassword);
                ps.setString(4, email);
                ps.setString(5, ip);
                ps.executeUpdate();

                // Log de registro
                logAction(player.getUniqueId(), player.getName(), "REGISTER", ip, true);

                // Autentica automaticamente após registro
                Bukkit.getScheduler().runTask(plugin, () -> {
                    authenticate(player);
                    player.sendMessage("");
                    player.sendMessage("§a§l✔ REGISTRO CONCLUÍDO!");
                    player.sendMessage("§aSua conta foi criada com sucesso.");
                    if (email != null && !email.isEmpty()) {
                        player.sendMessage("§aEmail vinculado: §f" + email);
                    }
                    player.sendMessage("§eLembre-se de sua senha para acessar novamente!");
                    player.sendMessage("");
                });

            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("§c§lERRO: §cFalha ao criar sua conta. Tente novamente.");
            }
        });

        return true;
    }

    /**
     * Realiza o login do jogador
     */
    public void login(Player player, String password) {
        String ip = player.getAddress().getAddress().getHostAddress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT password_hash FROM rs_auth_accounts WHERE uuid = ?"
                );
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    player.sendMessage("§c§lERRO: §cVocê não está registrado!");
                    player.sendMessage("§eUse: §f/register <senha> [email]");
                    return;
                }

                String storedHash = rs.getString("password_hash");
                String inputHash = hashPassword(password);

                if (!storedHash.equals(inputHash)) {
                    // Senha incorreta
                    int attempts = loginAttempts.getOrDefault(player.getUniqueId(), 0) + 1;
                    loginAttempts.put(player.getUniqueId(), attempts);

                    logAction(player.getUniqueId(), player.getName(), "FAILED_LOGIN", ip, false);

                    player.sendMessage("§c§lERRO: §cSenha incorreta!");
                    player.sendMessage("§cTentativas restantes: §f" + (MAX_ATTEMPTS - attempts));

                    if (attempts >= MAX_ATTEMPTS) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.kickPlayer(
                                    "§c§lMUITAS TENTATIVAS FALHADAS\n\n" +
                                            "§cVocê excedeu o limite de tentativas de login.\n" +
                                            "§eAguarde alguns minutos e tente novamente."
                            );
                        });
                    }
                    return;
                }

                // Senha correta - autenticar
                loginAttempts.remove(player.getUniqueId());

                // Atualiza IP no banco
                PreparedStatement updateIp = conn.prepareStatement(
                        "UPDATE rs_auth_accounts SET last_ip = ? WHERE uuid = ?"
                );
                updateIp.setString(1, ip);
                updateIp.setString(2, player.getUniqueId().toString());
                updateIp.executeUpdate();

                // Cria sessão
                createSession(player.getUniqueId(), ip);

                // Log de login
                logAction(player.getUniqueId(), player.getName(), "LOGIN", ip, true);

                // Autentica na thread principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    authenticate(player);
                    player.sendMessage("");
                    player.sendMessage("§a§l✔ LOGIN REALIZADO!");
                    player.sendMessage("§aBem-vindo de volta, §f" + player.getName() + "§a!");
                    player.sendMessage("§7Última conexão de: §f" + ip);
                    player.sendMessage("");
                });

            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("§c§lERRO: §cFalha ao autenticar. Tente novamente.");
            }
        });
    }

    /**
     * Marca o jogador como autenticado
     */
    private void authenticate(Player player) {
        authenticatedPlayers.add(player.getUniqueId());
        plugin.getLogger().info("§a[Auth] " + player.getName() + " autenticado.");
    }

    /**
     * Autentica jogador via sessão (chamado pelo Listener)
     */
    public void authenticateBySession(Player player) {
        authenticatedPlayers.add(player.getUniqueId());
        plugin.getLogger().info("§a[Auth] " + player.getName() + " autenticado via sessão.");
    }

    /**
     * Remove autenticação ao sair
     */
    public void logout(Player player) {
        authenticatedPlayers.remove(player.getUniqueId());
        loginAttempts.remove(player.getUniqueId());
    }

    /**
     * Verifica se existe uma sessão ativa válida
     */
    public boolean hasValidSession(UUID uuid, String ip) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT expires_at FROM rs_auth_sessions " +
                            "WHERE uuid = ? AND ip_address = ? AND expires_at > NOW()"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cria uma nova sessão
     */
    private void createSession(UUID uuid, String ip) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Remove sessões antigas do mesmo IP
                PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM rs_auth_sessions WHERE uuid = ? AND ip_address = ?"
                );
                delete.setString(1, uuid.toString());
                delete.setString(2, ip);
                delete.executeUpdate();

                // Cria nova sessão
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_auth_sessions (uuid, ip_address, expires_at) " +
                                "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 MINUTE))"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Limpa sessões expiradas periodicamente
     */
    private void startSessionCleaner() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM rs_auth_sessions WHERE expires_at < NOW()"
                );
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("§e[Auth] " + deleted + " sessões expiradas removidas.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 6000L, 6000L); // A cada 5 minutos
    }

    /**
     * Registra ação no log
     */
    private void logAction(UUID uuid, String username, String action, String ip, boolean success) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_auth_logs (uuid, username, action, ip_address, success) " +
                                "VALUES (?, ?, ?, ?, ?)"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, action);
                ps.setString(4, ip);
                ps.setBoolean(5, success);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Gera hash SHA-256 da senha
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Altera a senha de um jogador
     */
    public void changePassword(Player player, String oldPassword, String newPassword) {
        if (newPassword.length() < 6) {
            player.sendMessage("§c§lERRO: §cA nova senha deve ter no mínimo 6 caracteres!");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Verifica senha antiga
                PreparedStatement check = conn.prepareStatement(
                        "SELECT password_hash FROM rs_auth_accounts WHERE uuid = ?"
                );
                check.setString(1, player.getUniqueId().toString());
                ResultSet rs = check.executeQuery();

                if (!rs.next()) {
                    player.sendMessage("§c§lERRO: §cConta não encontrada!");
                    return;
                }

                String storedHash = rs.getString("password_hash");
                if (!storedHash.equals(hashPassword(oldPassword))) {
                    player.sendMessage("§c§lERRO: §cSenha atual incorreta!");
                    return;
                }

                // Atualiza senha
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE rs_auth_accounts SET password_hash = ? WHERE uuid = ?"
                );
                update.setString(1, hashPassword(newPassword));
                update.setString(2, player.getUniqueId().toString());
                update.executeUpdate();

                player.sendMessage("");
                player.sendMessage("§a§l✔ SENHA ALTERADA!");
                player.sendMessage("§aSua senha foi atualizada com sucesso.");
                player.sendMessage("§eGuarde-a em um local seguro!");
                player.sendMessage("");

            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("§c§lERRO: §cFalha ao alterar senha.");
            }
        });
    }
}