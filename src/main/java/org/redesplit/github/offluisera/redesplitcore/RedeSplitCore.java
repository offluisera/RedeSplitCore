package org.redesplit.github.offluisera.redesplitcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.redesplit.github.offluisera.redesplitcore.commands.*;
import org.redesplit.github.offluisera.redesplitcore.database.MySQL;
import org.redesplit.github.offluisera.redesplitcore.listeners.*;
import org.redesplit.github.offluisera.redesplitcore.managers.*;
import org.redesplit.github.offluisera.redesplitcore.player.*;
import org.redesplit.github.offluisera.redesplitcore.redis.RedisManager;
import org.redesplit.github.offluisera.redesplitcore.tasks.*;
import org.redesplit.github.offluisera.redesplitcore.utils.PermissionDumper;

public class RedeSplitCore extends JavaPlugin {

    private static RedeSplitCore instance;
    private MySQL mySQL;
    private PlayerManager playerManager;
    private PermissionInjector permissionInjector;
    private VanishManager vanishManager;
    private RedisManager redisManager;
    private String serverId;
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

        // 3. Inicializar REDIS (Correção da Duplicidade)
        getLogger().info("Iniciando conexão com Redis...");
        this.redisManager = new RedisManager();
        try {
            // Apenas conecta no Pool
            this.redisManager.connect();

            // Inicia o Subscriber (Apenas UMA vez aqui)
            // this.redisManager.startSubscriber();

        } catch (Exception e) {
            getLogger().severe("§c[ERRO FATAL] Não foi possível iniciar o Redis!");
            e.printStackTrace();
            // Opcional: Desativar plugin se o Redis for essencial
        }


        new StoreTask(this).runTaskTimer(this, 100L, 200L);
        getLogger().info("Sistema de Loja Ativado!");

        // 4. Inicializar Gerenciadores
        MotdManager.loadFromSql();
        this.playerManager = new PlayerManager();
        this.vanishManager = new VanishManager(this);
        this.permissionInjector = new PermissionInjector();

        // 5. Tarefas de Economia e Stats
        Bukkit.getScheduler().runTaskTimer(this, new EconomyTask(this), 400L, 72000L);
        new ServerStatsTask().runTaskTimer(this, 60L, 60L);
        getLogger().info("Sistema de ServerStats RedeSplit Ativado!");
        new ReferralTask().runTaskTimer(this, 1200L, 1200L);
        getLogger().info("Sistema de Referal RedeSplit Ativado!");
        new PermissionCleanupTask().runTaskTimer(this, 1200L, 6000L);
        new DeliveryTask(mySQL).runTaskTimerAsynchronously(this, 100L, 400L);
        getLogger().info("Sistema de Entregas RedeSplit Ativado!");

        // 6. Registrar Comandos
        registerCommands();
        getLogger().info("Sistema de Comandos ativado!");

        // 7. Registrar Eventos
        registerEvents(); // Verifique se não há duplicidade dentro deste método também
        getLogger().info("Sistema de Eventos ativado!");

        getLogger().info("§a[RedeSplitCore] Plugin inicializado com sucesso!");
    }

    @Override
    public void onDisable() {
        // Salvar jogadores online
        if (playerManager != null) {
            getLogger().info("§e[RedeSplitCore] Salvando dados de jogadores...");
            // Adicione logica de salvamento aqui se tiver
        }

        // 1. Desliga o Subscriber (Para a thread fantasma)
        if (this.redisManager != null) {
            redisManager.stopSubscriber();
            getLogger().info("Redis Subscriber encerrado.");

            // 2. Fecha a conexão do Pool
            this.redisManager.disconnect();
        }

        // 3. Fecha MySQL
        if (mySQL != null) {
            mySQL.close();
        }

        getLogger().info("§c[RedeSplitCore] Plugin desativado.");
    }


    // --- Métodos Auxiliares ---

    private void registerCommands() {
        EconomyCommands ecoCmd = new EconomyCommands();
        getCommand("money").setExecutor(ecoCmd);
        getCommand("pay").setExecutor(ecoCmd);
        getCommand("eco").setExecutor(ecoCmd);
        getCommand("cash").setExecutor(ecoCmd);
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

        // Comando essencial para o sistema de anúncios VIP
        getCommand("rsanuncio").setExecutor(new AnuncioCommand());

        PunishCommands punishExecutor = new PunishCommands();
        getCommand("ban").setExecutor(punishExecutor);
        getCommand("tempban").setExecutor(punishExecutor); // Aponta para o mesmo lugar
        getCommand("mute").setExecutor(punishExecutor);
        getCommand("tempmute").setExecutor(punishExecutor);
        getCommand("kick").setExecutor(punishExecutor);
        getCommand("unban").setExecutor(punishExecutor);
        getCommand("unmute").setExecutor(punishExecutor);
        getCommand("pardon").setExecutor(punishExecutor);
        getCommand("report").setExecutor(new ReportCommand());

        RankCommands rankCmd = new RankCommands();
        getCommand("setrank").setExecutor(rankCmd);
        getCommand("rank").setExecutor(rankCmd);
        getCommand("settemprank").setExecutor(rankCmd);
        getCommand("perm").setExecutor(rankCmd);
    }

    private void registerEvents() {
        // Garante que cada listener seja registrado apenas uma vez
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

    public boolean isRestarting() {
        return restarting;
    }

    public void setRestarting(boolean restarting) {
        this.restarting = restarting;
    }

}