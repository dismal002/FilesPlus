/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.provider.archive

import android.content.Context
import java8.nio.file.Path
import com.dismal.files.fileaction.ArchivePasswordDialogActivity
import com.dismal.files.fileaction.ArchivePasswordDialogFragment
import com.dismal.files.provider.common.UserAction
import com.dismal.files.provider.common.UserActionRequiredException
import com.dismal.files.util.createIntent
import com.dismal.files.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file, context)
        )
    }
}
