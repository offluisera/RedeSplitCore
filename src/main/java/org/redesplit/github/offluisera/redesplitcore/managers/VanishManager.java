package org.redesplit.github.offluisera.redesplitcore.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishManager {

    private final Set<UUID> vanished = new HashSet<>();
    private final RedeSplitCore plugin;

    public VanishManager(RedeSplitCore plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public void toggleVanish(Player player) {
        if (isVanished(player)) {
            // Ficar Visível
            vanished.remove(player.getUniqueId());
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(player);
            }
            player.sendMessage("§a§lVANISH: §aVocê está visível novamente.");

            // Mensagem fake de entrada (opcional)
            Bukkit.broadcastMessage("§e" + player.getName() + " entrou no jogo.");
        } else {
            // Ficar Invisível
            vanished.add(player.getUniqueId());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("redesplit.vanish.see")) {
                    p.hidePlayer(player);
                }
            }
            player.sendMessage("§a§lVANISH: §aVocê agora está invisível.");

            // Mensagem fake de saída
            Bukkit.broadcastMessage("§e" + player.getName() + " saiu do jogo.");
        }
    }

    // Esconde os vanished para quem acabou de entrar
    public void hideVanishedFrom(Player newPlayer) {
        if (!newPlayer.hasPermission("redesplit.vanish.see")) {
            for (UUID uuid : vanished) {
                Player v = Bukkit.getPlayer(uuid);
                if (v != null) {
                    newPlayer.hidePlayer(v);
                }
            }
        }
    }
}