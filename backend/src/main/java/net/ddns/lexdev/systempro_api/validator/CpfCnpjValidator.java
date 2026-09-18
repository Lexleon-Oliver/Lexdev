package net.ddns.lexdev.systempro_api.validator;

import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;

public final class CpfCnpjValidator {

    private CpfCnpjValidator() {
    }

    public static boolean isValidCpf(String value) {

        String cpf = CpfCnpjNormalizer.normalize(value);

        if (cpf == null || cpf.length() != 11) {
            return false;
        }

        if (isRepeatedDigits(cpf)) {
            return false;
        }

        int firstDigit = calculateCpfDigit(cpf, 9);
        int secondDigit = calculateCpfDigit(cpf, 10);

        return Character.getNumericValue(cpf.charAt(9)) == firstDigit
            && Character.getNumericValue(cpf.charAt(10)) == secondDigit;
    }

    public static boolean isValidCnpj(String value) {

        String cnpj = CpfCnpjNormalizer.normalize(value);

        if (cnpj == null || cnpj.length() != 14) {
            return false;
        }

        if (isRepeatedDigits(cnpj)) {
            return false;
        }

        int firstDigit = calculateCnpjDigit(cnpj, 12);
        int secondDigit = calculateCnpjDigit(cnpj, 13);

        return Character.getNumericValue(cnpj.charAt(12)) == firstDigit
            && Character.getNumericValue(cnpj.charAt(13)) == secondDigit;
    }

    private static int calculateCpfDigit(
        String cpf,
        int position
    ) {
        int length = position;
        int sum = 0;

        for (int i = 0; i < length; i++) {
            int digit =
                Character.getNumericValue(cpf.charAt(i));

            sum += digit * (length + 1 - i);
        }

        int remainder = sum % 11;

        return remainder < 2
            ? 0
            : 11 - remainder;
    }

    private static int calculateCnpjDigit(
        String cnpj,
        int position
    ) {
        int sum = 0;
        int weight = position == 12 ? 5 : 6;

        for (int i = 0; i < position; i++) {

            int digit =
                Character.getNumericValue(cnpj.charAt(i));

            sum += digit * weight;

            weight--;

            if (weight == 1) {
                weight = 9;
            }
        }

        int remainder = sum % 11;

        return remainder < 2
            ? 0
            : 11 - remainder;
    }

    private static boolean isRepeatedDigits(String value) {

        char first =
            value.charAt(0);

        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != first) {
                return false;
            }
        }

        return true;
    }
}
