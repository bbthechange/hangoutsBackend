package com.bbthechange.inviter.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Utility class for generating unique invite codes.
 */
public class InviteCodeGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generate a random invite code.
     * Format: 8 characters, lowercase alphanumeric (e.g., "a4x7b9k2")
     *
     * @return A randomly generated invite code
     */
    public static String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        return code.toString();
    }

    /**
     * Generate a unique invite code by checking against existing codes.
     * Loops until a code is generated that doesn't exist.
     *
     * With 36^8 = 2.8 trillion possible codes, collision probability is negligible.
     *
     * @param existsChecker Function that returns true if a code already exists
     * @return A unique invite code
     */
    public static String generateUnique(Predicate<String> existsChecker) {
        String code;
        do {
            code = generate();
        } while (existsChecker.test(code));
        return code;
    }
}
