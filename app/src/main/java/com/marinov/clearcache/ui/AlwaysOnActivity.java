package com.marinov.clearcache.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import com.marinov.clearcache.R;
import com.marinov.clearcache.data.AppInfo;
import com.marinov.clearcache.logic.AlwaysOnHelper;
import com.marinov.clearcache.logic.BatteryOptimizationHelper;
import com.marinov.clearcache.logic.DefaultAppsHelper;
import com.marinov.clearcache.logic.DeviceAdminHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Always-on apps: gerencia a isenção de otimização de bateria direto no sistema.
 * - Lista SOMENTE apps instalados pelo usuário (modo estrito: nada que veio na
 *   imagem do sistema, mesmo que tenha sido atualizado pela Play Store);
 * - Marcados = estado REAL do sistema (mesma lógica da limpeza de cache), sem registro local;
 * - Bloqueados (cinza): admins do dispositivo ativos + discador padrão + app de SMS padrão,
 *   que o Android mantém como "não otimizado" e não permite alterar;
 * - Sem botão de salvar: cada alteração é aplicada na hora via root e verificada;
 * - Falhou? O checkbox volta ao estado real do sistema e um toast explica o motivo.
 */
public class AlwaysOnActivity extends BaseAppListActivity {

    // Fila única: garante que toques rápidos (inclusive no mesmo app) sejam aplicados em ordem.
    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Remove o registro salvo por versões antigas (este contexto não guarda mais nada).
        AlwaysOnHelper.clearLegacyRecord(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLayoutRes() { return R.layout.activity_always_on; }

    @Override
    protected int getTitleRes() { return R.string.always_on_title; }

    @Override
    protected int getSubtitleRes() { return R.string.always_on_subtitle; }

    // Sem getSaveButtonRes(): FAB ocultado, salvamento automático por alteração.

    /**
     * MODO ESTRITO: somente apps que não vieram na imagem do sistema.
     * Para incluir também apps de sistema atualizados pelo usuário (ex.: Chrome via
     * Play Store), troque o retorno por:
     *   boolean updatedByUser = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
     *   return !isSystem || updatedByUser;
     */
    @Override
    protected boolean shouldIncludeApp(ApplicationInfo app, PackageManager pm) {
        boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        return !isSystem;
    }

    @Override
    protected Set<String> loadCheckedPackages() {
        // Varredura ao vivo dos apps irrestritos, a cada abertura da tela.
        return BatteryOptimizationHelper.getNotOptimizedPackages(this);
    }

    @Override
    protected Set<String> loadLockedPackages() {
        // Android auto-marca como "não otimizado" e não deixa alterar:
        // administradores do dispositivo ativos + discador padrão + app de SMS padrão.
        // (Browser, assistente de voz e launcher NÃO entram aqui.)
        Set<String> locked = new HashSet<>(DeviceAdminHelper.getActiveAdminPackages(this));
        String dialer = DefaultAppsHelper.getDefaultDialerPackage(this);
        if (dialer != null) locked.add(dialer);
        String sms = DefaultAppsHelper.getDefaultSmsPackage(this);
        if (sms != null) locked.add(sms);
        return locked;
    }

    @Override
    protected void onAppToggled(AppInfo app, boolean checked) {
        rootExecutor.execute(() -> {
            AlwaysOnHelper.ApplyResult result = AlwaysOnHelper.setNotOptimized(
                    getApplicationContext(), app.getPackageName(), checked);
            if (result == AlwaysOnHelper.ApplyResult.SUCCESS) return;

            // Em falha, converge o checkbox para o estado REAL do sistema.
            boolean actual = BatteryOptimizationHelper.isNotOptimized(
                    getApplicationContext(), app.getPackageName());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                app.setChecked(actual);
                notifyListChanged();
                Toast.makeText(this,
                        result == AlwaysOnHelper.ApplyResult.SYSTEM_PROTECTED
                                ? R.string.always_on_system_app_error
                                : R.string.always_on_apply_error,
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        rootExecutor.shutdown();
        super.onDestroy();
    }
}