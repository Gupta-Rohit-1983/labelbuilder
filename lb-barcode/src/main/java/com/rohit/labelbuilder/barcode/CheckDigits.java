package com.rohit.labelbuilder.barcode;

/**
 * Check-digit arithmetic (Phase 10c), computed here rather than only inside the encoder so the
 * inspector can show the digit a value will get, and so a wrong one can be reported with a specific
 * message instead of the encoder's generic rejection.
 *
 * <p>Pure and dependency-free.
 */
public final class CheckDigits {

    private static final String CODE39_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";

    private CheckDigits() {}

    /**
     * The GS1 modulo-10 check digit used by EAN-13, EAN-8, UPC-A, UPC-E and ITF-14.
     *
     * <p>Digits are weighted 3 and 1 alternately <b>from the right</b>, which is why the weighting
     * depends on the value's length rather than being fixed per position.
     *
     * @param digits the value without its check digit
     * @throws BarcodeException if the value is not all digits
     */
    public static int modulo10(String digits) {
        requireDigits(digits);
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    /** True when {@code value}'s last digit is the correct modulo-10 check digit for the rest. */
    public static boolean hasValidModulo10(String value) {
        if (value == null || value.length() < 2) {
            return false;
        }
        try {
            String body = value.substring(0, value.length() - 1);
            int expected = modulo10(body);
            return value.charAt(value.length() - 1) - '0' == expected;
        } catch (BarcodeException e) {
            return false;
        }
    }

    /**
     * The Code 39 modulo-43 check character, over its 43-character alphabet.
     *
     * @throws BarcodeException if the value contains a character Code 39 cannot encode
     */
    public static char modulo43(String value) {
        if (value == null) {
            throw new BarcodeException("value is required");
        }
        int sum = 0;
        for (char c : value.toCharArray()) {
            int index = CODE39_ALPHABET.indexOf(c);
            if (index < 0) {
                throw new BarcodeException("'" + c + "' cannot be encoded as Code 39");
            }
            sum += index;
        }
        return CODE39_ALPHABET.charAt(sum % 43);
    }

    private static void requireDigits(String value) {
        if (value == null || value.isEmpty()) {
            throw new BarcodeException("a numeric value is required");
        }
        for (char c : value.toCharArray()) {
            if (c < '0' || c > '9') {
                throw new BarcodeException("expected digits only, found '" + c + "' in \"" + value + "\"");
            }
        }
    }
}
