package com.marinov.clearcache.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.marinov.clearcache.R;
import com.marinov.clearcache.logic.CacheCleaner;
import com.marinov.clearcache.logic.PrefsConstants;
import com.marinov.clearcache.logic.RootHelper;

import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.Set;

public class CleanCacheDialogActivity extends AppCompatActivity {
    private static final String TAG = CleanCacheDialogActivity.class.getSimpleName();

    private ActivityResultLauncher<Intent> mExcludeAppsLauncher;
    private SharedPreferences prefs;
    private AlertDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, MODE_PRIVATE);

        mExcludeAppsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isFinishing()) showConfirmationDialog();
                });

        if (savedInstanceState == null) {
            if (!prefs.getBoolean(PrefsConstants.KEY_EXCLUDE_CONFIGURED, false)) {
                showExcludeAppsDialog();
            } else {
                showConfirmationDialog();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        super.onDestroy();
    }

    private void showExcludeAppsDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();

        currentDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.exclude_dialog_title))
                .setMessage(getString(R.string.exclude_dialog_message))
                .setPositiveButton(getString(R.string.exclude_dialog_positive_button), (dialog, which) -> mExcludeAppsLauncher.launch(new Intent(this, ExcludeAppsActivity.class)))
                .setNegativeButton(getString(R.string.exclude_dialog_negative_button), (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(PrefsConstants.KEY_EXCLUDE_CONFIGURED, true)
                            .putStringSet(PrefsConstants.KEY_EXCLUDED_PACKAGES, new HashSet<>())
                            .apply();
                    showConfirmationDialog();
                })
                .setCancelable(false)
                .create();
        currentDialog.setCanceledOnTouchOutside(false);
        currentDialog.show();
    }

    private void showConfirmationDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();

        currentDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirm_dialog_title))
                .setMessage(getString(R.string.confirm_dialog_message))
                .setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> showProgressAndExecute())
                .setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> finish())
                .setNeutralButton(getString(R.string.edit_ignored_apps), (dialog, which) -> mExcludeAppsLauncher.launch(new Intent(this, ExcludeAppsActivity.class)))
                .setCancelable(false)
                .create();
        currentDialog.setCanceledOnTouchOutside(false);
        currentDialog.show();
    }

    private void showProgressAndExecute() {
        if (isFinishing() || isDestroyed()) return;

        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null, false);
        CircularProgressIndicator cpi = progressView.findViewById(R.id.progress);
        if (cpi != null) {
            cpi.setIndeterminate(true);
            cpi.show();
        }

        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();

        currentDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        currentDialog.setCanceledOnTouchOutside(false);
        currentDialog.setOnShowListener(d -> {
            if (currentDialog.getWindow() != null) {
                currentDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        currentDialog.show();

        new Thread(() -> {
            int exitCode = -1;
            try {
                Process su = Runtime.getRuntime().exec("su");
                Set<String> excludedPackages = prefs.getStringSet(PrefsConstants.KEY_EXCLUDED_PACKAGES, new HashSet<>());
                DataOutputStream os = CacheCleaner.getDataOutputStream(su, excludedPackages);
                os.flush();
                exitCode = su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao limpar cache", e);
            }
            final int finalExitCode = exitCode;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (currentDialog != null && currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
                if (finalExitCode == 0) {
                    showRebootPrompt();
                } else {
                    showRootDeniedDialog();
                }
            });
        }).start();
    }

    private void showRootDeniedDialog() {
        if (isFinishing() || isDestroyed()) return;
        currentDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.root_denied_dialog_title))
                .setMessage(getString(R.string.root_denied_dialog_message))
                .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> finish())
                .setCancelable(false)
                .create();
        currentDialog.show();
    }

    private void showRebootPrompt() {
        if (isFinishing() || isDestroyed()) return;
        currentDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reboot_dialog_title))
                .setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> RootHelper.rebootDevice())
                .setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> finish())
                .setCancelable(false)
                .create();
        currentDialog.setCanceledOnTouchOutside(false);
        currentDialog.show();

        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }
}