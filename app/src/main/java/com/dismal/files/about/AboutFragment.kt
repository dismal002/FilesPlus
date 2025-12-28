/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.dismal.files.databinding.AboutFragmentBinding
import com.dismal.files.easteregg.MLandActivity
import com.dismal.files.ui.LicensesDialogFragment
import com.dismal.files.util.createViewIntent
import com.dismal.files.util.startActivitySafe

class AboutFragment : Fragment() {
    private lateinit var binding: AboutFragmentBinding
    private var versionTapCount = 0
    private var lastTapTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AboutFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.gitHubLayout.setOnClickListener { startActivitySafe(GITHUB_URI.createViewIntent()) }
        binding.licensesLayout.setOnClickListener { LicensesDialogFragment.show(this) }
        // Privacy policy removed - app no longer collects data
        binding.authorNameLayout.setOnClickListener {
            startActivitySafe(AUTHOR_RESUME_URI.createViewIntent())
        }
        binding.authorGitHubLayout.setOnClickListener {
            startActivitySafe(AUTHOR_GITHUB_URI.createViewIntent())
        }
        binding.authorXLayout.setOnClickListener {
            startActivitySafe(AUTHOR_X_URI.createViewIntent())
        }
        
        // Easter egg: tap version 7 times to launch Flappy Droid
        binding.versionLayout.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            // Reset counter if more than 2 seconds have passed since last tap
            if (currentTime - lastTapTime > 2000) {
                versionTapCount = 0
            }
            
            versionTapCount++
            lastTapTime = currentTime
            
            if (versionTapCount >= 7) {
                // Launch easter egg
                val intent = Intent(requireContext(), MLandActivity::class.java)
                startActivity(intent)
                versionTapCount = 0 // Reset counter
            }
        }
    }

    companion object {
        private val GITHUB_URI = Uri.parse("https://github.com/dismal002/FilesPlus")
        // Privacy policy URI removed - app no longer collects data
        private val AUTHOR_RESUME_URI = Uri.parse("https://dismal02.blogspot.com/")
        private val AUTHOR_GITHUB_URI = Uri.parse("https://github.com/dismal002")
        private val AUTHOR_X_URI = Uri.parse("https://x.com/dismal002")
    }
}
