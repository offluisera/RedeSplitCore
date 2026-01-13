package org.redesplit.github.offluisera.redesplitcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.redesplit.github.offluisera.redesplitcore.api.PlaceholderAPI; // ✅ NOVO IMPORT
import org.redesplit.github.offluisera.redesplitcore.commands.*;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;
import org.redesplit.github.offluisera.redesplitcore.listeners.*;
import org.redesplit.github.offluisera.redesplitcore.managers.*;
import org.redesplit.github.offluisera.redesplitcore.player.*;
import org.redesplit.github.offluisera.redesplitcore.redis.RedisManager;
import org.redesplit.github.offluisera.redesplitcore.system.AuthSystem;
import org.redesplit.github.offluisera.redesplitcore.system.DiscordLinkManager;
import org.redesplit.github.offluisera.redesplitcore.system.PasswordRecovery;
import org.redesplit.github.offluisera.redesplitcore.tasks.*;
import org.redesplit.github.offluisera.redesplitcore.utils.PermissionDumper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class RedeSplitCore extends JavaPlugin {

    private static RedeSplitCore instance;
    private MySQL mySQL;
    private boolean shuttingDown = false;
    private PlayerManager playerManager;
    private PermissionInjector permissionInjector;
    private VanishManager vanishManager;
    private RedisManager redisManager;
    private AuthSystem authSystem;
    private PasswordRecovery passwordRecovery;
    private PlaceholderAPI placeholderAPI; // ✅ NOVO
    private String serverId;
    private DiscordLinkManager discordLinkManager;
    private boolean restarting = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. Tarefas Básicas
        new RankExpirationTask().runTaskTimer(this, 1200L, 1200L);
        new PlaytimeTask().runTaskTimer(this, 1200L, 1200L);
        this.serverId = getConfig().getString("server-id", "desconhecido");
        getLogger().info("§a[RedeSplitCore] Server ID carregado: " + this.serverId);

        // 2. Inicializar Banco de Dados
        this.mySQL = new MySQL(this);
        if (!mySQL.setup()) {
            getLogger().severe("§c[RedeSplitCore] Falha ao conectar no MySQL. Desativando...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mySQL.createTables();
        this.mySQL.updatePermissionsTable();
        this.mySQL.addExpirationColumn();

        PermissionDumper.dump();

        // 3. INICIALIZAR SISTEMA DE AUTENTICAÇÃO
        this.authSystem = new AuthSystem(this);
        getLogger().info("§a[Auth] Sistema de Login ativado!");

        // 4. INICIALIZAR SISTEMA DE RECUPERAÇÃO DE SENHA
        this.passwordRecovery = new PasswordRecovery(this);
        getLogger().info("§a[Auth] Sistema de Recuperação de Senha ativado!");

        // 5. Inicializar REDIS
        getLogger().info("Iniciando conexão com Redis...");
        this.redisManager = new RedisManager();
        try {
            this.redisManager.connect();
        } catch (Exception e) {
            getLogger().severe("§c[ERRO FATAL] Não foi possível iniciar o Redis!");
            e.printStackTrace();
        }

        new StoreTask(this).runTaskTimer(this, 100L, 200L);
        getLogger().info("Sistema de Loja Ativado!");

        // 6. Inicializar Gerenciadores
        MotdManager.loadFromSql();
        this.playerManager = new PlayerManager();
        this.vanishManager = new VanishManager(this);
        this.permissionInjector = new PermissionInjector();

        this.discordLinkManager = new DiscordLinkManager(this);
        getLogger().info("§a[Discord] Sistema de vinculação ativado!");

        // ✅ 7. INICIALIZAR PLACEHOLDER API
        this.placeholderAPI = new PlaceholderAPI(this);
        getLogger().info("§a[PlaceholderAPI] Sistema de Placeholders ativado!");
        getLogger().info("§e[PlaceholderAPI] Outros plugins podem usar: RedeSplitCore.getPlaceholderAPI()");

        // 8. Tarefas de Economia e Stats
        Bukkit.getScheduler().runTaskTimer(this, new EconomyTask(this), 400L, 72000L);
        new ServerStatsTask().runTaskTimer(this, 200, 200);
        getLogger().info("Sistema de ServerStats RedeSplit Ativado!");
        new ReferralTask().runTaskTimer(this, 1200L, 1200L);
        getLogger().info("Sistema de Referal RedeSplit Ativado!");
        new PermissionCleanupTask().runTaskTimer(this, 1200L, 6000L);
        new DeliveryTask(mySQL).runTaskTimerAsynchronously(this, 100L, 600L);
        getLogger().info("Sistema de Entregas RedeSplit Ativado!");

        // 9. Registrar Comandos
        registerCommands();
        getLogger().info("Sistema de Comandos ativado!");

        // 10. Registrar Eventos
        registerEvents();
        getLogger().info("Sistema de Eventos ativado!");

        getLogger().info("§a[RedeSplitCore] Plugin inicializado com sucesso!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§e╔════════════════════════════════════════╗");
        getLogger().info("§e║  RedeSplitCore - Shutdown Iniciado    ║");
        getLogger().info("§e╚════════════════════════════════════════╝");

        // === MARCA COMO DESLIGANDO ===
        shuttingDown = true;

        // === 1. CANCELA TODAS AS TASKS PRIMEIRO ===
        getLogger().info("§e[1/5] Cancelando tasks assíncronas...");
        try {
            Bukkit.getScheduler().cancelTasks(this);
            getLogger().info("§a[✓] Tasks canceladas!");
        } catch (Exception e) {
            getLogger().warning("§c[!] Erro ao cancelar tasks: " + e.getMessage());
        }

        // === 2. AGUARDA TASKS TERMINAREM ===
        getLogger().info("§e[2/5] Aguardando tasks finalizarem...");
        try {
            Thread.sleep(1000); // 1 segundo para tasks terminarem
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // === 3. SALVA DADOS DOS JOGADORES (SÍNCRONO) ===
        getLogger().info("§e[3/5] Salvando dados dos jogadores...");
        if (playerManager != null) {
            try {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    SplitPlayer sp = playerManager.getPlayer(p.getUniqueId());
                    if (sp != null) {
                        // Salva de forma SÍNCRONA durante shutdown
                        savePlayerSync(sp);
                    }
                }
                getLogger().info("§a[✓] Jogadores salvos: " + Bukkit.getOnlinePlayers().size());
            } catch (Exception e) {
                getLogger().severe("§c[!] Erro ao salvar jogadores: " + e.getMessage());
            }
        }

        // === 4. DESLIGA REDIS ===
        getLogger().info("§e[4/5] Desconectando Redis...");
        if (redisManager != null) {
            try {
                redisManager.stopSubscriber();
                redisManager.disconnect();
                getLogger().info("§a[✓] Redis desconectado!");
            } catch (Exception e) {
                getLogger().warning("§c[!] Erro ao desconectar Redis: " + e.getMessage());
            }
        }

        // === 5. FECHA HIKARICP POR ÚLTIMO ===
        getLogger().info("§e[5/5] Fechando pool de conexões MySQL...");
        if (mySQL != null) {
            try {
                mySQL.close();
                getLogger().info("§a[✓] MySQL desconectado!");
            } catch (Exception e) {
                getLogger().severe("§c[!] Erro ao desconectar MySQL: " + e.getMessage());
            }
        }

        getLogger().info("§a╔════════════════════════════════════════╗");
        getLogger().info("§a║  RedeSplitCore Desligado com Sucesso  ║");
        getLogger().info("§a╚════════════════════════════════════════╝");
    }

    /**
     * Salva jogador de forma SÍNCRONA (usado no shutdown)
     */
    private void savePlayerSync(SplitPlayer sp) {
        if (mySQL == null || !mySQL.isConnected()) {
            return;
        }

        try (Connection conn = mySQL.getConnection()) {
            String serverId = getServerId();
            boolean isLobby = serverId.equalsIgnoreCase("geral") || serverId.equalsIgnoreCase("lobby");
            long sessionTime = sp.getSessionPlaytime();

            // Salva Global (Cash)
            PreparedStatement psGlobal = conn.prepareStatement(
                    "UPDATE rs_players SET cash = ?, last_login = NOW() WHERE uuid = ?"
            );
            psGlobal.setDouble(1, sp.getCash());
            psGlobal.setString(2, sp.getUuid().toString());
            psGlobal.executeUpdate();
            psGlobal.close();

            // Salva Local (Coins + Playtime)
            if (isLobby) {
                PreparedStatement psLobby = conn.prepareStatement(
                        "UPDATE rs_players SET coins = ?, playtime = playtime + ? WHERE uuid = ?"
                );
                psLobby.setDouble(1, sp.getCoins());
                psLobby.setLong(2, sessionTime);
                psLobby.setString(3, sp.getUuid().toString());
                psLobby.executeUpdate();
                psLobby.close();
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
            getLogger().warning("Erro ao salvar " + sp.getName() + ": " + e.getMessage());
        }
    }

    // Métodos auxiliares do savePlayerSync
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

    /**
     * Verifica se o plugin está em processo de shutdown
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    // --- Métodos Auxiliares ---

    private void registerCommands() {
        // COMANDOS DE AUTENTICAÇÃO
        AuthCommands authCmd = new AuthCommands(authSystem);
        getCommand("login").setExecutor(authCmd);
        getCommand("logar").setExecutor(authCmd);
        getCommand("register").setExecutor(authCmd);
        getCommand("registrar").setExecutor(authCmd);
        getCommand("changepassword").setExecutor(authCmd);
        getCommand("trocarsenha").setExecutor(authCmd);

        // COMANDO DE RECUPERAÇÃO DE SENHA
        getCommand("recovery").setExecutor(new RecoveryCommand(passwordRecovery));
        getCommand("vinculardc").setExecutor(new DiscordLinkCommand());


        // Comandos de Economia
        EconomyCommands ecoCmd = new EconomyCommands();
        getCommand("money").setExecutor(ecoCmd);
        getCommand("pay").setExecutor(ecoCmd);
        getCommand("eco").setExecutor(ecoCmd);
        getCommand("cash").setExecutor(ecoCmd);

        // Comandos Diversos
        getCommand("ativar").setExecutor(new KeyCommand());
        getCommand("check").setExecutor(new CheckCommand());
        getCommand("ranks").setExecutor(new RanksCommand());
        getCommand("setparent").setExecutor(new SetParentCommand());
        getCommand("permhelp").setExecutor(new PermHelpCommand());
        getCommand("vanish").setExecutor(new VanishCommand());
        getCommand("sc").setExecutor(new StaffChatCommand());
        getCommand("votar").setExecutor(new VoteCommand());
        getCommand("medalha").setExecutor(new MedalhaCommand());
        getCommand("indique").setExecutor(new ReferralCommand());
        getCommand("rsanuncio").setExecutor(new AnuncioCommand());

        // Comandos de Punição
        PunishCommands punishExecutor = new PunishCommands();
        getCommand("ban").setExecutor(punishExecutor);
        getCommand("tempban").setExecutor(punishExecutor);
        getCommand("mute").setExecutor(punishExecutor);
        getCommand("tempmute").setExecutor(punishExecutor);
        getCommand("kick").setExecutor(punishExecutor);
        getCommand("unban").setExecutor(punishExecutor);
        getCommand("unmute").setExecutor(punishExecutor);
        getCommand("pardon").setExecutor(punishExecutor);
        getCommand("report").setExecutor(new ReportCommand());
        getCommand("phtest").setExecutor(new PlaceholderTestCommand());


        // Comandos de Rank
        RankCommands rankCmd = new RankCommands();
        getCommand("setrank").setExecutor(rankCmd);
        getCommand("rank").setExecutor(rankCmd);
        getCommand("settemprank").setExecutor(rankCmd);
        getCommand("perm").setExecutor(rankCmd);
        getCommand("tag").setExecutor(new TagCommand());
    }

    private void registerEvents() {
        // Listener de Autenticação (DEVE VIR PRIMEIRO)
        getServer().getPluginManager().registerEvents(new AuthListener(this, authSystem), this);

        // Listeners Existentes
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new RestartListener(), this);
        getServer().getPluginManager().registerEvents(new MotdListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new PunishmentListener(), this);
    }

    public String getServerId() {
        return getConfig().getString("server-id", "desconhecido");
    }

    // --- Getters ---
    public static RedeSplitCore getInstance() { return instance; }
    public MySQL getMySQL() { return mySQL; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public PermissionInjector getPermissionInjector() { return permissionInjector; }
    public RedisManager getRedisManager() { return redisManager; }
    public AuthSystem getAuthSystem() { return authSystem; }
    public PasswordRecovery getPasswordRecovery() { return passwordRecovery; }
    public DiscordLinkManager getDiscordLinkManager() {return discordLinkManager;}

    // ✅ GETTER DA PLACEHOLDER API
    public static PlaceholderAPI getPlaceholderAPI() {
        return instance.placeholderAPI;
    }

    public boolean isRestarting() {
        return restarting;
    }

    public void setRestarting(boolean restarting) {
        this.restarting = restarting;
    }
}