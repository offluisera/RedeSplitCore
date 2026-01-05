package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

public class KeyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem ativar chaves.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUso correto: /ativar <codigo>");
            return true;
        }

        Player p = (Player) sender;
        String code = args[0]; // Não use toUpperCase aqui se o banco for case-sensitive, mas geralmente keys são UPPER.

        // Executa assincronamente para não travar o servidor
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Busca a chave
                PreparedStatement st = conn.prepareStatement("SELECT * FROM rs_keys WHERE code = ?");
                st.setString(1, code);
                ResultSet rs = st.executeQuery();

                if (rs.next()) {
                    int id = rs.getInt("id");
                    String rewardCmd = rs.getString("reward_cmd");
                    int maxUses = rs.getInt("max_uses");
                    int uses = rs.getInt("uses");
                    Timestamp expiresAt = rs.getTimestamp("expires_at");

                    // 2. Validações
                    if (uses >= maxUses) {
                        p.sendMessage("§cEsta chave já atingiu o limite máximo de usos.");
                        return;
                    }

                    if (expiresAt != null && expiresAt.before(new Timestamp(System.currentTimeMillis()))) {
                        p.sendMessage("§cEsta chave expirou.");
                        return;
                    }

                    // 3. Atualiza o uso (+1)
                    PreparedStatement updateSt = conn.prepareStatement("UPDATE rs_keys SET uses = uses + 1 WHERE id = ?");
                    updateSt.setInt(1, id);
                    updateSt.executeUpdate();
                    updateSt.close();

                    // 4. Entrega a recompensa (Na Main Thread)
                    String finalCmd = rewardCmd.replace("%player%", p.getName());

                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                        p.sendMessage("§a§lSUCESSO! §aChave ativada e recompensa recebida.");
                        // Opcional: Efeito sonoro ou visual
                        // p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    });

                } else {
                    p.sendMessage("§cCódigo inválido ou inexistente.");
                }

                rs.close();
                st.close();

            } catch (Exception e) {
                p.sendMessage("§cErro ao processar chave. Contate um admin.");
                e.printStackTrace();
            }
        });

        return true;
    }
}