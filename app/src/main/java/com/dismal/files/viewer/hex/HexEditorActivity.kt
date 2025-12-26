/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.viewer.hex

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.dismal.files.app.AppActivity
import com.dismal.files.util.putArgs

class HexEditorActivity : AppActivity() {
    private lateinit var fragment: HexEditorFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = HexEditorFragment().putArgs(HexEditorFragment.Args(intent))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        } else {
            fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                as HexEditorFragment
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fragment.onSupportNavigateUp()) {
            return true
        }
        return super.onSupportNavigateUp()
    }
}