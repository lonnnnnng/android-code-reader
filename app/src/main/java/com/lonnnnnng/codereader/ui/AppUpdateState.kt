package com.lonnnnnng.codereader.ui

import com.lonnnnnng.codereader.update.AppRelease
import java.io.File

/** @author long */
enum class AppUpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

/** 更新状态独立于导入/搜索 busy，下载时仍可继续浏览设置并看到进度。 @author long */
data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val release: AppRelease? = null,
    val progressPercent: Int = 0,
    val downloadedApk: File? = null,
    val errorMessage: String? = null,
    val dialogVisible: Boolean = false,
)
