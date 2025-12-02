package com.marinov.clearcache;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExcludeAppsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private ProgressBar progressBar;
    private Button saveButton;
    private ImageButton searchButton;
    private EditText searchBar;

    // Lista mestre contendo todos os apps
    private List<AppInfo> allAppsList;
    // Lista exibida atualmente (filtrada ou não)
    private List<AppInfo> displayList;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exclude_apps);

        recyclerView = findViewById(R.id.apps_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        saveButton = findViewById(R.id.save_button);
        searchButton = findViewById(R.id.search_button);
        searchBar = findViewById(R.id.search_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        allAppsList = new ArrayList<>();
        displayList = new ArrayList<>();

        // O adapter manipula a displayList
        adapter = new AppListAdapter(displayList);
        recyclerView.setAdapter(adapter);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        saveButton.setOnClickListener(v -> savePreferencesAndFinish());

        setupSearch();
        setupBackPress(); // Configura o botão voltar
        loadApps();
    }

    private void setupSearch() {
        // Toggle da barra de busca
        searchButton.setOnClickListener(v -> {
            if (searchBar.getVisibility() == View.GONE) {
                searchBar.setVisibility(View.VISIBLE);
                searchBar.requestFocus();
            } else {
                searchBar.setVisibility(View.GONE);
                searchBar.setText(""); // Limpa a busca ao fechar
                // O TextWatcher já chamará filterApps("")
            }
        });

        // Lógica de filtro
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
        // Intercepta o botão voltar do sistema
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchBar.getVisibility() == View.VISIBLE) {
                    // 1. Se a busca estiver visível, limpa e esconde
                    searchBar.setText("");
                    searchBar.setVisibility(View.GONE);
                } else {
                    // 2. Se não estiver visível, prossegue com o voltar padrão (fecha a activity)
                    setEnabled(false); // Desativa este callback para não entrar em loop
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
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : allAppsList) {
                if (app.getAppName().toLowerCase().contains(lowerQuery) ||
                        app.getPackageName().toLowerCase().contains(lowerQuery)) {
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
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> excluded = prefs.getStringSet("excluded_packages", new HashSet<>());
            List<AppInfo> loadedApps = new ArrayList<>();

            for (ApplicationInfo app : packages) {
                // Filtra apps do sistema sem ícone de launcher (opcional)
                if (pm.getLaunchIntentForPackage(app.packageName) != null || (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    AppInfo appInfo = new AppInfo();
                    appInfo.setAppName((String) pm.getApplicationLabel(app));
                    appInfo.setPackageName(app.packageName);
                    appInfo.setIcon(pm.getApplicationIcon(app));
                    appInfo.setChecked(excluded.contains(app.packageName));
                    loadedApps.add(appInfo);
                }
            }

            // Ordena a lista alfabeticamente
            loadedApps.sort((o1, o2) -> o1.getAppName().compareToIgnoreCase(o2.getAppName()));

            runOnUiThread(() -> {
                allAppsList.clear();
                allAppsList.addAll(loadedApps);

                // Inicialmente a lista de exibição é igual à completa
                displayList.clear();
                displayList.addAll(allAppsList);

                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                saveButton.setEnabled(true);
            });
        }).start();
    }

    private void savePreferencesAndFinish() {
        // Importante: Usamos allAppsList para salvar, pois o adapter pode estar mostrando
        // apenas uma lista filtrada (busca). Queremos salvar o estado de TUDO.
        Set<String> newExcludedSet = new HashSet<>();
        for (AppInfo app : allAppsList) {
            if (app.isChecked()) {
                newExcludedSet.add(app.getPackageName());
            }
        }

        prefs.edit()
                .putStringSet("excluded_packages", newExcludedSet)
                .putBoolean("exclude_list_configured", true)
                .apply();

        finish();
    }
}