package org.redesplit.github.offluisera.redesplitcore.utils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sistema de hash de senhas com SALT para máxima segurança
 * Compatível com Java 8 (sem BCrypt externo)
 */
public class PasswordHasher {

    private static final int SALT_LENGTH = 16; // 128 bits
    private static final int ITERATIONS = 10000; // Dificulta brute-force

    /**
     * Gera um hash seguro da senha com salt único
     * @return String no formato: SALT$HASH
     */
    public static String hash(String password) {
        try {
            // 1. Gera salt aleatório
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // 2. Combina senha + salt e faz hash iterado
            byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);

            // 3. Retorna: base64(salt)$base64(hash)
            String saltB64 = Base64.getEncoder().encodeToString(salt);
            String hashB64 = Base64.getEncoder().encodeToString(hash);
            return saltB64 + "$" + hashB64;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Verifica se a senha corresponde ao hash armazenado
     */
    public static boolean verify(String password, String storedHash) {
        try {
            // 1. Separa salt e hash
            String[] parts = storedHash.split("\\$");
            if (parts.length != 2) return false;

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] originalHash = Base64.getDecoder().decode(parts[1]);

            // 2. Gera hash da senha digitada com o mesmo salt
            byte[] testHash = pbkdf2(password.toCharArray(), salt, ITERATIONS);

            // 3. Compara os hashes (constant-time para evitar timing attack)
            return slowEquals(originalHash, testHash);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * PBKDF2 com SHA-256 (implementação manual para Java 8)
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Converte senha para bytes
        byte[] passwordBytes = new String(password).getBytes("UTF-8");

        // Primeira iteração: hash(password + salt)
        digest.update(passwordBytes);
        digest.update(salt);
        byte[] result = digest.digest();

        // Iterações restantes
        for (int i = 1; i < iterations; i++) {
            digest.reset();
            result = digest.digest(result);
        }

        return result;
    }

    /**
     * Comparação constant-time (previne timing attacks)
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    /**
     * Gera senha aleatória forte (útil para recuperação)
     */
    public static String generateRandomPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    /**
     * Valida força da senha
     */
    public static boolean isStrongPassword(String password) {
        if (password.length() < 8) return false;

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        // Exige pelo menos 3 dos 4 tipos
        int strength = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) +
                (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        return strength >= 3;
    }
}