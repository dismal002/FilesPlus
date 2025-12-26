/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.fileproperties.apk

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.dismal.files.R
import com.dismal.files.databinding.PermissionListDialogBinding
import com.dismal.files.util.Failure
import com.dismal.files.util.Loading
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.Stateful
import com.dismal.files.util.Success
import com.dismal.files.util.args
import com.dismal.files.util.fadeInUnsafe
import com.dismal.files.util.fadeOutUnsafe
import com.dismal.files.util.fadeToVisibilityUnsafe
import com.dismal.files.util.getQuantityString
import com.dismal.files.util.layoutInflater
import com.dismal.files.util.putArgs
import com.dismal.files.util.show
import com.dismal.files.util.viewModels

class PermissionListDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { PermissionListViewModel(args.permissionNames) } }

    private lateinit var binding: PermissionListDialogBinding

    private lateinit var adapter: PermissionListAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .apply {
                val permissionsSize = args.permissionNames.size
                setTitle(
                    getQuantityString(
                        R.plurals.file_properties_apk_requested_permissions_positive_format,
                        permissionsSize, permissionsSize
                    )
                )

                binding = PermissionListDialogBinding.inflate(context.layoutInflater)
                binding.recyclerView.layoutManager = LinearLayoutManager(context)
                adapter = PermissionListAdapter()
                binding.recyclerView.adapter = adapter
                setView(binding.root)

                viewModel.permissionListLiveData.observe(this@PermissionListDialogFragment) {
                    onPermissionListChanged(it)
                }
            }
            .setPositiveButton(android.R.string.ok, null)
            .create()

    private fun onPermissionListChanged(stateful: Stateful<List<PermissionItem>>) {
        when (stateful) {
            is Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.emptyView.fadeOutUnsafe()
                adapter.clear()
            }
            is Failure -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = stateful.throwable.toString()
                binding.emptyView.fadeOutUnsafe()
                adapter.clear()
            }
            is Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.emptyView.fadeToVisibilityUnsafe(stateful.value.isEmpty())
                adapter.replace(stateful.value)
            }
        }
    }

    companion object {
        fun show(permissionNames: Array<String>, fragment: Fragment) {
            PermissionListDialogFragment().putArgs(Args(permissionNames)).show(fragment)
        }
    }

    @Parcelize
    class Args(val permissionNames: Array<String>) : ParcelableArgs
}
