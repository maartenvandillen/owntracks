package it.vandillen.tracker.ui.mixins

import androidx.activity.result.ActivityResultCaller

interface ActivityResultCallerWithPermissionCallback :
    ActivityResultCaller, PermissionResultCallback
