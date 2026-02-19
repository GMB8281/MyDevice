package com.marinov.clearcache;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExcludeAppsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private ProgressBar progressBar;
    private ExtendedFloatingActionButton saveButton;
    private EditText searchBar;

    private List<AppInfo> allAppsList;
    private List<AppInfo> displayList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exclude_apps);

        recyclerView = findViewById(R.id.apps_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        saveButton = findViewById(R.id.save_button);
        searchBar = findViewById(R.id.search_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        allAppsList = new ArrayList<>();
        displayList = new ArrayList<>();

        adapter = new AppListAdapter(displayList);
        recyclerView.setAdapter(adapter);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

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

        saveButton.setOnClickListener(v -> savePreferencesAndFinish());

        setupSearch();
        setupBackPress();
        loadApps();
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
            List<ApplicationInfo> packages;
            try {
                packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            } catch (Exception e) {
                packages = new ArrayList<>();
            }

            Set<String> excluded = prefs.getStringSet("excluded_packages", new HashSet<>());
            List<AppInfo> loadedApps = new ArrayList<>();

            for (ApplicationInfo app : packages) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null || (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    AppInfo appInfo = new AppInfo();
                    appInfo.setAppName((String) pm.getApplicationLabel(app));
                    appInfo.setPackageName(app.packageName);
                    appInfo.setIcon(pm.getApplicationIcon(app));
                    appInfo.setChecked(excluded.contains(app.packageName));
                    loadedApps.add(appInfo);
                }
            }

            loadedApps.sort((o1, o2) -> o1.getAppName().compareToIgnoreCase(o2.getAppName()));

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                allAppsList.clear();
                allAppsList.addAll(loadedApps);
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