package com.marinov.clearcache.data;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private Drawable icon;
    private String appName;
    private String packageName;
    private boolean isChecked;
    private boolean isLocked;

    public Drawable getIcon() { return icon; }
    public void setIcon(Drawable icon) { this.icon = icon; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public boolean isChecked() { return isChecked; }
    public void setChecked(boolean checked) { isChecked = checked; }

    /** Bloqueado: marcado obrigatoriamente, acinzentado e não desselecionável. */
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }
}