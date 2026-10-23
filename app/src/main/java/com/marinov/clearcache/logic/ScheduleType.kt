package com.marinov.clearcache.logic

enum class ScheduleType(val key: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly");

    companion object {
        fun fromKey(key: String?, default: ScheduleType): ScheduleType =
            values().firstOrNull { it.key == key } ?: default
    }
}
