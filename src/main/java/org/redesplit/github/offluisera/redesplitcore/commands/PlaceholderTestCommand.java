package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.api.PlaceholderAPI;

/**
 * Comando para testar as placeholders do RedeSplitCore
 * Uso: /phtest [placeholder] ou /phtest list
 */
public class PlaceholderTestCommand implements CommandExecutor {

    private final PlaceholderAPI placeholderAPI;

    public PlaceholderTestCommand() {
        this.placeholderAPI = RedeSplitCore.getPlaceholderAPI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;

        // Sem argumentos - mostra menu de ajuda
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        // Lista todas as placeholders
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("lista")) {
            listAllPlaceholders(player);
            return true;
        }

        // Testa todas as placeholders
        if (args[0].equalsIgnoreCase("all") || args[0].equalsIgnoreCase("todas")) {
            testAllPlaceholders(player);
            return true;
        }

        // Testa uma placeholder específica
        testSpecificPlaceholder(player, args[0]);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage("§8§m-------------------§r §6§lPlaceholder Test §8§m-------------------");
        player.sendMessage("");
        player.sendMessage("§e/phtest list §7- Lista todas as placeholders disponíveis");
        player.sendMessage("§e/phtest all §7- Testa todas as placeholders");
        player.sendMessage("§e/phtest <placeholder> §7- Testa uma placeholder específica");
        player.sendMessage("");
        player.sendMessage("§7Exemplos:");
        player.sendMessage("§e  /phtest splitcore_rank");
        player.sendMessage("§e  /phtest splitcore_money");
        player.sendMessage("");
        player.sendMessage("§8§m-----------------------------------------------------");
        player.sendMessage("");
    }

    private void listAllPlaceholders(Player player) {
        player.sendMessage("");
        player.sendMessage("§8§m-------------------§r §6§lPlaceholders §8§m-------------------");
        player.sendMessage("");

        player.sendMessage("§6§l👑 RANK");
        player.sendMessage("§e  %splitcore_rank% §7- Rank colorido");
        player.sendMessage("§e  %splitcore_rank_name% §7- Nome do rank");
        player.sendMessage("§e  %splitcore_rank_color% §7- Cor do rank");
        player.sendMessage("§e  %splitcore_rank_prefix% §7- Prefixo do rank");
        player.sendMessage("§e  %splitcore_rank_suffix% §7- Sufixo do rank");
        player.sendMessage("");

        player.sendMessage("§6§l👤 JOGADOR");
        player.sendMessage("§e  %splitcore_player% §7- Nome do jogador");
        player.sendMessage("§e  %splitcore_displayname% §7- Display name");
        player.sendMessage("§e  %splitcore_uuid% §7- UUID do jogador");
        player.sendMessage("");

        player.sendMessage("§6§l💰 ECONOMIA");
        player.sendMessage("§e  %splitcore_money% §7- Dinheiro formatado");
        player.sendMessage("§e  %splitcore_money_raw% §7- Dinheiro sem formatação");
        player.sendMessage("§e  %splitcore_cash% §7- Cash formatado");
        player.sendMessage("§e  %splitcore_cash_raw% §7- Cash sem formatação");
        player.sendMessage("");

        player.sendMessage("§6§l📊 ESTATÍSTICAS");
        player.sendMessage("§e  %splitcore_playtime% §7- Tempo de jogo");
        player.sendMessage("§e  %splitcore_playtime_hours% §7- Horas jogadas");
        player.sendMessage("§e  %splitcore_playtime_raw% §7- Segundos totais");
        player.sendMessage("§e  %splitcore_firstjoin% §7- Primeiro login");
        player.sendMessage("§e  %splitcore_lastjoin% §7- Último login");
        player.sendMessage("");

        player.sendMessage("§6§l🏅 MEDALHAS");
        player.sendMessage("§e  %splitcore_medal% §7- Medalha atual");
        player.sendMessage("§e  %splitcore_medals_count% §7- Total de medalhas");
        player.sendMessage("");

        player.sendMessage("§6§l👥 REFERRAL");
        player.sendMessage("§e  %splitcore_referrer% §7- Quem indicou");
        player.sendMessage("§e  %splitcore_referrals% §7- Quantos indicou");
        player.sendMessage("");

        player.sendMessage("§6§l💬 DISCORD");
        player.sendMessage("§e  %splitcore_discord% §7- Status vinculação");
        player.sendMessage("§e  %splitcore_discord_id% §7- ID do Discord");
        player.sendMessage("");

        player.sendMessage("§6§l⚙️ STATUS");
        player.sendMessage("§e  %splitcore_vanish% §7- Status vanish");
        player.sendMessage("§e  %splitcore_authenticated% §7- Autenticado");
        player.sendMessage("");

        player.sendMessage("§6§l🌐 SERVIDOR");
        player.sendMessage("§e  %splitcore_server% §7- ID do servidor");
        player.sendMessage("");

        player.sendMessage("§8§m-----------------------------------------------------");
        player.sendMessage("");
    }

    private void testSpecificPlaceholder(Player player, String placeholder) {
        // Remove % se o usuário incluiu
        placeholder = placeholder.replace("%", "");

        // Adiciona o prefixo se não tiver
        if (!placeholder.startsWith("splitcore_")) {
            placeholder = "splitcore_" + placeholder;
        }

        String result = placeholderAPI.parsePlaceholder(player, placeholder);

        player.sendMessage("");
        player.sendMessage("§8§m-------------------§r §6§lTeste §8§m-------------------");
        player.sendMessage("");

        if (result != null) {
            player.sendMessage("§7Placeholder: §e%" + placeholder + "%");
            player.sendMessage("§7Resultado: " + result);
            player.sendMessage("");
            player.sendMessage("§a✓ Placeholder encontrada e processada com sucesso!");
        } else {
            player.sendMessage("§7Placeholder: §e%" + placeholder + "%");
            player.sendMessage("");
            player.sendMessage("§c✗ Placeholder não encontrada!");
            player.sendMessage("§7Use §e/phtest list §7para ver todas disponíveis.");
        }

        player.sendMessage("");
        player.sendMessage("§8§m-------------------------------------------");
        player.sendMessage("");
    }

    private void testAllPlaceholders(Player player) {
        player.sendMessage("");
        player.sendMessage("§8§m-------------------§r §6§lTeste Completo §8§m-------------------");
        player.sendMessage("");

        String[] placeholders = {
                "splitcore_rank",
                "splitcore_rank_name",
                "splitcore_rank_color",
                "splitcore_rank_prefix",
                "splitcore_player",
                "splitcore_displayname",
                "splitcore_money",
                "splitcore_cash",
                "splitcore_playtime",
                "splitcore_firstjoin",
                "splitcore_lastjoin",
                "splitcore_medal",
                "splitcore_medals_count",
                "splitcore_referrer",
                "splitcore_referrals",
                "splitcore_discord",
                "splitcore_discord_id",
                "splitcore_vanish",
                "splitcore_authenticated",
                "splitcore_server"
        };

        int sucessos = 0;
        int falhas = 0;

        for (String ph : placeholders) {
            String result = placeholderAPI.parsePlaceholder(player, ph);

            if (result != null) {
                player.sendMessage("§a✓ §7%" + ph + "% §f→ " + result);
                sucessos++;
            } else {
                player.sendMessage("§c✗ §7%" + ph + "% §f→ §cNULL");
                falhas++;
            }
        }

        player.sendMessage("");
        player.sendMessage("§7Total: §e" + placeholders.length + " §7placeholders");
        player.sendMessage("§aSuccessos: " + sucessos + " §7| §cFalhas: " + falhas);
        player.sendMessage("");
        player.sendMessage("§8§m-----------------------------------------------------");
        player.sendMessage("");
    }
}