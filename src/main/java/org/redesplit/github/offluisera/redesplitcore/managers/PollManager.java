package org.redesplit.github.offluisera.redesplitcore.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import redis.clients.jedis.Jedis;

import java.util.*;

public class PollManager {

    private static boolean active = false;
    private static String question;
    private static List<String> options;
    private static Map<Integer, Integer> votes; // ID da Opção -> Quantidade de Votos
    private static Set<UUID> votedPlayers;
    private static int pollIdSql; // ID do MySQL para salvar o vencedor depois

    public static void startPoll(int id, String quest, List<String> opts) {
        pollIdSql = id;
        question = quest;
        options = opts;
        votes = new HashMap<>();
        votedPlayers = new HashSet<>();
        active = true;

        // Inicializa contadores
        for (int i = 0; i < options.size(); i++) {
            votes.put(i + 1, 0); // Opção 1, 2, 3...
        }

        // Reseta o Redis para o site ler limpo
        updateRedis();

        // Anúncio Global
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§e§lNOVA ENQUETE INICIADA!");
        Bukkit.broadcastMessage("§fPerguta: §a" + question);
        Bukkit.broadcastMessage("");
        for (int i = 0; i < options.size(); i++) {
            // JSON Click event seria ideal aqui, mas vamos de texto simples para compatibilidade
            Bukkit.broadcastMessage("§6Digite §f/votar " + (i + 1) + " §6para: §e" + options.get(i));
        }
        Bukkit.broadcastMessage("");
    }

    public static void vote(Player p, int option) {
        if (!active) {
            p.sendMessage("§cNenhuma enquete ativa no momento.");
            return;
        }
        if (votedPlayers.contains(p.getUniqueId())) {
            p.sendMessage("§cVocê já votou nesta enquete!");
            return;
        }
        if (!votes.containsKey(option)) {
            p.sendMessage("§cOpção inválida.");
            return;
        }

        // Computa voto
        votes.put(option, votes.get(option) + 1);
        votedPlayers.add(p.getUniqueId());

        p.sendMessage("§aVoto computado com sucesso!");
        p.playSound(p.getLocation(), org.bukkit.Sound.valueOf("ORB_PICKUP"), 1f, 1f);

        // Atualiza o Redis para o site ver em tempo real
        updateRedis();
    }

    public static void stopPoll() {
        if (!active) return;

        active = false;
        Bukkit.broadcastMessage("§e§lENQUETE ENCERRADA!");
        Bukkit.broadcastMessage("§fObrigado a todos que votaram.");

        // Aqui você poderia salvar o resultado final no MySQL via Async
    }

    // Envia o estado atual para o Redis (Chave: poll:live)
    private static void updateRedis() {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Jedis jedis = new Jedis("82.39.107.62", 6379)) {
                jedis.auth("UHAFDjbnakfye@@jouiayhfiqwer903");

                // Formato JSON simples para o PHP ler fácil: {"1": 10, "2": 5}
                StringBuilder json = new StringBuilder("{");
                for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
                    json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue()).append(",");
                }
                if (json.length() > 1) json.setLength(json.length() - 1); // Remove última vírgula
                json.append("}");

                jedis.set("poll:live", json.toString());
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}