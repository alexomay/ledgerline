package mx.alexomay.ledgerline.account;

import java.security.SecureRandom;

/**
 * CLABE interbancaria: 3 dígitos de banco, 3 de plaza, 11 de cuenta y un
 * dígito de control. El dígito de control se calcula multiplicando los 17
 * primeros dígitos por los pesos 3, 7, 1 (repetidos), sumando el último dígito
 * de cada producto y tomando (10 - suma mod 10) mod 10.
 */
public final class Clabe {

    private static final int[] WEIGHTS = {3, 7, 1};
    private static final SecureRandom RANDOM = new SecureRandom();

    private Clabe() {}

    public static int checkDigit(String first17) {
        if (first17 == null || !first17.matches("\\d{17}")) {
            throw new IllegalArgumentException("Se esperaban 17 dígitos");
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += ((first17.charAt(i) - '0') * WEIGHTS[i % 3]) % 10;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValid(String clabe) {
        return clabe != null
                && clabe.matches("\\d{18}")
                && checkDigit(clabe.substring(0, 17)) == clabe.charAt(17) - '0';
    }

    public static String generate(String bankCode, String branchCode) {
        StringBuilder sb = new StringBuilder(bankCode).append(branchCode);
        for (int i = 0; i < 11; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.append(checkDigit(sb.toString())).toString();
    }
}
