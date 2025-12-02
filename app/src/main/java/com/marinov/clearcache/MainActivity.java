package com.marinov.clearcache;

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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_EXCLUDE_CONFIGURED = "exclude_list_configured";
    private static final String KEY_EXCLUDED_PACKAGES = "excluded_packages";

    private ActivityResultLauncher<Intent> mExcludeAppsLauncher;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Registra o launcher para a atividade de exclusão de apps
        mExcludeAppsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Chamado quando ExcludeAppsActivity fecha (seja 1a vez ou edição)
                    // Reabre o diálogo de confirmação principal
                    showConfirmationDialog();
                });

        // Verifica se o usuário já configurou a lista
        if (!prefs.getBoolean(KEY_EXCLUDE_CONFIGURED, false)) {
            // Primeira execução
            showExcludeAppsDialog();
        } else {
            // Já configurado
            showConfirmationDialog();
        }
    }

    private void showExcludeAppsDialog() {
        // NOTA: Recomenda-se atualizar o texto de R.string.exclude_dialog_message no strings.xml
        // para remover menções de que não é possível editar a lista posteriormente.
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.exclude_dialog_title))
                .setMessage(getString(R.string.exclude_dialog_message))
                .setPositiveButton(getString(R.string.exclude_dialog_positive_button), (dialog, which) -> {
                    mExcludeAppsLauncher.launch(new Intent(this, ExcludeAppsActivity.class));
                })
                .setNegativeButton(getString(R.string.exclude_dialog_negative_button), (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(KEY_EXCLUDE_CONFIGURED, true)
                            .putStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>())
                            .commit();
                    showConfirmationDialog();
                })
                .setCancelable(false)
                .create()
                .show();
    }

    private void showConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirm_dialog_title))
                .setMessage(getString(R.string.confirm_dialog_message))
                .setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> {
                    dialog.dismiss();
                    showProgressAndExecute();
                })
                .setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> finish())
                // Novo botão para editar a lista usando String Resource
                .setNeutralButton(getString(R.string.edit_ignored_apps), (dialog, which) -> {
                    // Usa o mesmo launcher para reabrir o diálogo ao voltar
                    mExcludeAppsLauncher.launch(new Intent(this, ExcludeAppsActivity.class));
                })
                .setCancelable(false)
                .create()
                .show();
    }

    private void showProgressAndExecute() {
        View progressView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_progress, null, false);

        CircularProgressIndicator cpi =
                progressView.findViewById(R.id.progress);
        if (cpi != null) {
            cpi.setIndeterminate(true);
            cpi.show();
        }

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();

        progressDialog.setOnShowListener(d -> {
            if (progressDialog.getWindow() != null) {
                progressDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });

        progressDialog.show();

        Set<String> excludedPackages = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>());

        new Thread(() -> {
            int exitCode = -1;
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = getDataOutputStream(su, excludedPackages);
                os.flush();
                exitCode = su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error while clearing cache", e);
            }

            final int finalExitCode = exitCode;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalExitCode == 0) {
                    showRebootPrompt();
                } else {
                    showRootDeniedDialog();
                }
            });
        }).start();
    }

    private void showRootDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.root_denied_dialog_title))
                .setMessage(getString(R.string.root_denied_dialog_message))
                .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> finish())
                .setCancelable(false)
                .create()
                .show();
    }

    @NonNull
    private static DataOutputStream getDataOutputStream(Process su, Set<String> excludedPackages) throws IOException {
        DataOutputStream os = new DataOutputStream(su.getOutputStream());
        os.writeBytes("for pkg in $(pm list packages | sed 's/^package://'); do\n");

        StringBuilder command = new StringBuilder("  if [ \"$pkg\" != \"com.marinov.clearcache\" ] "
                + "&& [ \"$pkg\" != \"com.android.systemui\" ]");

        for (String pkg : excludedPackages) {
            if (pkg != null && pkg.matches("^[a-zA-Z0-9._-]+$")) {
                command.append(" && [ \"$pkg\" != \"").append(pkg).append("\" ]");
            }
        }

        command.append(" ; then\n");
        os.writeBytes(command.toString());

        os.writeBytes("    am force-stop \"$pkg\"\n");
        os.writeBytes("  fi\n");
        os.writeBytes("done\n");
        os.writeBytes("pm trim-caches 9999999999999\n");
        os.writeBytes("exit\n");
        return os;
    }

    private void showRebootPrompt() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.reboot_dialog_title))
                .setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> {
                    dialog.dismiss();
                    rebootDevice();
                })
                .setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> finish())
                .setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
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
                Log.e(TAG, "Error while rebooting the device", e);
            }
        }).start();
    }
}