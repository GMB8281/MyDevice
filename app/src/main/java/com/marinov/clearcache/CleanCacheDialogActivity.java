package com.marinov.clearcache;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CleanCacheDialogActivity extends AppCompatActivity {

    private static final String TAG = CleanCacheDialogActivity.class.getSimpleName();

    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_EXCLUDE_CONFIGURED = "exclude_list_configured";
    public static final String KEY_EXCLUDED_PACKAGES = "excluded_packages";

    private ActivityResultLauncher<Intent> mExcludeAppsLauncher;
    private SharedPreferences prefs;
    private AlertDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        mExcludeAppsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isFinishing()) showConfirmationDialog();
                });

        if (savedInstanceState == null) {
            if (!prefs.getBoolean(KEY_EXCLUDE_CONFIGURED, false)) {
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
                            .putBoolean(KEY_EXCLUDE_CONFIGURED, true)
                            .putStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>())
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

        Set<String> excludedPackages = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>());

        new Thread(() -> {
            int exitCode = -1;
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = getDataOutputStream(su, excludedPackages);
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

    // MÉTODO TORNADO PÚBLICO E ESTÁTICO PARA REUSO NOS SERVIÇOS
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

    // MÉTODO PÚBLICO ESTÁTICO PARA EXECUÇÃO EM BACKGROUND
    public static void executeCleanCacheSilent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> excludedPackages = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>());
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

    private void showRebootPrompt() {
        if (isFinishing() || isDestroyed()) return;

        currentDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reboot_dialog_title))
                .setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> rebootDevice())
                .setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> finish())
                .setCancelable(false)
                .create();

        currentDialog.setCanceledOnTouchOutside(false);
        currentDialog.show();

        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void rebootDevice() {
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(su.getOutputStream());
                os.writeBytes("reboot\n");
                os.writeBytes("exit\n");
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao reiniciar dispositivo", e);
            }
        }).start();
    }
}