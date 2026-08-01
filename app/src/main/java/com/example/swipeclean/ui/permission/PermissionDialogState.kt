package com.example.swipeclean.ui.permission

data class PermissionDialogState(
    val showDialog: Boolean = true,
    val isLoading: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val currentPermissionStep: PermissionStep = PermissionStep.IMAGES
)

enum class PermissionStep {
    IMAGES,
    VIDEOS,
    NOTIFICATIONS,
    COMPLETE
}