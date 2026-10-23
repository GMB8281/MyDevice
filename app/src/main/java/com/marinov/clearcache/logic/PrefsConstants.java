package com.marinov.clearcache.logic;

public class PrefsConstants {
    public static final String PREFS_NAME = "app_prefs";

    // Exclude Apps
    public static final String KEY_EXCLUDE_CONFIGURED = "exclude_list_configured";
    public static final String KEY_EXCLUDED_PACKAGES = "excluded_packages";

    // Auto Reboot
    public static final String KEY_AUTO_REBOOT_ENABLED = "auto_reboot_enabled";
    public static final String KEY_REBOOT_HOUR = "reboot_hour";
    public static final String KEY_REBOOT_MINUTE = "reboot_minute";
    public static final String KEY_REBOOT_DAY_PREFIX = "reboot_day_";
    public static final String KEY_REBOOT_SCHEDULE_TYPE = "reboot_schedule_type";   // daily | weekly | monthly
    public static final String KEY_REBOOT_DAY_OF_MONTH = "reboot_day_of_month";     // 1..31

    // Auto Clean
    public static final String KEY_AUTO_CLEAN_ENABLED = "auto_clean_enabled";
    public static final String KEY_CLEAN_HOUR = "clean_hour";
    public static final String KEY_CLEAN_MINUTE = "clean_minute";
    public static final String KEY_CLEAN_DAY_PREFIX = "clean_day_";
    public static final String KEY_CLEAN_SCHEDULE_TYPE = "clean_schedule_type";     // daily | weekly | monthly
    public static final String KEY_CLEAN_DAY_OF_MONTH = "clean_day_of_month";       // 1..31

    // Battery Protection (NOVO)
    public static final String KEY_BATTERY_PROTECTION_ENABLED = "battery_protection_enabled";
    public static final String KEY_WEAK_BATTERY = "weak_battery_level";
    public static final String KEY_CRITICAL_BATTERY = "critical_battery_level";
    public static final String KEY_ENERGY_RESERVE = "energy_reserve_level";
}
