package net.typeblog.shelter.util;

import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Array;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Opening access to actions across the profile boundary poses a security risk
// The risk is that other applications might also be able to start our activities
// through system's IntentForwarderActivity
// That activity runs in the system process, thus normal limitations like "permissions"
// and "exported" will not work.
// This class tries to fix it by appending a timestamp and a signature of the timestamp
// to our own Intents sent through the boundary, ensuring that only Shelter can invoke
// its high-privilege functions across that boundary, assuming that no other application
// would be able to access Shelter's internal storage to gain access to the private key.
// The private key is generated the first time this class is used, and then shared
// across the profile boundary. Shelter will always trust the first key it receives.
public class AuthenticationUtility {
    public static void signIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) {
            // Generate the key if we don't have one yet
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
                keyGen.init(256);
                key = bytesToHex(keyGen.generateKey().getEncoded());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("WTF?");
            }

            LocalStorageManager.getInstance().setString(
                    LocalStorageManager.PREF_AUTH_KEY, key);

            // If this is the first time, we just send the key to the other side
            intent.putExtra("auth_key", key);
        } else {
            long timestamp = new Date().getTime();
            intent.putExtra("timestamp", timestamp);
            intent.putExtra("signature", sign(key, intentToString(intent)));
        }
    }

    public static boolean checkIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (key == null) {
            // If we haven't got a key yet, we just take the key sent by the other side
            // If not, NEVER receive any key because it can be fake.
            // We only trust the first key we receive
            if (intent.hasExtra("auth_key")) {
                LocalStorageManager.getInstance().setString(
                        LocalStorageManager.PREF_AUTH_KEY, intent.getStringExtra("auth_key"));
                return true;
            } else {
                // We haven't got a key, and we can't check if it is true or not.
                return false;
            }
        } else {
            long timestamp = new Date().getTime();
            long intentTimestamp = intent.getLongExtra("timestamp", 0);
            String signature = intent.getStringExtra("signature");
            intent.removeExtra("signature"); // We don't include the signature itself while checking
            return timestamp - intentTimestamp < 30 * 1000 &&
                    sign(key, intentToString(intent)).equals(signature);
        }
    }

    public static void reset() {
        LocalStorageManager.getInstance().remove(LocalStorageManager.PREF_AUTH_KEY);
    }

    private static String sign(String hexKey, String serializedString) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return bytesToHex(mac.doFinal(serializedString.getBytes()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("WTF?");
        }
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    private static byte[] hexStringToByteArray(String s) {
        try {
            int len = s.length();
            if (len > 1) {
                byte[] data = new byte[len / 2];
                for (int i = 0 ; i < len ; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16));
                }
                return data;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A deterministic way to convert an Intent into a String
    private static String intentToString(Intent intent) {
        // Read all the extras as "key,value" first
        Bundle intentExtras = intent.getExtras();
        List<String> extras = new ArrayList<>();
        for (String key : intentExtras.keySet()) {
            Object obj = intentExtras.get(key);

            // Sign all primitive-typed and primitive-array-typed extras
            if (isPrimitiveType(obj.getClass())) {
                extras.add(key + "," + obj);
            } else if (isPrimitiveArray(obj.getClass())) {
                extras.add(key + "," + primitiveArrayToString(obj));
            }
        }

        // Sort all the extras alphabetically
        extras.sort(Comparator.naturalOrder());

        // Collapse all extras into one string and append it after the action
        return intent.getAction() + ";" + extras.stream().collect(Collectors.joining(";"));
    }

    private static boolean isPrimitiveType(Class<?> clazz) {
        return clazz == Integer.class || clazz == Long.class || clazz == Float.class || clazz == Double.class
                || clazz == Short.class || clazz == Byte.class || clazz == Character.class
                || clazz == String.class || clazz == Boolean.class
                || clazz == int.class || clazz == long.class || clazz == float.class
                || clazz == double.class || clazz == short.class || clazz == byte.class
                || clazz == char.class || clazz == boolean.class;
    }

    private static boolean isPrimitiveArray(Class<?> clazz) {
        return clazz.isArray() && isPrimitiveType(clazz.getComponentType());
    }

    private static String primitiveArrayToString(Object array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Array.getLength(array); i++) {
            sb.append(Array.get(array, i));
            sb.append("|");
        }
        return sb.toString();
    }
}
