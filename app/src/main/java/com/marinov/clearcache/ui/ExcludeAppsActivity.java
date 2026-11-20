package com.marinov.clearcache.ui;

import com.marinov.clearcache.R;
import com.marinov.clearcache.logic.BatteryOptimizationHelper;
import com.marinov.clearcache.logic.DefaultAppsHelper;
import com.marinov.clearcache.logic.PrefsConstants;

import java.util.HashSet;
import java.util.Set;

public class ExcludeAppsActivity extends BaseAppListActivity {

    @Override
    protected int getLayoutRes() { return R.layout.activity_exclude_apps; }

    @Override
    protected int getTitleRes() { return R.string.exclude_apps_title; }

    @Override
    protected int getSubtitleRes() { return R.string.exclude_apps_subtitle; }

    @Override
    protected int getSaveButtonRes() { return R.string.exclude_apps_save_button; }

    @Override
    protected Set<String> loadCheckedPackages() {
        return prefs.getStringSet(PrefsConstants.KEY_EXCLUDED_PACKAGES, new HashSet<>());
    }

    @Override
    protected Set<String> loadLockedPackages() {
        // Proteções obrigatórias: este app, SystemUI, discador/SMS/assistente/launcher
        // padrão e todos os apps "não otimizados" (incluindo os setados via Always-on).
        Set<String> locked = new HashSet<>(DefaultAppsHelper.getMandatoryExcludedPackages(this));
        locked.addAll(BatteryOptimizationHelper.getNotOptimizedPackages(this));
        return locked;
    }

    @Override
    protected void saveSelection(Set<String> selectedPackages) {
        // Bloqueados não são persistidos: a UI e o CacheCleaner os reinjetam dinamicamente.
        prefs.edit()
                .putStringSet(PrefsConstants.KEY_EXCLUDED_PACKAGES, selectedPackages)
                .putBoolean(PrefsConstants.KEY_EXCLUDE_CONFIGURED, true)
                .apply();
        finish();
    }
}