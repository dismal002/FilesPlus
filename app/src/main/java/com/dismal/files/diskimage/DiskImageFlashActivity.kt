/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.dismal.files.app.AppActivity
import com.dismal.files.file.MimeType
import com.dismal.files.R
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.ParcelableParceler
import com.dismal.files.util.args
import com.dismal.files.util.putArgs

class DiskImageFlashActivity : AppActivity() {
    private val args by args<Args>()

    private val viewModel by viewModels<DiskImageFlashViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.disk_image_flash_activity)

        if (savedInstanceState == null) {
            val fragment = DiskImageFlashFragment().putArgs(
                DiskImageFlashFragment.Args(args.path, args.mimeType)
            )
            supportFragmentManager.commit {
                add(R.id.fragment_container, fragment)
            }
        }
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val mimeType: MimeType
    ) : ParcelableArgs

    companion object {
        fun createIntent(path: Path, mimeType: MimeType, context: Context): Intent =
            Intent(context, DiskImageFlashActivity::class.java)
                .putArgs(Args(path, mimeType))
    }
}