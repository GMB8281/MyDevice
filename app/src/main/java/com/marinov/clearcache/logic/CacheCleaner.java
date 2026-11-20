package com.marinov.clearcache.logic;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class CacheCleaner {

    private static final String TAG = "CacheCleaner";

    /**
     * Conjunto completo de pacotes protegidos contra o force-stop:
     * exclusões manuais do usuário + apps padrão / SystemUI / este app
     * + apps "não otimizados" na bateria (verificados AO VIVO a cada limpeza,
     * incluindo os marcados em Always-on — nenhum registro local envolvido).
     * Deve ser chamado fora da main thread.
     */
    @NonNull
    public static Set<String> getProtectedPackages(Context context, Set<String> excludedPackages) {
        Set<String> protectedPackages = new TreeSet<>();
        if (excludedPackages != null) {
            for (String pkg : excludedPackages) {
                if (pkg != null) protectedPackages.add(pkg);
            }
        }
        protectedPackages.addAll(DefaultAppsHelper.getMandatoryExcludedPackages(context));
        protectedPackages.addAll(BatteryOptimizationHelper.getNotOptimizedPackages(context));
        return protectedPackages;
    }

    @NonNull
    public static DataOutputStream getDataOutputStream(Context context, Process su, Set<String> excludedPackages) throws IOException {
        Set<String> protectedPackages = getProtectedPackages(context, excludedPackages);

        // Padrão glob do case: os pacotes são validados por regex, então '.', '_' e '-'
        // são literais e não há caracteres especiais de glob (*, ?, [).
        StringBuilder skipPattern = new StringBuilder();
        for (String pkg : protectedPackages) {
            if (pkg != null && pkg.matches("^[a-zA-Z0-9._-]+$")) {
                if (skipPattern.length() > 0) skipPattern.append('|');
                skipPattern.append(pkg);
            }
        }

        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        os.writeBytes("for pkg in $(pm list packages | sed 's/^package://'); do\n");
        os.writeBytes("  case \"$pkg\" in\n");
        if (skipPattern.length() > 0) {
            os.writeBytes("    " + skipPattern + ") ;;\n");
        }
        os.writeBytes("    *) am force-stop \"$pkg\" ;;\n");
        os.writeBytes("  esac\n");
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
                DataOutputStream os = getDataOutputStream(context, su, excludedPackages);
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Erro na limpeza silenciosa", e);
            }
        }).start();
    }
}