package org.redesplit.github.offluisera.redesplitcore.managers;

import org.bukkit.ChatColor;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MotdManager {

    private static String line1 = "&6&lREDE SPLIT";
    private static String line2 = "&eCarregando...";

    public static void setMotd(String l1, String l2) {
        line1 = l1;
        line2 = l2;
    }

    public static String getFormattedMotd() {
        return ChatColor.translateAlternateColorCodes('&', line1 + "\n" + line2);
    }

    public static void loadFromSql() {
        try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT line1, line2 FROM rs_motd WHERE id = 1");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                line1 = rs.getString("line1");
                line2 = rs.getString("line2");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}