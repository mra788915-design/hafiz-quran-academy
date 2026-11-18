package com.example.ui.auth

import com.example.data.models.UserRole

/**
 * Configures how this APK behaves on a user's phone:
 * - [AUTO_SINGLE_APP] (Default): Installs ONLY 1 app icon on the phone. Once Admin, Teacher, or Parent
 *   signs in with their Admin-issued credentials (or selects their role once), this phone locks strictly
 *   into ONLY that single dedicated app (Admin App OR Teacher App OR Parent App) while staying connected
 *   live via Firebase Cloud.
 * - [ADMIN_ONLY] / [TEACHER_ONLY] / [PARENT_ONLY]: Optional build-time lock if exporting 3 pre-locked APKs.
 */
enum class AppEditionMode(val prelockedRole: UserRole?) {
    AUTO_SINGLE_APP(null),
    ADMIN_ONLY(UserRole.ADMIN),
    TEACHER_ONLY(UserRole.TEACHER),
    PARENT_ONLY(UserRole.PARENT)
}

object AppEditionConfig {
    val ACTIVE_EDITION: AppEditionMode = AppEditionMode.AUTO_SINGLE_APP
}
