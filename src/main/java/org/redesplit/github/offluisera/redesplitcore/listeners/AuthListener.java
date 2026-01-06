package org.redesplit.github.offluisera.redesplitcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.system.AuthSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener que gerencia as proteções enquanto o jogador não está autenticado
 * Bloqueia: movimento, chat, comandos, inventário, interações, dano, etc
 */
public class AuthListener implements Listener {

    private final RedeSplitCore plugin;
    private final AuthSystem authSystem;

    // Armazena as localizações iniciais dos jogadores não autenticados
    private final Map<UUID, Location> spawnLocations = new HashMap<>();

    // Tempo limite para fazer login (60 segundos)
    private final int LOGIN_TIMEOUT = 60;

    public AuthListener(RedeSplitCore plugin, AuthSystem authSystem) {
        this.plugin = plugin;
        this.authSystem = authSystem;
    }

    /**
     * Quando o jogador entra no servidor
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress().getAddress().getHostAddress();

        // Remove mensagem padrão de entrada (já tem sistema customizado no ConnectionListener)
        event.setJoinMessage(null);

        // Salva a localização inicial
        spawnLocations.put(uuid, player.getLocation().clone());

        // Verifica se tem sessão ativa
        if (authSystem.hasValidSession(uuid, ip)) {
            // Auto-login por sessão
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !authSystem.isAuthenticated(uuid)) {
                    // Marca como autenticado pela sessão
                    authSystem.authenticateBySession(player);
                    player.sendMessage("§a§l✔ §aLogin automático realizado! (Sessão ativa)");
                }
            }, 10L);
            return;
        }

        // Não tem sessão - precisa fazer login
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!authSystem.isAuthenticated(uuid)) {
                showLoginMessage(player);
                startLoginTimeout(player);
            }
        }, 20L);
    }

    /**
     * Quando o jogador sai do servidor
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        authSystem.logout(player);
        spawnLocations.remove(player.getUniqueId());
    }

    /**
     * Exibe mensagem de login/registro
     */
    private void showLoginMessage(Player player) {
        boolean isRegistered = authSystem.isRegistered(player.getUniqueId());

        // DEBUG - Remover após testar
        plugin.getLogger().info("§e[Auth] DEBUG - Jogador: " + player.getName() + " | UUID: " + player.getUniqueId() + " | Registrado: " + isRegistered);

        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("       §e§lSISTEMA DE AUTENTICAÇÃO REDESPLIT");
        player.sendMessage("");

        if (isRegistered) {
            player.sendMessage("     §fBem-vindo de volta, §a" + player.getName() + "§f!");
            player.sendMessage("");
            player.sendMessage("     §7Faça login para continuar:");
            player.sendMessage("     §e/login <senha>");
        } else {
            player.sendMessage("     §fOlá, §a" + player.getName() + "§f!");
            player.sendMessage("");
            player.sendMessage("     §7Para começar a jogar, registre-se:");
            player.sendMessage("     §e/register <senha> [email]");
            player.sendMessage("");
            player.sendMessage("     §7O email é opcional, mas recomendado");
            player.sendMessage("     §7para recuperação de conta.");
        }

        player.sendMessage("");
        player.sendMessage("     §c§l⚠ §cVocê tem §f" + LOGIN_TIMEOUT + " segundos §cpara se autenticar!");
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    /**
     * Inicia o contador de timeout
     */
    private void startLoginTimeout(Player player) {
        new BukkitRunnable() {
            int countdown = LOGIN_TIMEOUT;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (authSystem.isAuthenticated(player.getUniqueId())) {
                    cancel();
                    return;
                }

                countdown--;

                // Avisos em momentos específicos
                if (countdown == 30 || countdown == 10 || countdown <= 5) {
                    player.sendMessage("§c§l⚠ §cVocê tem §f" + countdown + " segundos §cpara se autenticar!");
                }

                if (countdown <= 0) {
                    player.kickPlayer(
                            "§c§lTEMPO ESGOTADO\n\n" +
                                    "§cVocê não fez login a tempo.\n" +
                                    "§eReconecte e tente novamente."
                    );
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Executa a cada segundo
    }

    // ========================================
    // BLOQUEIOS DE AÇÕES ANTES DO LOGIN
    // ========================================

    /**
     * Bloqueia movimento do jogador
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Permite movimento da cabeça, mas não do corpo
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (!authSystem.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);

            // Teleporta de volta para a posição inicial se tentar se mover muito
            Location spawn = spawnLocations.get(player.getUniqueId());
            if (spawn != null && spawn.distance(player.getLocation()) > 2) {
                player.teleport(spawn);
            }
        }
    }

    /**
     * Bloqueia comandos (exceto login/register)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Permite apenas comandos de autenticação
        if (command.startsWith("/login") ||
                command.startsWith("/register") ||
                command.startsWith("/logar") ||
                command.startsWith("/registrar") ||
                command.startsWith("/changepassword") ||
                command.startsWith("/trocarsenha")) {
            return;
        }

        if (!authSystem.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c§lERRO: §cVocê precisa se autenticar primeiro!");
            player.sendMessage("§eUse: §f/register <senha> [email]");
        }
    }

    /**
     * Bloqueia chat
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c§lERRO: §cVocê precisa fazer login para usar o chat!");
        }
    }

    /**
     * Bloqueia quebrar blocos
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia colocar blocos
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia interações (clicar em blocos, etc)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia abrir inventários
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (!authSystem.isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Bloqueia dropar itens
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia pegar itens
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (!authSystem.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia receber dano
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!authSystem.isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Bloqueia atacar outros jogadores/entidades
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (!authSystem.isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}