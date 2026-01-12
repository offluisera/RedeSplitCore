package org.redesplit.github.offluisera.redesplitcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.redesplit.github.offluisera.redesplitcore.commands.*;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;
import org.redesplit.github.offluisera.redesplitcore.listeners.*;
import org.redesplit.github.offluisera.redesplitcore.managers.*;
import org.redesplit.github.offluisera.redesplitcore.player.*;
import org.redesplit.github.offluisera.redesplitcore.redis.RedisManager;
import org.redesplit.github.offluisera.redesplitcore.system.AuthSystem;
import org.redesplit.github.offluisera.redesplitcore.system.DiscordLinkManager;
import org.redesplit.github.offluisera.redesplitcore.system.PasswordRecovery; // ✅ IMPORT ADICIONADO
import org.redesplit.github.offluisera.redesplitcore.tasks.*;
import org.redesplit.github.offluisera.redesplitcore.utils.PermissionDumper;


public class RedeSplitCore extends JavaPlugin {

    private static RedeSplitCore instance;
    private MySQL mySQL;
    private PlayerManager playerManager;
    private PermissionInjector permissionInjector;
    private VanishManager vanishManager;
    private RedisManager redisManager;
    private AuthSystem authSystem;
    private PasswordRecovery passwordRecovery; // ✅ VARIÁVEL DE INSTÂNCIA ADICIONADA
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

        // ✅ 3. INICIALIZAR SISTEMA DE AUTENTICAÇÃO
        this.authSystem = new AuthSystem(this);
        getLogger().info("§a[Auth] Sistema de Login ativado!");

        // ✅ 4. INICIALIZAR SISTEMA DE RECUPERAÇÃO DE SENHA
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


        // 7. Tarefas de Economia e Stats
        Bukkit.getScheduler().runTaskTimer(this, new EconomyTask(this), 400L, 72000L);
        new ServerStatsTask().runTaskTimer(this, 200, 200);
        getLogger().info("Sistema de ServerStats RedeSplit Ativado!");
        new ReferralTask().runTaskTimer(this, 1200L, 1200L);
        getLogger().info("Sistema de Referal RedeSplit Ativado!");
        new PermissionCleanupTask().runTaskTimer(this, 1200L, 6000L);
        new DeliveryTask(mySQL).runTaskTimerAsynchronously(this, 100L, 600L);
        getLogger().info("Sistema de Entregas RedeSplit Ativado!");

        // 8. Registrar Comandos
        registerCommands();
        getLogger().info("Sistema de Comandos ativado!");

        // 9. Registrar Eventos
        registerEvents();
        getLogger().info("Sistema de Eventos ativado!");

        getLogger().info("§a[RedeSplitCore] Plugin inicializado com sucesso!");
    }

    @Override
    public void onDisable() {
        // Salvar jogadores online
        if (playerManager != null) {
            getLogger().info("§e[RedeSplitCore] Salvando dados de jogadores...");
        }

        // 1. Desliga o Subscriber
        if (this.redisManager != null) {
            redisManager.stopSubscriber();
            getLogger().info("Redis Subscriber encerrado.");
            this.redisManager.disconnect();
        }

        // 2. Fecha MySQL
        if (mySQL != null) {
            mySQL.close();
        }

        getLogger().info("§c[RedeSplitCore] Plugin desativado.");
    }

    // --- Métodos Auxiliares ---

    private void registerCommands() {
        // ✅ COMANDOS DE AUTENTICAÇÃO
        AuthCommands authCmd = new AuthCommands(authSystem);
        getCommand("login").setExecutor(authCmd);
        getCommand("logar").setExecutor(authCmd);
        getCommand("register").setExecutor(authCmd);
        getCommand("registrar").setExecutor(authCmd);
        getCommand("changepassword").setExecutor(authCmd);
        getCommand("trocarsenha").setExecutor(authCmd);

        // ✅ COMANDO DE RECUPERAÇÃO DE SENHA
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

        // Comandos de Rank
        RankCommands rankCmd = new RankCommands();
        getCommand("setrank").setExecutor(rankCmd);
        getCommand("rank").setExecutor(rankCmd);
        getCommand("settemprank").setExecutor(rankCmd);
        getCommand("perm").setExecutor(rankCmd);
    }

    private void registerEvents() {
        // ✅ Listener de Autenticação (DEVE VIR PRIMEIRO)
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
    public PasswordRecovery getPasswordRecovery() { return passwordRecovery; } // ✅ GETTER ADICIONADO
    public DiscordLinkManager getDiscordLinkManager() {return discordLinkManager;}

    public boolean isRestarting() {
        return restarting;
    }

    public void setRestarting(boolean restarting) {
        this.restarting = restarting;
    }
}