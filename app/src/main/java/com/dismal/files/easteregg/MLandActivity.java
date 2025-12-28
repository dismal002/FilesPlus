/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dismal.files.easteregg;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.dismal.files.R;
import com.dismal.files.app.AppActivity;

/**
 * Enhanced Flappy Droid Activity - Open Source Version
 * 
 * Main activity for the enhanced Flappy Droid game with additional features
 * like high score tracking, difficulty selection, and settings.
 */
public class MLandActivity extends AppActivity {
    private static final String PREFS_NAME = "FlappyDroidPrefs";
    private static final String HIGH_SCORE_KEY = "high_score";
    private static final String DIFFICULTY_KEY = "difficulty";
    private static final String SOUND_ENABLED_KEY = "sound_enabled";
    
    MLand mLand;
    private SharedPreferences mPrefs;
    private boolean mSoundEnabled = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the action bar for fullscreen game experience
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.mland);
        
        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        mLand = (MLand) findViewById(R.id.world);
        mLand.setScoreFieldHolder((ViewGroup) findViewById(R.id.scores));
        
        final View welcome = findViewById(R.id.welcome);
        mLand.setSplash(welcome);
        
        // Initialize UI elements
        
        // Load saved settings
        loadSettings();
        
        // Apply sound setting to MLand
        mLand.setSoundEnabled(mSoundEnabled);
        
        // Setup controllers if available
        final int numControllers = mLand.getGameControllers().size();
        if (numControllers > 0) {
            mLand.setupPlayers(numControllers);
        }
        
        // Setup FloatingActionButtons
        setupFloatingActionButtons();
        
        updateUI();
        showSwipeTip();
    }
    
    private void showSwipeTip() {
        int swipeTipCount = mPrefs.getInt("swipecount", 0);
        if (swipeTipCount <= 4) {
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putInt("swipecount", swipeTipCount + 1);
            editor.apply();
            
            // Show a simple toast instead of Snackbar for simplicity
            Toast.makeText(this, "Swipe to change scenery and colors", Toast.LENGTH_LONG).show();
        }
    }
    
    private void setupFloatingActionButtons() {
        FloatingActionMenu fabMenu = (FloatingActionMenu) findViewById(R.id.menu2);
        FloatingActionButton aboutFab = (FloatingActionButton) findViewById(R.id.about);
        FloatingActionButton soundFab = (FloatingActionButton) findViewById(R.id.sound);
        FloatingActionButton difficultyFab = (FloatingActionButton) findViewById(R.id.difficulty);
        
        if (aboutFab != null) {
            aboutFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAboutDialog();
                }
            });
        }
        
        if (soundFab != null) {
            soundFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSound();
                }
            });
        }
        
        if (difficultyFab != null) {
            difficultyFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    difficultyButtonPressed(v);
                }
            });
        }
    }
    
    private void showAboutDialog() {
        Toast.makeText(this, "Enhanced Flappy Droid v2.0\nCollect stars for bonus points!\nTap to jump, avoid obstacles!", Toast.LENGTH_LONG).show();
        
        // Test the color changing functionality
        mLand.changePlayerColors();
    }
    
    private void toggleSound() {
        mSoundEnabled = !mSoundEnabled;
        mLand.setSoundEnabled(mSoundEnabled);
        saveSettings();
        
        String message = mSoundEnabled ? "Sound enabled" : "Sound disabled";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        // Test the scene changing functionality
        mLand.changeScene();
    }
    
    private void loadSettings() {
        int highScore = mPrefs.getInt(HIGH_SCORE_KEY, 0);
        String difficulty = mPrefs.getString(DIFFICULTY_KEY, "EASY");
        mSoundEnabled = mPrefs.getBoolean(SOUND_ENABLED_KEY, true);
        
        try {
            MLand.currentDifficulty = MLand.DifficultyLevel.valueOf(difficulty);
        } catch (IllegalArgumentException e) {
            MLand.currentDifficulty = MLand.DifficultyLevel.EASY;
        }
    }
    
    private void saveSettings() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(DIFFICULTY_KEY, MLand.currentDifficulty.name());
        editor.putBoolean(SOUND_ENABLED_KEY, mSoundEnabled);
        
        // Save high score if current score is higher
        int currentHighScore = mPrefs.getInt(HIGH_SCORE_KEY, 0);
        int currentScore = getCurrentHighScore();
        if (currentScore > currentHighScore) {
            editor.putInt(HIGH_SCORE_KEY, currentScore);
        }
        
        editor.apply();
    }
    
    private int getCurrentHighScore() {
        int maxScore = 0;
        for (int i = 0; i < mLand.getNumPlayers(); i++) {
            MLand.Player player = mLand.getPlayer(i);
            if (player != null && player.getScore() > maxScore) {
                maxScore = player.getScore();
            }
        }
        return maxScore;
    }
    
    private void updateUI() {
        updateDifficultyIcon();
    }

    public void updateSplashPlayers() {
        final int N = mLand.getNumPlayers();
        final View minus = findViewById(R.id.player_minus_button);
        final View plus = findViewById(R.id.player_plus_button);
        
        if (N == 1) {
            minus.setVisibility(View.INVISIBLE);
            plus.setVisibility(View.VISIBLE);
            plus.requestFocus();
        } else if (N == mLand.MAX_PLAYERS) {
            minus.setVisibility(View.VISIBLE);
            plus.setVisibility(View.INVISIBLE);
            minus.requestFocus();
        } else {
            minus.setVisibility(View.VISIBLE);
            plus.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        mLand.stop();
        saveSettings();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mLand.onAttachedToWindow(); // resets and starts animation
        updateSplashPlayers();
        mLand.showSplash();
        updateUI();
    }

    public void playerMinus(View v) {
        mLand.removePlayer();
        updateSplashPlayers();
    }

    public void playerPlus(View v) {
        mLand.addPlayer();
        updateSplashPlayers();
    }

    public void startButtonPressed(View v) {
        findViewById(R.id.player_minus_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.player_plus_button).setVisibility(View.INVISIBLE);
        mLand.start(true);
    }
    
    public void difficultyButtonPressed(View v) {
        // Cycle through difficulty levels
        switch (MLand.currentDifficulty) {
            case EASY:
                MLand.currentDifficulty = MLand.DifficultyLevel.NORMAL;
                break;
            case NORMAL:
                MLand.currentDifficulty = MLand.DifficultyLevel.HARD;
                break;
            case HARD:
                MLand.currentDifficulty = MLand.DifficultyLevel.EASY;
                break;
        }
        updateUI();
        updateDifficultyIcon();
        saveSettings();
    }
    
    private void updateDifficultyIcon() {
        FloatingActionButton difficultyFab = (FloatingActionButton) findViewById(R.id.difficulty);
        if (difficultyFab != null) {
            int iconResource;
            switch (MLand.currentDifficulty) {
                case EASY:
                    iconResource = R.drawable.ic_easy_level;
                    break;
                case NORMAL:
                    iconResource = R.drawable.ic_normal_level;
                    break;
                case HARD:
                    iconResource = R.drawable.ic_hard_level;
                    break;
                default:
                    iconResource = R.drawable.ic_normal_level;
                    break;
            }
            difficultyFab.setImageResource(iconResource);
        }
    }
    
    public void resetHighScore(View v) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(HIGH_SCORE_KEY, 0);
        editor.apply();
        updateUI();
    }
}