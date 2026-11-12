package studio.realframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Settings stored in prefs; API tokens encrypted with AndroidKeyStore (not backed up). */
public final class SecretVault {
    private final SharedPreferences prefs;
    private static final String ALIAS = "realframe_secret_key_v1";
    public SecretVault(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }
    public String get(String key, String defaultValue) { return prefs.getString(key, defaultValue); }
    public void set(String key, String value) { prefs.edit().putString(key, value).apply(); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            gen.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    public void setToken(String name, String token) throws Exception {
        if (token == null || token.isEmpty()) {
            prefs.edit().remove("secret_" + name).apply(); return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String serialized = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(encrypted, Base64.NO_WRAP);
        prefs.edit().putString("secret_" + name, serialized).apply();
    }
    public String token(String name) {
        try {
            String value = prefs.getString("secret_" + name, "");
            if (value.isEmpty()) return "";
            String[] parts = value.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }
}
