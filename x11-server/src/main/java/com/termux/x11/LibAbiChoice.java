package com.termux.x11;

import java.util.function.Predicate;

/**
 * Chooses the ABI folder {@code libXlorie.so} is loaded from. The loader runs at the bitness of
 * the app that installed it (see the launcher's {@code termux-x11} script), so the folder must
 * match the running process, not {@code Build.SUPPORTED_ABIS[0]}, which is the device's preference.
 */
final class LibAbiChoice {
    private LibAbiChoice() {}

    /**
     * @return the first ABI of the process's own bitness whose library exists, else the first of
     *         {@code all} whose library exists, else {@code null}.
     */
    static String choose(boolean is64Bit, String[] abis64, String[] abis32, String[] all,
                         Predicate<String> libraryExists) {
        String found = firstPresent(is64Bit ? abis64 : abis32, libraryExists);
        return found != null ? found : firstPresent(all, libraryExists);
    }

    private static String firstPresent(String[] abis, Predicate<String> libraryExists) {
        if (abis == null) return null;
        for (String abi : abis) {
            if (libraryExists.test(abi)) return abi;
        }
        return null;
    }
}
