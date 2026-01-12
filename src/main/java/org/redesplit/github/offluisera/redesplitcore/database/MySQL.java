package org.redesplit.github.offluisera.redesplitcore.database;

import org.bukkit.Bukkit;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class MySQL {

    private final RedeSplitCore plugin;
    private static HikariDataSource dataSource;

    public MySQL(RedeSplitCore plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        try {
            HikariConfig config = new HikariConfig();

            String host = plugin.getConfig().getString("database.host", "localhost");
            String db = plugin.getConfig().getString("database.name", "redesplit");
            String user = plugin.getConfig().getString("database.user", "root");
            String pass = plugin.getConfig().getString("database.password", "");
            int porta = plugin.getConfig().getInt("database.port", 3306);

            String url = "jdbc:mysql://" + host + ":" + porta + "/" + db +
                    "?useSSL=false&useTimezone=true&serverTimezone=UTC&allowPublicKeyRetrieval=true" +
                    "&autoReconnect=true&useUnicode=true&characterEncoding=utf8";

            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // ✅ CONFIGURAÇÕES OTIMIZADAS PARA EVITAR TIMEOUT
            config.setMaximumPoolSize(20);              // Aumentado de 10 para 20
            config.setMinimumIdle(5);                   // Aumentado de 2 para 5
            config.setConnectionTimeout(10000);         // 10 segundos (antes era 5)
            config.setIdleTimeout(600000);              // 10 minutos
            config.setMaxLifetime(1800000);             // 30 minutos
            config.setLeakDetectionThreshold(300000);    // Detecta vazamento após 60s

            // Propriedades de performance
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            dataSource = new HikariDataSource(config);

            plugin.getLogger().info("§a[MySQL] Pool de conexões iniciado (20 máx / 5 mín)");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Erro na inicialização do MySQL: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void updatePermissionsTable() {
        try (Connection conn = getConnection()) {

            // 1. Adiciona coluna SERVER_SCOPE (Ex: 'survival', 'lobby', 'GLOBAL')
            // O padrão é 'GLOBAL' para manter compatibilidade com o que já existe
            try {
                PreparedStatement st = conn.prepareStatement(
                        "ALTER TABLE rs_ranks_permissions ADD COLUMN server_scope VARCHAR(32) DEFAULT 'GLOBAL'"
                );
                st.executeUpdate();
                st.close();
                Bukkit.getLogger().info("§a[MySQL] Coluna 'server_scope' adicionada com sucesso!");
            } catch (SQLException e) {
                // Ignora o erro se a coluna já existir (seguro para rodar sempre)
            }

            // 2. Adiciona coluna WORLD_SCOPE (Ex: 'spawn', 'minerar', 'GLOBAL')
            try {
                PreparedStatement st = conn.prepareStatement(
                        "ALTER TABLE rs_ranks_permissions ADD COLUMN world_scope VARCHAR(32) DEFAULT 'GLOBAL'"
                );
                st.executeUpdate();
                st.close();
                Bukkit.getLogger().info("§a[MySQL] Coluna 'world_scope' adicionada com sucesso!");
            } catch (SQLException e) {
                // Ignora o erro se a coluna já existir
            }

        } catch (SQLException e) {
            Bukkit.getLogger().severe("§c[MySQL] Erro ao atualizar tabela de permissões: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addExpirationColumn() {
        try (Connection conn = getConnection()) {
            try {
                // Adiciona coluna EXPIRATION (DATETIME)
                // Se for NULL, significa que é PERMANENTE
                PreparedStatement st = conn.prepareStatement(
                        "ALTER TABLE rs_ranks_permissions ADD COLUMN expiration DATETIME DEFAULT NULL"
                );
                st.executeUpdate();
                st.close();
                Bukkit.getLogger().info("§a[MySQL] Coluna 'expiration' adicionada com sucesso!");
            } catch (SQLException e) {
                // Ignora se a coluna já existir
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateStaffStatus(String playerName, boolean vanished, boolean staffMode) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                String sql = "INSERT INTO rs_staff_status (player_name, is_vanished, is_staff_mode, last_update) " +
                        "VALUES (?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE " +
                        "is_vanished = ?, is_staff_mode = ?, last_update = NOW()";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerName);
                    ps.setInt(2, vanished ? 1 : 0);
                    ps.setInt(3, staffMode ? 1 : 0);
                    ps.setInt(4, vanished ? 1 : 0);
                    ps.setInt(5, staffMode ? 1 : 0);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {

            // --- TABELAS BÁSICAS ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "ip VARCHAR(45)," + // Adicionado IP para consistência
                    "last_ip VARCHAR(45)," + // Adicionado Last IP para consistência
                    "rank_id VARCHAR(32) DEFAULT 'membro', " +
                    "coins DOUBLE DEFAULT 0.0, " +
                    "cash DOUBLE DEFAULT 0.0, " +
                    "playtime BIGINT DEFAULT 0, " + // Adicionado Playtime
                    "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " + // Adicionado Last Login
                    "join_message VARCHAR(100), " + // Adicionado Join Message
                    "join_color VARCHAR(2) DEFAULT '§b')"); // Adicionado Join Color

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_web_commands (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_name VARCHAR(16), " +
                    "action VARCHAR(32), " +
                    "value VARCHAR(255), " +
                    "executed TINYINT(1) DEFAULT 0)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_temp_ranks (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "rank_id VARCHAR(32), " +
                    "previous_rank VARCHAR(32), " + // Mantido para compatibilidade, embora foreign key seja melhor
                    "expires DATETIME, " + // Mantido expires DATETIME
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)"); // Adicionado FK

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_user_permissions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36), " +
                    "permission VARCHAR(255), " +
                    "expires DATETIME, " +
                    "active TINYINT(1) DEFAULT 1)");

            // --- TABELA DE DENÚNCIAS (REPORTS) ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_reports (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "reporter VARCHAR(32) NOT NULL," +
                    "reported VARCHAR(32) NOT NULL," +
                    "reason VARCHAR(255) NOT NULL," +
                    "status VARCHAR(20) DEFAULT 'ABERTO'," +
                    "solved_by VARCHAR(32) DEFAULT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "solved_at TIMESTAMP NULL," +
                    "evidence_url VARCHAR(255) DEFAULT NULL" + // Adicionado evidence_url
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_chat_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_name VARCHAR(16) NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "date DATETIME DEFAULT CURRENT_TIMESTAMP)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_ip_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "ip VARCHAR(45) NOT NULL, " +
                    "date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)"); // Adicionado FK

            // Tabela de vinculação Discord
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rs_discord_links (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "username VARCHAR(16) NOT NULL," +
                            "discord_id VARCHAR(20) DEFAULT NULL," +
                            "discord_tag VARCHAR(40) DEFAULT NULL," +
                            "verification_code VARCHAR(8) DEFAULT NULL," +
                            "code_expires_at TIMESTAMP NULL," +
                            "linked_at TIMESTAMP NULL," +
                            "status VARCHAR(20) DEFAULT 'PENDING'," +  // PENDING, LINKED, EXPIRED
                            "INDEX (discord_id), INDEX (verification_code)" +
                            ")"
            );

            // --- TABELAS DE PUNIÇÕES (Corrigido para corresponder à estrutura esperada) ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_punishments (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "staff_name VARCHAR(16)," +
                    "type VARCHAR(20)," +
                    "reason VARCHAR(100)," +
                    "expires BIGINT," +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "active BOOLEAN DEFAULT TRUE," +
                    "evidence_url VARCHAR(255) DEFAULT NULL)"); // Adicionado evidence_url

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_tickets (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "author VARCHAR(16) NOT NULL, " +
                    "subject VARCHAR(100) NOT NULL, " +
                    "category VARCHAR(50) NOT NULL, " +
                    "status VARCHAR(20) DEFAULT 'ABERTO', " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_ticket_replies (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "ticket_id INT NOT NULL, " +
                    "user VARCHAR(16) NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "is_staff TINYINT(1) DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (ticket_id) REFERENCES rs_tickets(id) ON DELETE CASCADE)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_server_stats (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "online_players INT, " +
                    "tps DOUBLE, " +
                    "ram_usage DOUBLE, " +
                    "date DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // --- TABELA DE NOTÍCIAS ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_news (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(100) NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "type VARCHAR(20) NOT NULL, " +
                    "author VARCHAR(30) NOT NULL, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // --- NOVAS TABELAS PARA O SISTEMA DE RANKS ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_ranks (" +
                    "rank_id VARCHAR(32) PRIMARY KEY, " +
                    "display_name VARCHAR(32), " +
                    "prefix VARCHAR(32), " +
                    "color VARCHAR(2), " +
                    "parent_rank VARCHAR(50) DEFAULT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_ranks_permissions (" +
                    "rank_id VARCHAR(32) NOT NULL, " +
                    "permission VARCHAR(255) NOT NULL, " +
                    "PRIMARY KEY (rank_id, permission))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_known_permissions (" +
                    "permission VARCHAR(255) PRIMARY KEY)");

            // Tabela de Logs de Auditoria (Audit Log)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_audit_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(32) NOT NULL, " +
                    "action VARCHAR(10) NOT NULL, " +
                    "rank_id VARCHAR(32) NOT NULL, " +
                    "permission VARCHAR(255) NOT NULL, " +
                    "date DATETIME DEFAULT CURRENT_TIMESTAMP)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_staff_status (" +
                    "player_name VARCHAR(16) PRIMARY KEY, " +
                    "is_vanished TINYINT(1) DEFAULT 0, " +
                    "is_staff_mode TINYINT(1) DEFAULT 0, " +
                    "last_update DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_economy_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "total_coins DOUBLE NOT NULL, " +
                    "total_cash DOUBLE NOT NULL, " +
                    "date DATE UNIQUE NOT NULL)");

            // Tabela de Palavras Proibidas
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_banned_words (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "word VARCHAR(100) NOT NULL UNIQUE, " +
                    "severity ENUM('baixo', 'medio', 'alto') DEFAULT 'medio')");

            // Tabela de Regras de Mute Predefinidas
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_punishment_types (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "reason_name VARCHAR(100) NOT NULL, " +
                    "duration VARCHAR(20) NOT NULL, " +
                    "category VARCHAR(50))");

            // Inserção de dados iniciais para o filtro funcionar imediatamente
            st.executeUpdate("INSERT IGNORE INTO rs_punishment_types (reason_name, duration) VALUES " +
                    "('Divulgação de Servidores', '12h'), ('Ofensas', '12h'), ('Preconceito', '0')");

            st.executeUpdate("INSERT IGNORE INTO rs_banned_words (word) VALUES ('lixo'), ('hack'), ('curioso'), ('.com'), ('.br')");

            // Tabela para o Ranking de Infratores (Alertas do Filtro)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_filter_alerts (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_name VARCHAR(16) NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "severity ENUM('baixo', 'medio', 'alto') DEFAULT 'baixo', " +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Tabela de Auditoria de Ações da Staff
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_staff_actions_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "operator VARCHAR(16) NOT NULL, " +
                    "action_type VARCHAR(50) NOT NULL, " +
                    "details TEXT NOT NULL, " +
                    "ip_address VARCHAR(45), " +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_staff_chat (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(32) NOT NULL," +
                    "message TEXT NOT NULL," +
                    "source VARCHAR(10) DEFAULT 'GAME'," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- NOVA TABELA: ENQUETES (POLLS) ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_polls (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "question VARCHAR(255) NOT NULL," +
                    "options TEXT NOT NULL," +
                    "winner VARCHAR(255) DEFAULT NULL," +
                    "results TEXT DEFAULT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "status VARCHAR(10) DEFAULT 'CLOSED'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- TABELA DE MOTD ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_motd (" +
                    "id INT PRIMARY KEY," +
                    "line1 VARCHAR(255) DEFAULT '&6&lREDE SPLIT &7- &fVenha se divertir!'," +
                    "line2 VARCHAR(255) DEFAULT '&eAcesse: &bwww.redesplit.com.br'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- TABELA DE REVISÕES (APPEALS) ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_appeals (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "punishment_id INT DEFAULT 0," +
                    "reason TEXT NOT NULL," +
                    "status VARCHAR(20) DEFAULT 'PENDENTE'," +
                    "staff_handler VARCHAR(32) DEFAULT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "handled_at TIMESTAMP NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- TABELA: MURAL DE AMIZADES (PROFILE COMMENTS) ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_profile_comments (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "profile_owner VARCHAR(32) NOT NULL," +
                    "author VARCHAR(32) NOT NULL," +
                    "comment TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX (profile_owner)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- TABELA: MEDALHAS DOS JOGADORES ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_player_badges (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "badge_id VARCHAR(32) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX (player_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // --- TABELA: NOTIFICAÇÕES DO SISTEMA ---
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_notifications (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "type VARCHAR(20) NOT NULL," +
                    "message TEXT NOT NULL," +
                    "is_read TINYINT(1) DEFAULT 0," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX (player_name, is_read)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_global_chat (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "author VARCHAR(32) NOT NULL," +
                    "message TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_audit_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(255) NOT NULL," +
                    "action VARCHAR(255) NOT NULL," +
                    "rank_id VARCHAR(255) NOT NULL," +
                    "permission VARCHAR(255) NOT NULL," +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            Bukkit.getLogger().info("§a[MySQL] Tabela de Auditoria Perm (Logs) verificada!");

            // TABELA CUPONS DESCONTO

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_products (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL," +
                    "description VARCHAR(255)," +
                    "price DECIMAL(10,2) NOT NULL," +
                    "old_price DECIMAL(10,2) DEFAULT NULL," + // Preço antigo (Promoção)
                    "icon VARCHAR(50) DEFAULT 'fa-box'," +
                    "icon_color VARCHAR(20) DEFAULT '#0d6efd'," + // Cor do ícone
                    "command TEXT NOT NULL," +
                    "category VARCHAR(20) DEFAULT 'VIP'," +
                    "server VARCHAR(50) DEFAULT 'Global'" +
                    ")");

            Bukkit.getLogger().info("§a[MySQL] Tabela de cupons da loja verificada!");

            // Tabela de Keys

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_keys (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "code VARCHAR(32) NOT NULL UNIQUE," +
                    "reward_cmd TEXT NOT NULL," +
                    "max_uses INT DEFAULT 1," +
                    "uses INT DEFAULT 0," +
                    "note VARCHAR(100)," +
                    "expires_at DATETIME NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Tabela Financeira in-game

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_transactions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(32) NOT NULL," +
                    "admin_user VARCHAR(32) DEFAULT 'SISTEMA'," +
                    "action_type VARCHAR(20) NOT NULL," + // ADD ou REMOVE
                    "currency VARCHAR(10) NOT NULL," +      // CASH ou COINS
                    "amount DECIMAL(15,2) NOT NULL," +      // Suporta centavos (Ex: 10.50)
                    "description VARCHAR(255)," +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // 1. Tabela de Produtos (Itens a venda na loja)
            PreparedStatement st1 = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS rs_products (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL," +
                            "description VARCHAR(255)," +
                            "price DECIMAL(10,2) NOT NULL," +
                            "icon VARCHAR(50) DEFAULT 'fa-box'," +
                            "command TEXT NOT NULL," +
                            "category VARCHAR(20) DEFAULT 'VIP'" +
                            ")"
            );
            st1.executeUpdate();
            st1.close();

            // 2. Tabela de Vendas (Histórico financeiro real)
            PreparedStatement st2 = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS rs_sales (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player VARCHAR(32) NOT NULL," +
                            "product_id INT NOT NULL," +
                            "product_name VARCHAR(64)," +
                            "price_paid DECIMAL(10,2) NOT NULL," +
                            "payment_method VARCHAR(20) DEFAULT 'PIX'," +
                            "status VARCHAR(20) DEFAULT 'PENDING'," + // PENDING, APPROVED, CANCELED
                            "transaction_id VARCHAR(100)," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "approved_at TIMESTAMP NULL" +
                            ")"
            );
            st2.executeUpdate();
            st2.close();

            // 3. Tabela de Fila de Comandos (Para entregar o VIP após aprovação)
            PreparedStatement st3 = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS rs_command_queue (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "command TEXT NOT NULL," +
                            "status VARCHAR(10) DEFAULT 'WAITING'" +
                            ")"
            );
            st3.executeUpdate();
            st3.close();

            Bukkit.getLogger().info("§a[MySQL] Tabelas da Loja Virtual (Produtos, Vendas, Fila) verificadas!");

            // Tabela de banners da loja

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_banners (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "image_url VARCHAR(255) NOT NULL," + // Link da imagem (imgur, etc)
                    "title VARCHAR(100)," +              // Título Grande
                    "subtitle VARCHAR(255)," +           // Texto menor
                    "btn_text VARCHAR(50)," +            // Texto do Botão
                    "btn_link VARCHAR(100)," +           // Link (ex: ?server=RankUP)
                    "display_order INT DEFAULT 0," +      // Ordem de aparição
                    "active TINYINT DEFAULT 1" +          // 1 = Ativo, 0 = Escondido
                    ")");

            Bukkit.getLogger().info("§a[MySQL] Tabela de Banners criada!");

            // Garante que existe uma linha padrão (ID 1)
            st.executeUpdate("INSERT IGNORE INTO rs_motd (id, line1, line2) VALUES (1, '&6&lREDE SPLIT', '&eLoja e Site Online!')");

            // =================================================================================
            // 2. TABELAS DE MODOS DE JOGO (ECONOMIA E STATS SEPARADOS)
            // =================================================================================

            // SkyBlock
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_skyblock (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "balance DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            // Survival
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_survival (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "balance DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            // FullPvP (Com Kills/Deaths)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_fullpvp (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "balance DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "kills INT DEFAULT 0," +
                    "deaths INT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            // RankUp (Com Prestige)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_rankup (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "balance DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "prestige INT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            // BedWars (Coins, Wins, Kills)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_bedwars (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "coins DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "wins INT DEFAULT 0," +
                    "kills INT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            Bukkit.getLogger().info("§a[MySQL] Tabela Stats Server carregada com sucesso!");

            // SkyWars (Coins, Wins, Kills)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_stats_skywars (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16)," +
                    "coins DOUBLE DEFAULT 0," +
                    "playtime BIGINT DEFAULT 0," +
                    "wins INT DEFAULT 0," +
                    "kills INT DEFAULT 0," +
                    "FOREIGN KEY (uuid) REFERENCES rs_players(uuid) ON DELETE CASCADE)");

            // Códigos de invite

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_referral_codes (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "code VARCHAR(20) UNIQUE NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_referrals (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "inviter_uuid VARCHAR(36) NOT NULL," +
                    "invited_uuid VARCHAR(36) NOT NULL UNIQUE," +
                    "status VARCHAR(20) DEFAULT 'PENDING'," +
                    "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            Bukkit.getLogger().info("§a[MySQL] Tabela Referral Codes carregada com sucesso!");

            // Sisatema de transferir cash

            st.executeUpdate("CREATE TABLE IF NOT EXISTS rs_cash_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "sender VARCHAR(16) NOT NULL," +
                    "receiver VARCHAR(16) NOT NULL," +
                    "amount INT NOT NULL," +
                    "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "ip_sender VARCHAR(45)" +
                    ")");

            Bukkit.getLogger().info("§a[MySQL] Tabela Cash Logs carregada com sucesso!");


            RedeSplitCore.getInstance().getLogger().info("§a[MySQL] Tabelas verificadas e carregadas com sucesso!");

            // Atualizações automáticas de colunas
            try { st.executeUpdate("ALTER TABLE rs_products ADD COLUMN upsell_product_id INT DEFAULT NULL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE rs_reports ADD COLUMN staff_handler VARCHAR(32) DEFAULT NULL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE rs_reports ADD COLUMN solved_by VARCHAR(32) DEFAULT NULL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE rs_reports ADD COLUMN solved_at TIMESTAMP NULL DEFAULT NULL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE rs_polls ADD COLUMN results TEXT DEFAULT NULL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE rs_players ADD COLUMN last_ip VARCHAR(45) DEFAULT NULL"); } catch (SQLException e) {}
            try { st.executeUpdate("ALTER TABLE rs_ranks ADD COLUMN parent_rank VARCHAR(50) DEFAULT NULL"); } catch (SQLException e) {}
            try { st.executeUpdate("ALTER TABLE rs_punishments ADD COLUMN evidence_url VARCHAR(255) DEFAULT NULL"); } catch (SQLException e) {}
            try { st.executeUpdate("ALTER TABLE rs_players ADD COLUMN join_message VARCHAR(100) DEFAULT NULL"); } catch (SQLException e) {}
            try { st.executeUpdate("ALTER TABLE rs_reports ADD COLUMN evidence_url VARCHAR(255) DEFAULT NULL"); } catch (SQLException e) {}
            try {
                st.executeUpdate("ALTER TABLE rs_players ADD COLUMN join_color VARCHAR(2) DEFAULT '§b'");
                RedeSplitCore.getInstance().getLogger().info("§a[MySQL] Coluna 'join_color' adicionada com sucesso.");
            } catch (SQLException e) {}

        } catch (SQLException e) {
            RedeSplitCore.getInstance().getLogger().severe("Erro MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Mantido o método de permissões e outros utilitários
    public Set<String> getPermissionsWithInheritance(String initialRankId) {
        Set<String> finalPermissions = new HashSet<>();
        Set<String> visitedRanks = new HashSet<>();
        String currentRank = initialRankId;

        try (Connection conn = getConnection()) {
            while (currentRank != null && !currentRank.isEmpty()) {
                if (visitedRanks.contains(currentRank)) {
                    break;
                }
                visitedRanks.add(currentRank);

                try (PreparedStatement st = conn.prepareStatement("SELECT permission FROM rs_ranks_permissions WHERE rank_id = ?")) {
                    st.setString(1, currentRank);
                    try (ResultSet rs = st.executeQuery()) {
                        while (rs.next()) {
                            finalPermissions.add(rs.getString("permission"));
                        }
                    }
                }

                String parentRank = null;
                try (PreparedStatement st = conn.prepareStatement("SELECT parent_rank FROM rs_ranks WHERE rank_id = ?")) {
                    st.setString(1, currentRank);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) {
                            parentRank = rs.getString("parent_rank");
                        }
                    }
                }
                currentRank = parentRank;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return finalPermissions;
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DataSource is null");
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}