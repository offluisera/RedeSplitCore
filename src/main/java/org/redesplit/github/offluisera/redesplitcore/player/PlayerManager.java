package org.redesplit.github.offluisera.redesplitcore.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerManager {

    private final Map<UUID, SplitPlayer> players = new HashMap<>();

    public SplitPlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public void addPlayer(UUID uuid, SplitPlayer splitPlayer) {
        players.put(uuid, splitPlayer);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void unloadPlayer(UUID uuid) {
        SplitPlayer sp = getPlayer(uuid);
        if (sp != null) {
            savePlayer(sp);

            // ⭐ SALVA XP ANTES DE DESCARREGAR
            RedeSplitCore.getInstance().getXPManager().saveXP(sp);

            removePlayer(uuid);
        }
    }

    public void loadPlayer(UUID uuid, String name) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                String serverId = RedeSplitCore.getInstance().getServerId();
                boolean isLobby = serverId.equalsIgnoreCase("geral") || serverId.equalsIgnoreCase("lobby");

                // 1. Busca Global
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM rs_players WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    updateLastLogin(conn, uuid, name);

                    SplitPlayer sp = new SplitPlayer(
                            uuid,
                            name,
                            rs.getString("rank_id"),
                            rs.getDouble("coins"),
                            rs.getDouble("cash")
                    );

                    // --- CARREGA PUNIÇÕES ATIVAS (MUTE) ---
                    loadActiveMute(conn, sp);

                    // ⭐ CARREGA XP (NOVO)
                    loadXP(conn, sp);

                    // 2. Busca Específica (Se não for Lobby)
                    if (!isLobby) {
                        String tableName = getTableName(serverId);
                        String colName = getCoinColumnName(serverId);

                        try (PreparedStatement psGame = conn.prepareStatement("SELECT * FROM " + tableName + " WHERE uuid = ?")) {
                            psGame.setString(1, uuid.toString());
                            ResultSet rsGame = psGame.executeQuery();

                            if (rsGame.next()) {
                                double localMoney = rsGame.getDouble(colName);
                                sp.setCoins(localMoney);
                            } else {
                                createGameData(conn, tableName, uuid, name);
                                sp.setCoins(0.0);
                            }
                        } catch (Exception e) {
                            RedeSplitCore.getInstance().getLogger().warning("Erro ao carregar tabela " + tableName);
                        }
                    }

                    addPlayer(uuid, sp);
                    loadPermissions(uuid);

                    // ⭐ ATUALIZA BARRA DE XP VISUAL (SYNC)
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            RedeSplitCore.getInstance().getXPManager().updateXPBar(player, sp);
                        }
                    });

                } else {
                    createPlayerGlobal(uuid, name);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ⭐ NOVO MÉTODO: CARREGA XP DO BANCO
    private void loadXP(Connection conn, SplitPlayer sp) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT xp, level FROM rs_player_xp WHERE uuid = ?"
            );
            ps.setString(1, sp.getUuid().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long xp = rs.getLong("xp");
                int level = rs.getInt("level");

                sp.setXp(xp);
                sp.setLevel(level);

                RedeSplitCore.getInstance().getLogger().info(
                        "§a[XP] Carregado para " + sp.getName() + ": " + xp + " XP (Nível " + level + ")"
                );
            } else {
                // Cria registro inicial
                PreparedStatement psCreate = conn.prepareStatement(
                        "INSERT INTO rs_player_xp (uuid, player_name, xp, level) VALUES (?, ?, 0, 1)"
                );
                psCreate.setString(1, sp.getUuid().toString());
                psCreate.setString(2, sp.getName());
                psCreate.executeUpdate();
                psCreate.close();

                sp.setXp(0);
                sp.setLevel(1);

                RedeSplitCore.getInstance().getLogger().info(
                        "§e[XP] Criado registro inicial para " + sp.getName()
                );
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            RedeSplitCore.getInstance().getLogger().severe(
                    "§c[XP] Erro ao carregar XP de " + sp.getName()
            );
            e.printStackTrace();
        }
    }

    // --- CARREGA MUTE ATIVO DO BANCO ---
    private void loadActiveMute(Connection conn, SplitPlayer sp) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT expires, reason, operator FROM rs_punishments WHERE player_name = ? AND type = 'MUTE' AND active = 1 AND expires > NOW() ORDER BY id DESC LIMIT 1"
            );
            ps.setString(1, sp.getName());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp("expires");
                if (ts != null) {
                    sp.setMuteExpires(ts.getTime());
                    sp.setMuteReason(rs.getString("reason"));
                    sp.setMuteOperator(rs.getString("operator"));
                }
            } else {
                sp.setMuteExpires(0);
                sp.setMuteReason(null);
                sp.setMuteOperator(null);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- CHAMADO PELO REDIS PARA ATUALIZAR MUTE SEM RELOGAR ---
    public void refreshPunishments(String playerName) {
        Player p = Bukkit.getPlayer(playerName);
        if (p == null) return;

        SplitPlayer sp = getPlayer(p.getUniqueId());
        if (sp == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                loadActiveMute(conn, sp);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // --- CHAMADO PELO REDIS PARA ATUALIZAR ECONOMIA SEM RELOGAR ---
    public void refreshEconomy(UUID uuid) {
        SplitPlayer sp = getPlayer(uuid);
        if (sp == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                String serverId = RedeSplitCore.getInstance().getServerId();
                boolean isLobby = serverId.equalsIgnoreCase("geral") || serverId.equalsIgnoreCase("lobby");

                String table = isLobby ? "rs_players" : getTableName(serverId);
                String col = isLobby ? "coins" : getCoinColumnName(serverId);

                // Recarrega Cash (Sempre Global)
                PreparedStatement psCash = conn.prepareStatement("SELECT cash FROM rs_players WHERE uuid = ?");
                psCash.setString(1, uuid.toString());
                ResultSet rsCash = psCash.executeQuery();
                if (rsCash.next()) {
                    double newCash = rsCash.getDouble("cash");
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> sp.setCash(newCash));
                }

                // Recarrega Coins (Local)
                PreparedStatement psCoins = conn.prepareStatement("SELECT " + col + " FROM " + table + " WHERE uuid = ?");
                psCoins.setString(1, uuid.toString());
                ResultSet rsCoins = psCoins.executeQuery();
                if (rsCoins.next()) {
                    double newCoins = rsCoins.getDouble(col);
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> sp.setCoins(newCoins));
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ⭐ NOVO: CHAMADO PELO REDIS PARA ATUALIZAR XP SEM RELOGAR
    public void refreshXP(UUID uuid) {
        SplitPlayer sp = getPlayer(uuid);
        if (sp == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT xp, level FROM rs_player_xp WHERE uuid = ?"
                );
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    long newXP = rs.getLong("xp");
                    int newLevel = rs.getInt("level");

                    // Atualiza na thread principal
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        sp.setXp(newXP);
                        sp.setLevel(newLevel);

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            RedeSplitCore.getInstance().getXPManager().updateXPBar(player, sp);
                        }
                    });
                }

                rs.close();
                ps.close();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void createPlayerGlobal(UUID uuid, String name) {
        try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rs_players (uuid, name, rank_id, coins, cash, last_login) VALUES (?, ?, 'membro', 0.0, 0.0, NOW()) " +
                            "ON DUPLICATE KEY UPDATE name = VALUES(name), last_login = NOW()");
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();

            SplitPlayer sp = new SplitPlayer(uuid, name, "membro", 0.0, 0.0);

            // ⭐ CRIA REGISTRO DE XP PARA NOVO JOGADOR
            PreparedStatement psXP = conn.prepareStatement(
                    "INSERT INTO rs_player_xp (uuid, player_name, xp, level) VALUES (?, ?, 0, 1)"
            );
            psXP.setString(1, uuid.toString());
            psXP.setString(2, name);
            psXP.executeUpdate();
            psXP.close();

            sp.setXp(0);
            sp.setLevel(1);

            addPlayer(uuid, sp);

            String serverId = RedeSplitCore.getInstance().getServerId();
            if (!serverId.equalsIgnoreCase("geral") && !serverId.equalsIgnoreCase("lobby")) {
                createGameData(conn, getTableName(serverId), uuid, name);
            }
            loadPermissions(uuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createGameData(Connection conn, String table, UUID uuid, String name) throws SQLException {
        String sql = "INSERT IGNORE INTO " + table + " (uuid, player_name) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public void savePlayer(SplitPlayer sp) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                String serverId = RedeSplitCore.getInstance().getServerId();
                boolean isLobby = serverId.equalsIgnoreCase("geral") || serverId.equalsIgnoreCase("lobby");
                long sessionTime = sp.getSessionPlaytime();

                PreparedStatement psGlobal = conn.prepareStatement("UPDATE rs_players SET cash = ?, last_login = NOW() WHERE uuid = ?");
                psGlobal.setDouble(1, sp.getCash());
                psGlobal.setString(2, sp.getUuid().toString());
                psGlobal.executeUpdate();

                if (isLobby) {
                    PreparedStatement psLobby = conn.prepareStatement("UPDATE rs_players SET coins = ?, playtime = playtime + ? WHERE uuid = ?");
                    psLobby.setDouble(1, sp.getCoins());
                    psLobby.setLong(2, sessionTime);
                    psLobby.setString(3, sp.getUuid().toString());
                    psLobby.executeUpdate();
                } else {
                    String table = getTableName(serverId);
                    String col = getCoinColumnName(serverId);
                    String sql = "UPDATE " + table + " SET " + col + " = ?, playtime = playtime + ? WHERE uuid = ?";
                    try (PreparedStatement psGame = conn.prepareStatement(sql)) {
                        psGame.setDouble(1, sp.getCoins());
                        psGame.setLong(2, sessionTime);
                        psGame.setString(3, sp.getUuid().toString());
                        psGame.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateLastLogin(Connection conn, UUID uuid, String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("UPDATE rs_players SET last_login = NOW(), name = ? WHERE uuid = ?");
        ps.setString(1, name);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
    }

    private void loadPermissions(UUID uuid) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            Player player = Bukkit.getPlayer(uuid);
            updatePermissions(Bukkit.getPlayer(uuid));
            TagManager.update(Bukkit.getPlayer(uuid));
            TagManager.updateForPlayer(player);
            TagManager.updateAll();
        });
    }

    public void updatePermissions(Player player) {
        if (player == null) return;
        SplitPlayer sp = getPlayer(player.getUniqueId());
        if (sp == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                sp.getPermissions().clear();
                Set<String> rankPerms = RedeSplitCore.getInstance().getMySQL().getPermissionsWithInheritance(sp.getRankId());
                sp.getPermissions().addAll(rankPerms);

                PreparedStatement psUser = conn.prepareStatement(
                        "SELECT permission FROM rs_user_permissions WHERE uuid = ? AND active = 1 AND (expires IS NULL OR expires > NOW())");
                psUser.setString(1, player.getUniqueId().toString());
                ResultSet rsUser = psUser.executeQuery();
                while (rsUser.next()) {
                    sp.getPermissions().add(rsUser.getString("permission"));
                }

                Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                    if (player.isOnline()) {
                        RedeSplitCore.getInstance().getPermissionInjector().inject(player, sp.getPermissions());
                    }
                });
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private String getTableName(String serverId) {
        switch (serverId.toLowerCase()) {
            case "skyblock": return "rs_stats_skyblock";
            case "rankup":   return "rs_stats_rankup";
            case "fullpvp":  return "rs_stats_fullpvp";
            case "survival": return "rs_stats_survival";
            case "bedwars":  return "rs_stats_bedwars";
            case "skywars":  return "rs_stats_skywars";
            default:         return "rs_players";
        }
    }

    private String getCoinColumnName(String serverId) {
        switch (serverId.toLowerCase()) {
            case "bedwars":
            case "skywars":
                return "coins";
            default:
                return "balance";
        }
    }
}