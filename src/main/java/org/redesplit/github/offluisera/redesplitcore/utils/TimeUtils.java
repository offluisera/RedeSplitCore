package org.redesplit.github.offluisera.redesplitcore.utils;

public class TimeUtils {

    // Converte string (10m, 1h, 1d) para minutos
    public static long parseTime(String input) {
        if (input.equals("0")) return 0; // Permanente

        String unit = input.substring(input.length() - 1).toLowerCase();
        long value;
        try {
            value = Long.parseLong(input.substring(0, input.length() - 1));
        } catch (NumberFormatException e) {
            return -1; // Formato inválido
        }

        switch (unit) {
            case "m": return value;           // Minutos
            case "h": return value * 60;      // Horas
            case "d": return value * 1440;    // Dias
            default: return -1;
        }
    }

    // Formata minutos para texto bonito
    public static String formatTime(long minutes) {
        if (minutes == 0) return "Permanente";
        if (minutes < 60) return minutes + " minutos";
        if (minutes < 1440) {
            long hours = minutes / 60;
            return hours + " horas";
        }
        long days = minutes / 1440;
        return days + " dias";
    }
}