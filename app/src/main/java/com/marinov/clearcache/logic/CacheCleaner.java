package com.marinov.clearcache.logic;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CacheCleaner {
    private static final String TAG = "CacheCleaner";

    @NonNull
    public static DataOutputStream getDataOutputStream(Process su, Set<String> excludedPackages) throws IOException {
        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        os.writeBytes("for pkg in $(pm list packages | sed 's/^package://'); do\n");
        StringBuilder command = new StringBuilder("  if [ \"$pkg\" != \"com.marinov.clearcache\" ] "
                + "&& [ \"$pkg\" != \"com.android.systemui\" ]");
        if (excludedPackages != null) {
            for (String pkg : excludedPackages) {
                if (pkg != null && pkg.matches("^[a-zA-Z0-9._-]+$")) {
                    command.append(" && [ \"$pkg\" != \"").append(pkg).append("\" ]");
                }
            }
        }
        command.append(" ; then\n");
        os.writeBytes(command.toString());
        os.writeBytes("    am force-stop \"$pkg\"\n");
        os.writeBytes("  fi\n");
        os.writeBytes("done\n");
        os.writeBytes("pm trim-caches 9999999999999\n");
        os.writeBytes("rm -rf /storage/emulated/0/Movies/.thumbnails\n");
        os.writeBytes("rm -rf /storage/emulated/0/Music/.thumbnails\n");
        os.writeBytes("rm -rf /storage/emulated/0/Pictures/.thumbnails\n");
        os.writeBytes("exit\n");
        return os;
    }

    public static void executeCleanCacheSilent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> excludedPackages = prefs.getStringSet(PrefsConstants.KEY_EXCLUDED_PACKAGES, new HashSet<>());
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = getDataOutputStream(su, excludedPackages);
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Erro na limpeza silenciosa", e);
            }
        }).start();
    }
}