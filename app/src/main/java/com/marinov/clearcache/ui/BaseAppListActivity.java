package com.marinov.clearcache.ui;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.marinov.clearcache.R;
import com.marinov.clearcache.data.AppInfo;
import com.marinov.clearcache.logic.PrefsConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Base compartilhada pelas telas de seleção de apps (Excluir da Limpeza / Always-on).
 * Usa o layout global layout_app_list_screen, incluído nos layouts das Activities.
 *
 * Dois modos:
 *  - Com botão de salvar (getSaveButtonRes() != 0): alterações acumulam e são
 *    persistidas via saveSelection() ao tocar no FAB (tela de exclusão);
 *  - Sem botão (getSaveButtonRes() == 0): FAB ocultado e cada alteração dispara
 *    onAppToggled() para aplicação imediata (tela Always-on).
 *
 * Regras comuns:
 *  - Apps selecionados aparecem primeiro; ordem alfabética dentro de cada grupo
 *    (ordenação aplicada no carregamento e na busca);
 *  - Apps bloqueados (locked) aparecem marcados, acinzentados e não podem ser desmarcados;
 *  - Busca por nome/pacote, back limpa a busca primeiro;
 *  - shouldIncludeApp() define quais apps entram na lista (cada tela decide).
 *
 * loadCheckedPackages(), loadLockedPackages() e shouldIncludeApp() rodam em thread de trabalho.
 */
public abstract class BaseAppListActivity extends AppCompatActivity {

    protected SharedPreferences prefs;

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private ProgressBar progressBar;
    private ExtendedFloatingActionButton saveButton;
    private EditText searchBar;

    private final List<AppInfo> allAppsList = new ArrayList<>();
    private final List<AppInfo> displayList = new ArrayList<>();

    protected abstract int getLayoutRes();
    protected abstract int getTitleRes();
    protected abstract int getSubtitleRes();
    protected abstract Set<String> loadCheckedPackages();
    protected abstract Set<String> loadLockedPackages();

    /** 0 = sem botão de salvar (modo salvamento automático via onAppToggled). */
    protected int getSaveButtonRes() { return 0; }

    /** Chamado pelo botão de salvar (apenas quando getSaveButtonRes() != 0). */
    protected void saveSelection(Set<String> selectedPackages) { }

    /** Gancho de salvamento automático: disparado a cada alteração de checkbox. */
    protected void onAppToggled(AppInfo app, boolean checked) { }

    /** Filtro da lista. Padrão: apps lançáveis ou de sistema (tela de exclusão). */
    protected boolean shouldIncludeApp(ApplicationInfo app, PackageManager pm) {
        return pm.getLaunchIntentForPackage(app.packageName) != null
                || (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** Atualiza a lista visível (ex.: reverter um toggle que falhou). */
    protected void notifyListChanged() {
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutRes());

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, MODE_PRIVATE);

        recyclerView = findViewById(R.id.apps_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        saveButton = findViewById(R.id.save_button);
        searchBar = findViewById(R.id.search_bar);

        ((TextView) findViewById(R.id.title_text)).setText(getTitleRes());
        ((TextView) findViewById(R.id.subtitle_text)).setText(getSubtitleRes());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(displayList, this::onAppToggled);
        recyclerView.setAdapter(adapter);

        int saveButtonRes = getSaveButtonRes();
        if (saveButtonRes != 0) {
            saveButton.setText(saveButtonRes);
            saveButton.setOnClickListener(v -> collectSelectionAndSave());
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0 && saveButton.isExtended()) {
                        saveButton.shrink();
                    } else if (dy < 0 && !saveButton.isExtended()) {
                        saveButton.extend();
                    }
                }
            });
        } else {
            // Modo auto-save: sem FAB; devolve o espaço reservado para ele à lista.
            saveButton.setVisibility(View.GONE);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(), pad);
        }

        setupSearch();
        setupBackPress();
        loadApps();
    }

    private void collectSelectionAndSave() {
        Set<String> selected = new HashSet<>();
        for (AppInfo app : allAppsList) {
            if (app.isChecked() && !app.isLocked()) {
                selected.add(app.getPackageName());
            }
        }
        saveSelection(selected);
    }

    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!searchBar.getText().toString().isEmpty()) {
                    searchBar.setText("");
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void filterApps(String query) {
        List<AppInfo> filteredList = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(allAppsList);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            for (AppInfo app : allAppsList) {
                if (app.getAppName().toLowerCase(Locale.ROOT).contains(lowerQuery)
                        || app.getPackageName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    filteredList.add(app);
                }
            }
        }
        adapter.updateList(filteredList);
    }

    private void loadApps() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        saveButton.setEnabled(false);

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages;
            try {
                packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            } catch (Exception e) {
                packages = new ArrayList<>();
            }

            Set<String> checkedPackages = loadCheckedPackages();
            Set<String> lockedPackages = loadLockedPackages();

            List<AppInfo> loadedApps = new ArrayList<>();
            for (ApplicationInfo app : packages) {
                if (!shouldIncludeApp(app, pm)) continue;

                AppInfo appInfo = new AppInfo();
                appInfo.setAppName(String.valueOf(pm.getApplicationLabel(app)));
                appInfo.setPackageName(app.packageName);
                appInfo.setIcon(pm.getApplicationIcon(app));
                boolean locked = lockedPackages.contains(app.packageName);
                appInfo.setLocked(locked);
                appInfo.setChecked(locked || checkedPackages.contains(app.packageName));
                loadedApps.add(appInfo);
            }

            sortSelectedFirstThenAlphabetical(loadedApps);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allAppsList.clear();
                allAppsList.addAll(loadedApps);
                filterApps(searchBar.getText().toString());
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                saveButton.setEnabled(true);
            });
        }).start();
    }

    private static void sortSelectedFirstThenAlphabetical(List<AppInfo> apps) {
        apps.sort((a, b) -> {
            if (a.isChecked() != b.isChecked()) return a.isChecked() ? -1 : 1;
            return a.getAppName().compareToIgnoreCase(b.getAppName());
        });
    }
}