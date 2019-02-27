package it.infuse.jenkins.usemango.util;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.prefs.Preferences;
import java.nio.charset.StandardCharsets;

/*
* This class has been developed using various examples online on how to interact with Windows Registry.
* It uses the reflection concept in Java to access methods of the private java.util.prefs.WindowsPreferences class
* and extending those methods into java.util.prefs.Preferences class.
* The reason it has been implemented this way is because Java provides a way of interacting with registry using Preferences
* but that localises the access to HKEY\Software\JavaSoft\Prefs so accessing other Keys at different path becomes difficult.
* This class can be extended even further to include other Registry operation for now it only reads a single key value.
* */
public class WinRegistry {
    public static final int HKEY_CURRENT_USER = 0x80000001;
    public static final int HKEY_LOCAL_MACHINE = 0x80000002;
    public static final int REG_SUCCESS = 0;

    private static final int KEY_READ = 0x20019;
    private static final Preferences userRoot = Preferences.userRoot();
    private static final Preferences systemRoot = Preferences.systemRoot();
    private static final Class<? extends Preferences> userClass = userRoot.getClass();
    private static final Method regOpenKey;
    private static final Method regCloseKey;
    private static final Method regQueryValueEx;

    static {
        try {
            regOpenKey = userClass.getDeclaredMethod("WindowsRegOpenKey",
                    new Class[] { int.class, byte[].class, int.class });
            regOpenKey.setAccessible(true);
            regCloseKey = userClass.getDeclaredMethod("WindowsRegCloseKey",
                    new Class[] { int.class });
            regCloseKey.setAccessible(true);
            regQueryValueEx = userClass.getDeclaredMethod("WindowsRegQueryValueEx",
                    new Class[] { int.class, byte[].class });
            regQueryValueEx.setAccessible(true);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WinRegistry() {  }

    /**
     * Read a value from key and value name
     * @param hkey   HKEY_CURRENT_USER/HKEY_LOCAL_MACHINE
     * @param key
     * @param valueName
     * @return the value
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws UnsupportedEncodingException
     */
    public static String readString(int hkey, String key, String valueName)
            throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, UnsupportedEncodingException
    {
        if (hkey == HKEY_LOCAL_MACHINE) {
            return readString(systemRoot, hkey, key, valueName);
        }
        else if (hkey == HKEY_CURRENT_USER) {
            return readString(userRoot, hkey, key, valueName);
        }
        else {
            throw new IllegalArgumentException("hkey=" + hkey);
        }
    }

    private static String readString(Preferences root, int hkey, String key, String value)
            throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, UnsupportedEncodingException
    {
        int[] handles = (int[]) regOpenKey.invoke(root, new Object[] {
                Integer.valueOf(hkey), toCstr(key), Integer.valueOf(KEY_READ) });
        if (handles[1] != REG_SUCCESS) {
            return null;
        }
        byte[] valb = (byte[]) regQueryValueEx.invoke(root, new Object[] {
                Integer.valueOf(handles[0]), toCstr(value) });
        regCloseKey.invoke(root, new Object[] { Integer.valueOf(handles[0]) });
        return (valb != null ? new String(valb, StandardCharsets.UTF_8.name()).trim() : null);
    }

    // utility
    private static byte[] toCstr(String str) {
        byte[] result = new byte[str.length() + 1];

        for (int i = 0; i < str.length(); i++) {
            result[i] = (byte) str.charAt(i);
        }
        result[str.length()] = 0;
        return result;
    }
}