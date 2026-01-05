package org.redesplit.github.offluisera.redesplitcore.player;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class PermissionInjector {

    // Mapa para guardar o "anexo" de permissões de cada jogador
    private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();

    /**
     * Remove as permissões antigas e injeta as novas.
     */
    public void inject(Player player, Set<String> permissions) {
        // 1. Limpa injeção antiga se existir para não duplicar
        uninject(player);

        // 2. Cria um novo anexo de permissões vinculado ao nosso plugin
        PermissionAttachment attachment = player.addAttachment(RedeSplitCore.getInstance());

        // 3. Adiciona cada permissão da lista (vinda do MySQL)
        for (String perm : permissions) {
            attachment.setPermission(perm, true);
        }

        // 4. Salva o anexo no mapa para podermos remover depois
        attachments.put(player.getUniqueId(), attachment);
    }

    /**
     * Remove as permissões do jogador (usado ao sair ou trocar de rank).
     */
    public void uninject(Player player) {
        if (attachments.containsKey(player.getUniqueId())) {
            try {
                player.removeAttachment(attachments.remove(player.getUniqueId()));
            } catch (IllegalArgumentException ex) {
                // Ignora erro se o player já saiu
            }
        }
    }
}