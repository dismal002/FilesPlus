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

import android.animation.LayoutTransition;
import android.animation.TimeAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.dismal.files.R;

import java.util.ArrayList;
import java.util.Random;

/**
 * Enhanced Flappy Droid Game - Open Source Version
 * 
 * This is a clean, open source implementation of the Android Marshmallow Easter Egg game
 * with additional features like sound effects, enhanced graphics, and improved gameplay.
 * 
 * Features:
 * - Original Android Marshmallow game mechanics
 * - Sound effects for jumps, crashes, and scoring
 * - Enhanced graphics with multiple obstacle types
 * - Collectible bonus points
 * - Multiple difficulty levels
 * - Controller support
 * - Multiplayer support (up to 6 players)
 */
public class MLand extends FrameLayout {
    public static final String TAG = "MLand";
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean DEBUG_DRAW = false;
    public static final boolean SHOW_TOUCHES = false;
    public static final boolean DEBUG_IDDQD = Log.isLoggable(TAG + ".iddqd", Log.DEBUG);
    
    public static final float PI_2 = (float) (Math.PI/2);
    public static final boolean AUTOSTART = true;
    public static final boolean HAVE_STARS = true;
    public static final float DEBUG_SPEED_MULTIPLIER = 0.5f;
    
    public static final int DEFAULT_PLAYERS = 1;
    public static final int MIN_PLAYERS = 1;
    public static final int MAX_PLAYERS = 6;
    
    static final float CONTROLLER_VIBRATION_MULTIPLIER = 2f;
    
    // Game difficulty levels
    public enum DifficultyLevel {
        EASY, NORMAL, HARD
    }
    
    // Enhanced obstacle types
    public enum ObstacleType {
        MARSHMALLOW, LOLLIPOP
    }
    
    // Game state
    public static DifficultyLevel currentDifficulty = DifficultyLevel.EASY;
    public static int currentLevel = 0;
    public static int bonusPoints = 0;
    
    // Scene and time constants
    private static final int DAY = 0, NIGHT = 1, TWILIGHT = 2, SUNSET = 3;
    private static final int[][] SKIES = {
            { 0xFFc0c0FF, 0xFFa0a0FF }, // DAY
            { 0xFF000010, 0xFF000000 }, // NIGHT
            { 0xFF000040, 0xFF000010 }, // TWILIGHT
            { 0xFFa08020, 0xFF204080 }, // SUNSET
    };
    
    private static final int SCENE_CITY = 0, SCENE_TX = 1, SCENE_ZRH = 2, SCENE_COUNT = 3;
    
    // Resource arrays for enhanced graphics
    static final int[] ANTENNAE = {R.drawable.mm_antennae, R.drawable.mm_antennae2};
    static final int[] EYES = {R.drawable.mm_eyes, R.drawable.mm_eyes2};
    static final int[] MOUTHS = {R.drawable.mm_mouth1, R.drawable.mm_mouth2, 
                                R.drawable.mm_mouth3, R.drawable.mm_mouth4};
    static final int[] CACTI = {R.drawable.cactus1, R.drawable.cactus2, R.drawable.cactus3};
    static final int[] MOUNTAINS = {R.drawable.mountain1, R.drawable.mountain2, R.drawable.mountain3};
    
    // Extended POPS array with all custom graphics from restored version
    static final int[] POPS = {R.drawable.pop_belt, R.drawable.pop_droid, R.drawable.pop_pizza, 
                              R.drawable.pop_stripes, R.drawable.pop_swirl, R.drawable.pop_vortex, 
                              R.drawable.pop_vortex2, R.drawable.neko1, R.drawable.neko2, 
                              R.drawable.neko3, R.drawable.neko4, R.drawable.neko5, R.drawable.neko6, 
                              R.drawable.neko7, R.drawable.neko8, R.drawable.neko9, R.drawable.neko10, 
                              R.drawable.android_oreo_logo, R.drawable.octo1, R.drawable.octo2, 
                              R.drawable.octo3, R.drawable.octo4, R.drawable.octo5, R.drawable.octo6, 
                              R.drawable.octo7};
    
    // Rolling animation configuration for each pop type (0 = static, 1 = rolling)
    static final int[] ROLLINGPOP = {0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0};
    
    // Game parameters
    private static class Params {
        public float TRANSLATION_PER_SEC;
        public int OBSTACLE_SPACING, OBSTACLE_PERIOD;
        public int BONUS_SPACING, BONUS_PERIOD;
        public int POINT_SPACING, POINT_PERIOD;
        public int BOOST_DV;
        public int PLAYER_HIT_SIZE, PLAYER_SIZE;
        public int OBSTACLE_WIDTH, OBSTACLE_STEM_WIDTH;
        public int OBSTACLE_GAP, OBSTACLE_MIN;
        public int BUILDING_WIDTH_MIN, BUILDING_WIDTH_MAX, BUILDING_HEIGHT_MIN;
        public int CLOUD_SIZE_MIN, CLOUD_SIZE_MAX;
        public int STAR_SIZE_MIN, STAR_SIZE_MAX;
        public int G, MAX_V;
        public float SCENERY_Z, OBSTACLE_Z, PLAYER_Z, PLAYER_Z_BOOST, HUD_Z;
        
        public Params(Resources res) {
            TRANSLATION_PER_SEC = res.getDimension(R.dimen.translation_per_sec);
            OBSTACLE_SPACING = res.getDimensionPixelSize(R.dimen.obstacle_spacing);
            OBSTACLE_PERIOD = (int) (OBSTACLE_SPACING / TRANSLATION_PER_SEC);
            BONUS_SPACING = res.getDimensionPixelSize(R.dimen.bonus_spacing);
            BONUS_PERIOD = (int) (BONUS_SPACING / TRANSLATION_PER_SEC);
            POINT_SPACING = res.getDimensionPixelSize(R.dimen.point_spacing);
            POINT_PERIOD = (int) (POINT_SPACING / TRANSLATION_PER_SEC);
            BOOST_DV = res.getDimensionPixelSize(R.dimen.boost_dv);
            PLAYER_HIT_SIZE = res.getDimensionPixelSize(R.dimen.player_hit_size);
            PLAYER_SIZE = res.getDimensionPixelSize(R.dimen.player_size);
            OBSTACLE_WIDTH = res.getDimensionPixelSize(R.dimen.obstacle_width);
            OBSTACLE_STEM_WIDTH = res.getDimensionPixelSize(R.dimen.obstacle_stem_width);
            OBSTACLE_GAP = res.getDimensionPixelSize(R.dimen.obstacle_gap);
            OBSTACLE_MIN = res.getDimensionPixelSize(R.dimen.obstacle_height_min);
            BUILDING_HEIGHT_MIN = res.getDimensionPixelSize(R.dimen.building_height_min);
            BUILDING_WIDTH_MIN = res.getDimensionPixelSize(R.dimen.building_width_min);
            BUILDING_WIDTH_MAX = res.getDimensionPixelSize(R.dimen.building_width_max);
            CLOUD_SIZE_MIN = res.getDimensionPixelSize(R.dimen.cloud_size_min);
            CLOUD_SIZE_MAX = res.getDimensionPixelSize(R.dimen.cloud_size_max);
            STAR_SIZE_MIN = res.getDimensionPixelSize(R.dimen.star_size_min);
            STAR_SIZE_MAX = res.getDimensionPixelSize(R.dimen.star_size_max);
            G = res.getDimensionPixelSize(R.dimen.G);
            MAX_V = res.getDimensionPixelSize(R.dimen.max_v);
            SCENERY_Z = res.getDimensionPixelSize(R.dimen.scenery_z);
            OBSTACLE_Z = res.getDimensionPixelSize(R.dimen.obstacle_z);
            PLAYER_Z = res.getDimensionPixelSize(R.dimen.player_z);
            PLAYER_Z_BOOST = res.getDimensionPixelSize(R.dimen.player_z_boost);
            HUD_Z = res.getDimensionPixelSize(R.dimen.hud_z);
            
            // Sanity checking
            if (OBSTACLE_MIN <= OBSTACLE_WIDTH / 2) {
                L("error: obstacles might be too short, adjusting");
                OBSTACLE_MIN = OBSTACLE_WIDTH / 2 + 1;
            }
        }
    }
    
    private static Params PARAMS;
    private static float dp = 1f;
    
    // Game objects
    private TimeAnimator mAnim;
    private Vibrator mVibrator;
    private AudioManager mAudioManager;
    private final AudioAttributes mAudioAttrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).build();
    
    // Sound effects - no longer used, but kept for compatibility
    private MediaPlayer jumpSound, crashSound, scoreSound, bonusSound, collectedSound;
    
    private View mSplash;
    private ViewGroup mScoreFields;
    private ArrayList<Player> mPlayers = new ArrayList<>();
    private ArrayList<Obstacle> mObstaclesInPlay = new ArrayList<>();
    private ArrayList<Point> mPoints = new ArrayList<>();
    private ArrayList<BonusItem> mBonusItems = new ArrayList<>();
    
    // Game state
    private float t, dt;
    private float mLastPipeTime, mLastBonusTime, mLastPointTime;
    private int mCurrentPipeId;
    private int mWidth, mHeight;
    private boolean mAnimating, mPlaying;
    private boolean mFrozen;
    private int mCountdown = 0;
    private boolean mFlipped;
    private int mTaps;
    private int mTimeOfDay, mScene;
    private boolean mSoundEnabled = true;
    
    // Input handling
    private Paint mTouchPaint, mPlayerTracePaint;
    private ArrayList<Integer> mGameControllers = new ArrayList<>();
    
    public static void L(String s, Object... objects) {
        if (DEBUG) {
            Log.d(TAG, objects.length == 0 ? s : String.format(s, objects));
        }
    }
    
    public MLand(Context context) {
        this(context, null);
    }
    
    public MLand(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public MLand(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        // Initialize sound effects
        initSounds();
        
        setFocusable(true);
        PARAMS = new Params(getResources());
        mTimeOfDay = irand(0, SKIES.length - 1);
        mScene = irand(0, SCENE_COUNT);
        
        mTouchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTouchPaint.setColor(0x80FFFFFF);
        mTouchPaint.setStyle(Paint.Style.FILL);
        
        mPlayerTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPlayerTracePaint.setColor(0x80FFFFFF);
        mPlayerTracePaint.setStyle(Paint.Style.STROKE);
        mPlayerTracePaint.setStrokeWidth(2 * dp);
        
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setupPlayers(DEFAULT_PLAYERS);
    }
    
    private void initSounds() {
        // No longer needed - sounds are created on demand
        // Kept for compatibility
    }
    
    public void setSoundEnabled(boolean enabled) {
        mSoundEnabled = enabled;
        L("Sound enabled set to: " + enabled);
    }
    
    private void playSound(int soundResource) {
        // Sound system disabled
    }
    
    // Keep old method for backward compatibility but redirect to new method
    private void playSound(MediaPlayer sound) {
        // Sound system disabled
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        dp = getResources().getDisplayMetrics().density;
        reset();
        if (AUTOSTART) {
            start(false);
        }
    }
    
    @Override
    public boolean willNotDraw() {
        return !DEBUG;
    }
    
    // Game accessors
    public int getGameWidth() { return mWidth; }
    public int getGameHeight() { return mHeight; }
    public float getGameTime() { return t; }
    public float getLastTimeStep() { return dt; }
    
    public void setScoreFieldHolder(ViewGroup vg) {
        mScoreFields = vg;
        if (vg != null) {
            final LayoutTransition lt = new LayoutTransition();
            lt.setDuration(250);
            mScoreFields.setLayoutTransition(lt);
        }
        for (Player p : mPlayers) {
            mScoreFields.addView(p.mScoreField,
                    new ViewGroup.MarginLayoutParams(
                            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                            ViewGroup.MarginLayoutParams.MATCH_PARENT));
        }
    }
    
    public void setSplash(View v) {
        mSplash = v;
    }
    
    // Input device handling
    public static boolean isGamePad(InputDevice dev) {
        int sources = dev.getSources();
        return (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK));
    }
    
    public ArrayList getGameControllers() {
        mGameControllers.clear();
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);
            if (isGamePad(dev)) {
                if (!mGameControllers.contains(deviceId)) {
                    mGameControllers.add(deviceId);
                }
            }
        }
        return mGameControllers;
    }
    
    public int getControllerPlayer(int id) {
        final int player = mGameControllers.indexOf(id);
        if (player < 0 || player >= mPlayers.size()) return 0;
        return player;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        dp = getResources().getDisplayMetrics().density;
        stop();
        reset();
        if (AUTOSTART) {
            start(false);
        }
    }
    
    private static float luma(int bgcolor) {
        return 0.2126f * (float) (bgcolor & 0xFF0000) / 0xFF0000
                + 0.7152f * (float) (bgcolor & 0xFF00) / 0xFF00
                + 0.0722f * (float) (bgcolor & 0xFF) / 0xFF;
    }
    
    // Player management
    public Player getPlayer(int i) {
        return i < mPlayers.size() ? mPlayers.get(i) : null;
    }
    
    private int addPlayerInternal(Player p) {
        mPlayers.add(p);
        realignPlayers();
        TextView scoreField = (TextView)
            LayoutInflater.from(getContext()).inflate(R.layout.mland_scorefield, null);
        if (mScoreFields != null) {
            mScoreFields.addView(scoreField,
                new ViewGroup.MarginLayoutParams(
                        ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                        ViewGroup.MarginLayoutParams.MATCH_PARENT));
        }
        p.setScoreField(scoreField);
        return mPlayers.size()-1;
    }
    
    private void removePlayerInternal(Player p) {
        if (mPlayers.remove(p)) {
            removeView(p);
            mScoreFields.removeView(p.mScoreField);
            realignPlayers();
        }
    }
    
    private void realignPlayers() {
        final int N = mPlayers.size();
        float x = (mWidth - (N-1) * PARAMS.PLAYER_SIZE) / 2;
        for (int i=0; i<N; i++) {
            final Player p = mPlayers.get(i);
            p.setX(x);
            x += PARAMS.PLAYER_SIZE;
        }
    }
    
    private void clearPlayers() {
        while (mPlayers.size() > 0) {
            removePlayerInternal(mPlayers.get(0));
        }
    }
    
    public void setupPlayers(int num) {
        clearPlayers();
        for (int i=0; i<num; i++) {
            addPlayerInternal(Player.create(this));
        }
    }
    
    public void addPlayer() {
        if (getNumPlayers() == MAX_PLAYERS) return;
        addPlayerInternal(Player.create(this));
    }
    
    public int getNumPlayers() {
        return mPlayers.size();
    }
    
    public void removePlayer() {
        if (getNumPlayers() == MIN_PLAYERS) return;
        removePlayerInternal(mPlayers.get(mPlayers.size() - 1));
    }
    
    public void changeScene() {
        mScene = (mScene + 1) % SCENE_COUNT;
        L("Changed scene to: " + mScene + " (CITY=0, TX=1, ZRH=2)");
        reset(); // Reset the game to apply new scenery
    }
    
    public void changePlayerColors() {
        L("Changing player colors for " + mPlayers.size() + " players");
        for (Player p : mPlayers) {
            p.changeColor();
        }
    }
    
    private void thump(int playerIndex, long ms) {
        if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            return;
        }
        if (playerIndex < mGameControllers.size()) {
            int controllerId = mGameControllers.get(playerIndex);
            InputDevice dev = InputDevice.getDevice(controllerId);
            if (dev != null && dev.getVibrator().hasVibrator()) {
                dev.getVibrator().vibrate(
                        (long) (ms * CONTROLLER_VIBRATION_MULTIPLIER),
                        mAudioAttrs);
                return;
            }
        }
        mVibrator.vibrate(ms, mAudioAttrs);
    }  
  public void reset() {
        L("reset");
        bonusPoints = 0;
        currentLevel = 0;
        
        final Drawable sky = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                SKIES[mTimeOfDay]
        );
        sky.setDither(true);
        setBackground(sky);
        
        mFlipped = frand() > 0.5f;
        setScaleX(mFlipped ? -1 : 1);
        
        int i = getChildCount();
        while (i-->0) {
            final View v = getChildAt(i);
            if (v instanceof GameView) {
                removeViewAt(i);
            }
        }
        
        mObstaclesInPlay.clear();
        mPoints.clear();
        mBonusItems.clear();
        mCurrentPipeId = 0;
        
        mWidth = getWidth();
        mHeight = getHeight();
        
        // Add sun/moon
        boolean showingSun = (mTimeOfDay == DAY || mTimeOfDay == SUNSET) && frand() > 0.25;
        if (showingSun) {
            final Star sun = new Star(getContext());
            sun.setBackgroundResource(R.drawable.sun);
            final int w = getResources().getDimensionPixelSize(R.dimen.sun_size);
            sun.setTranslationX(frand(w, mWidth-w));
            if (mTimeOfDay == DAY) {
                sun.setTranslationY(frand(w, (mHeight * 0.66f)));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    sun.getBackground().setTint(0);
                }
            } else {
                sun.setTranslationY(frand(mHeight * 0.66f, mHeight - w));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    sun.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
                    sun.getBackground().setTint(0xC0FF8000);
                }
            }
            addView(sun, new LayoutParams(w, w));
        }
        
        if (!showingSun) {
            final boolean dark = mTimeOfDay == NIGHT || mTimeOfDay == TWILIGHT;
            final float ff = frand();
            if ((dark && ff < 0.75f) || ff < 0.5f) {
                final Star moon = new Star(getContext());
                moon.setBackgroundResource(R.drawable.moon);
                moon.getBackground().setAlpha(dark ? 255 : 128);
                moon.setScaleX(frand() > 0.5 ? -1 : 1);
                moon.setRotation(moon.getScaleX() * frand(5, 30));
                final int w = getResources().getDimensionPixelSize(R.dimen.sun_size);
                moon.setTranslationX(frand(w, mWidth - w));
                moon.setTranslationY(frand(w, mHeight - w));
                addView(moon, new LayoutParams(w, w));
            }
        }
        
        // Add scenery
        final int mh = mHeight / 6;
        final boolean cloudless = frand() < 0.25;
        final int N = 20;
        for (i=0; i<N; i++) {
            final float r1 = frand();
            final Scenery s;
            if (false && HAVE_STARS && r1 < 0.3 && mTimeOfDay != DAY) { // Temporarily disable scenery stars
                s = new Star(getContext());
            } else if (r1 < 0.6 && !cloudless) {
                s = new Cloud(getContext());
            } else {
                switch (mScene) {
                    case SCENE_ZRH:
                        s = new Mountain(getContext());
                        break;
                    case SCENE_TX:
                        s = new Cactus(getContext());
                        break;
                    case SCENE_CITY:
                    default:
                        s = new Building(getContext());
                        break;
                }
                s.z = (float) i / N;
                s.v = 0.85f * s.z;
                if (mScene == SCENE_CITY) {
                    s.setBackgroundColor(Color.GRAY);
                    s.h = irand(PARAMS.BUILDING_HEIGHT_MIN, mh);
                }
                final int c = (int)(255f*s.z);
                final Drawable bg = s.getBackground();
                if (bg != null) bg.setColorFilter(Color.rgb(c,c,c), PorterDuff.Mode.MULTIPLY);
            }
            final LayoutParams lp = new LayoutParams(s.w, s.h);
            if (s instanceof Building) {
                lp.gravity = Gravity.BOTTOM;
            } else {
                lp.gravity = Gravity.TOP;
                final float r = frand();
                if (s instanceof Star) {
                    lp.topMargin = (int) (r * r * mHeight);
                } else {
                    lp.topMargin = (int) (1 - r*r * mHeight/2) + mHeight/2;
                }
            }
            addView(s, lp);
            s.setTranslationX(frand(-lp.width, mWidth + lp.width));
        }
        
        for (Player p : mPlayers) {
            addView(p);
            p.reset();
        }
        realignPlayers();
        
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = new TimeAnimator();
        mAnim.setTimeListener(new TimeAnimator.TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator timeAnimator, long t, long dt) {
                step(t, dt);
            }
        });
    }
    
    public void start(boolean startPlaying) {
        L("start(startPlaying=%s)", startPlaying ? "true" : "false");
        if (startPlaying && mCountdown <= 0) {
            showSplash();
            
            if (mSplash != null) {
                mSplash.findViewById(R.id.play_button).setEnabled(false);
                final View playImage = mSplash.findViewById(R.id.play_button_image);
                final TextView playText = (TextView) mSplash.findViewById(R.id.play_button_text);
                
                playImage.animate().alpha(0f);
                playText.animate().alpha(1f);
                
                mCountdown = 3;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mCountdown == 0) {
                            startPlaying();
                        } else {
                            postDelayed(this, 500);
                        }
                        playText.setText(String.valueOf(mCountdown));
                        mCountdown--;
                    }
                });
            }
        }
        
        for (Player p : mPlayers) {
            p.setVisibility(View.INVISIBLE);
        }
        
        if (!mAnimating) {
            mAnim.start();
            mAnimating = true;
        }
    }
    
    public void hideSplash() {
        if (mSplash != null && mSplash.getVisibility() == View.VISIBLE) {
            mSplash.setClickable(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSplash.animate().alpha(0).translationZ(0).setDuration(300).withEndAction(
                        new Runnable() {
                            @Override
                            public void run() {
                                mSplash.setVisibility(View.GONE);
                            }
                        }
                );
            } else {
                mSplash.setVisibility(View.GONE);
            }
        }
    }
    
    public void showSplash() {
        if (mSplash != null && mSplash.getVisibility() != View.VISIBLE) {
            mSplash.setClickable(true);
            mSplash.setAlpha(0f);
            mSplash.setVisibility(View.VISIBLE);
            mSplash.animate().alpha(1f).setDuration(1000);
            mSplash.findViewById(R.id.play_button_image).setAlpha(1f);
            mSplash.findViewById(R.id.play_button_text).setAlpha(0f);
            mSplash.findViewById(R.id.play_button).setEnabled(true);
            mSplash.findViewById(R.id.play_button).requestFocus();
        }
    }
    
    public void startPlaying() {
        mPlaying = true;
        t = 0;
        mLastPipeTime = getGameTime() - PARAMS.OBSTACLE_PERIOD;
        mLastBonusTime = getGameTime() - PARAMS.BONUS_PERIOD;
        mLastPointTime = getGameTime() - PARAMS.POINT_PERIOD;
        
        hideSplash();
        realignPlayers();
        mTaps = 0;
        
        final int N = mPlayers.size();
        for (int i=0; i<N; i++) {
            final Player p = mPlayers.get(i);
            p.setVisibility(View.VISIBLE);
            p.reset();
            p.start();
            p.boost(-1, -1);
            p.unboost();
        }
    }
    
    public void stop() {
        if (mAnimating) {
            mAnim.cancel();
            mAnim = null;
            mAnimating = false;
            mPlaying = false;
            mTimeOfDay = irand(0, SKIES.length - 1);
            mScene = irand(0, SCENE_COUNT);
            mFrozen = true;
            
            for (Player p : mPlayers) {
                p.die();
            }
            
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    mFrozen = false;
                }
            }, 250);
        }
    }
    
    // Utility methods
    public static final float lerp(float x, float a, float b) {
        return (b - a) * x + a;
    }
    
    public static final float rlerp(float v, float a, float b) {
        return (v - a) / (b - a);
    }
    
    public static final float clamp(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }
    
    public static final float frand() {
        return (float) Math.random();
    }
    
    public static final float frand(float a, float b) {
        return lerp(frand(), a, b);
    }
    
    public static final int irand(int a, int b) {
        return Math.round(frand((float) a, (float) b));
    }
    
    public static int pick(int[] l) {
        return l[irand(0, l.length-1)];
    }
    
    public static int randInt(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }
    
    private ObstacleType getRandomObstacleType() {
        return frand() > 0.5f ? ObstacleType.MARSHMALLOW : ObstacleType.LOLLIPOP;
    }
    
    private void updateDifficulty() {
        // Adjust difficulty based on current level
        switch (currentDifficulty) {
            case EASY:
                if (currentLevel >= 10) PARAMS.OBSTACLE_GAP = (int)(PARAMS.OBSTACLE_GAP * 0.9f);
                break;
            case NORMAL:
                if (currentLevel >= 5) PARAMS.OBSTACLE_GAP = (int)(PARAMS.OBSTACLE_GAP * 0.85f);
                if (currentLevel >= 15) PARAMS.G = (int)(PARAMS.G * 1.1f);
                break;
            case HARD:
                if (currentLevel >= 3) PARAMS.OBSTACLE_GAP = (int)(PARAMS.OBSTACLE_GAP * 0.8f);
                if (currentLevel >= 8) PARAMS.G = (int)(PARAMS.G * 1.2f);
                break;
        }
    } 
   private void step(long t_ms, long dt_ms) {
        t = t_ms / 1000f;
        dt = dt_ms / 1000f;
        
        if (DEBUG) {
            t *= DEBUG_SPEED_MULTIPLIER;
            dt *= DEBUG_SPEED_MULTIPLIER;
        }
        
        // 1. Move all objects and update bounds
        final int N = getChildCount();
        int i = 0;
        for (; i<N; i++) {
            final View v = getChildAt(i);
            if (v instanceof GameView) {
                ((GameView) v).step(t_ms, dt_ms, t, dt);
            }
        }
        
        // 2. Spawn bonus items
        if (mPlaying && (t - mLastBonusTime) > PARAMS.BONUS_PERIOD) {
            mLastBonusTime = t;
            final BonusItem bonus = new BonusItem(getContext());
            addView(bonus, new LayoutParams(bonus.size, bonus.size, Gravity.CENTER_VERTICAL|Gravity.LEFT));
            bonus.setTranslationX(mWidth);
            bonus.setTranslationY(frand(bonus.size, mHeight - bonus.size));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bonus.setTranslationZ(PARAMS.OBSTACLE_Z);
            }
            mBonusItems.add(bonus);
        }
        
        // 2.5. Spawn collectable points (stars) - Fixed frequency
        if (mPlaying && (t - mLastPointTime) > PARAMS.POINT_PERIOD) {
            mLastPointTime = t;
            L("Spawning star - t=" + t + ", period=" + PARAMS.POINT_PERIOD + ", total stars before: " + mPoints.size());
            
            final Point point = new Point(getContext(), (float) PARAMS.OBSTACLE_WIDTH / 2);
            
            // Use half the obstacle width for star size (like Basis version)
            final int starSize = PARAMS.OBSTACLE_WIDTH / 2;
            addView(point, new LayoutParams(starSize, starSize, Gravity.CENTER_VERTICAL | Gravity.LEFT));
            point.setTranslationX(mWidth + 50); // Start just off screen
            point.setTranslationY(frand(100, mHeight - 100)); // Random Y position with margins
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                point.setTranslationZ(PARAMS.OBSTACLE_Z);
            }
            
            mPoints.add(point);
            L("Created star #" + mPoints.size() + " - size: " + starSize + "x" + starSize + " at " + point.getTranslationX() + "," + point.getTranslationY());
        }
        
        if (mPlaying) {
            int livingPlayers = 0;
            for (i = 0; i < mPlayers.size(); i++) {
                final Player p = getPlayer(i);
                if (p.mAlive) {
                    // Check for altitude
                    if (p.below(mHeight)) {
                        if (DEBUG_IDDQD) {
                            poke(i);
                            unpoke(i);
                        } else {
                            L("player %d hit the floor", i);
                            playSound(R.raw.crash);
                            thump(i, 80);
                            p.die();
                        }
                    }
                    
                    // Check for bonus collection
                    for (int j = mBonusItems.size(); j-- > 0; ) {
                        final BonusItem bonus = mBonusItems.get(j);
                        if (bonus.intersects(p) && !DEBUG_IDDQD) {
                            L("player collected bonus");
                            playSound(R.raw.bonus);
                            bonusPoints += 5;
                            p.addScore(5);
                            removeView(bonus);
                            mBonusItems.remove(bonus);
                        }
                    }
                    
                    // Check for point (star) collection
                    for (int j = mPoints.size(); j-- > 0; ) {
                        final Point point = mPoints.get(j);
                        if (!point.collected && point.intersects(p) && !DEBUG_IDDQD) {
                            L("STAR COLLECTED! Player at " + p.getTranslationX() + "," + p.getTranslationY() + " collected star at " + point.getTranslationX() + "," + point.getTranslationY());
                            point.collected = true; // Mark as collected immediately
                            
                            // Play collected sound
                            playSound(R.raw.collected);
                            bonusPoints += 2;
                            p.addScore(2);
                            
                            // Remove from list immediately
                            mPoints.remove(j);
                            
                            // Animate the star disappearing - simpler animation
                            point.animate()
                                .alpha(0.0f)
                                .setDuration(200)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        removeView(point);
                                    }
                                });
                        }
                    }
                    
                    // Check for obstacles
                    int maxPassedStem = 0;
                    for (int j = mObstaclesInPlay.size(); j-- > 0; ) {
                        final Obstacle ob = mObstaclesInPlay.get(j);
                        if (ob.intersects(p) && !DEBUG_IDDQD) {
                            L("player hit an obstacle");
                            playSound(R.raw.crash);
                            thump(i, 80);
                            p.die();
                        } else if (ob.cleared(p)) {
                            if (ob instanceof Stem) {
                                maxPassedStem = Math.max(maxPassedStem, ((Stem)ob).id);
                            }
                        }
                    }
                    
                    if (maxPassedStem > p.mScore) {
                        playSound(R.raw.score);
                        currentLevel++;
                        p.addScore(1);
                        updateDifficulty();
                    }
                }
                if (p.mAlive) livingPlayers++;
            }
            
            if (livingPlayers == 0) {
                stop();
                mTaps = 0;
            }
        }
        
        // 3. Time for more obstacles!
        if (mPlaying && (t - mLastPipeTime) > PARAMS.OBSTACLE_PERIOD) {
            mLastPipeTime = t;
            mCurrentPipeId++;
            
            final int obstacley = (int)(frand() * (mHeight - 2*PARAMS.OBSTACLE_MIN - PARAMS.OBSTACLE_GAP)) + PARAMS.OBSTACLE_MIN;
            final int inset = (PARAMS.OBSTACLE_WIDTH - PARAMS.OBSTACLE_STEM_WIDTH) / 2;
            final int yinset = PARAMS.OBSTACLE_WIDTH/2;
            final int d1 = irand(0, 250);
            
            final Obstacle s1 = new Stem(getContext(), obstacley - yinset, false);
            addView(s1, new LayoutParams(PARAMS.OBSTACLE_STEM_WIDTH, (int) s1.h, Gravity.TOP|Gravity.LEFT));
            s1.setTranslationX(mWidth + inset);
            s1.setTranslationY(-s1.h - yinset);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                s1.setTranslationZ(PARAMS.OBSTACLE_Z * 0.75f);
            }
            s1.animate().translationY(0).setStartDelay(d1).setDuration(250);
            mObstaclesInPlay.add(s1);
            
            final Obstacle p1 = new Pop(getContext(), PARAMS.OBSTACLE_WIDTH, getRandomObstacleType());
            addView(p1, new LayoutParams(PARAMS.OBSTACLE_WIDTH, PARAMS.OBSTACLE_WIDTH, Gravity.TOP|Gravity.LEFT));
            p1.setTranslationX(mWidth);
            p1.setTranslationY(-PARAMS.OBSTACLE_WIDTH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                p1.setTranslationZ(PARAMS.OBSTACLE_Z);
            }
            p1.setScaleX(0.25f);
            p1.setScaleY(-0.25f);
            p1.animate().translationY(s1.h - inset).scaleX(1f).scaleY(-1f).setStartDelay(d1).setDuration(250);
            mObstaclesInPlay.add(p1);
            
            final int d2 = irand(0, 250);
            final Obstacle s2 = new Stem(getContext(), mHeight - obstacley - PARAMS.OBSTACLE_GAP - yinset, true);
            addView(s2, new LayoutParams(PARAMS.OBSTACLE_STEM_WIDTH, (int) s2.h, Gravity.TOP|Gravity.LEFT));
            s2.setTranslationX(mWidth + inset);
            s2.setTranslationY(mHeight + yinset);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                s2.setTranslationZ(PARAMS.OBSTACLE_Z * 0.75f);
            }
            s2.animate().translationY(mHeight - s2.h).setStartDelay(d2).setDuration(400);
            mObstaclesInPlay.add(s2);
            
            final Obstacle p2 = new Pop(getContext(), PARAMS.OBSTACLE_WIDTH, getRandomObstacleType());
            addView(p2, new LayoutParams(PARAMS.OBSTACLE_WIDTH, PARAMS.OBSTACLE_WIDTH, Gravity.TOP|Gravity.LEFT));
            p2.setTranslationX(mWidth);
            p2.setTranslationY(mHeight);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                p2.setTranslationZ(PARAMS.OBSTACLE_Z);
            }
            p2.setScaleX(0.25f);
            p2.setScaleY(0.25f);
            p2.animate().translationY(mHeight - s2.h - yinset).scaleX(1f).scaleY(1f).setStartDelay(d2).setDuration(400);
            mObstaclesInPlay.add(p2);
        }
        
        // 4. Handle edge of screen
        while (i-->0) {
            final View v = getChildAt(i);
            if (v instanceof Point) {
                if (v.getTranslationX() + v.getWidth() < 0) {
                    removeViewAt(i);
                    mPoints.remove(v);
                }
            } else if (v instanceof Obstacle) {
                if (v.getTranslationX() + v.getWidth() < 0) {
                    removeViewAt(i);
                    mObstaclesInPlay.remove(v);
                }
            } else if (v instanceof BonusItem) {
                if (v.getTranslationX() + v.getWidth() < 0) {
                    removeViewAt(i);
                    mBonusItems.remove(v);
                }
            } else if (v instanceof Scenery) {
                final Scenery s = (Scenery) v;
                if (v.getTranslationX() + s.w < 0) {
                    v.setTranslationX(getWidth());
                }
            }
        }
        
        if (SHOW_TOUCHES || DEBUG_DRAW) invalidate();
    }
    
    // Input handling
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        L("touch: %s", ev);
        final int actionIndex = ev.getActionIndex();
        final float x = ev.getX(actionIndex);
        final float y = ev.getY(actionIndex);
        int playerIndex = (int) (getNumPlayers() * (x / getWidth()));
        if (mFlipped) playerIndex = getNumPlayers() - 1 - playerIndex;
        
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                poke(playerIndex, x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                unpoke(playerIndex);
                return true;
        }
        return false;
    }
    
    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        L("trackball: %s", ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                poke(0);
                return true;
            case MotionEvent.ACTION_UP:
                unpoke(0);
                return true;
        }
        return false;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        L("keyDown: %d", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                int player = getControllerPlayer(ev.getDeviceId());
                poke(player);
                return true;
        }
        return false;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev) {
        L("keyUp: %d", keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                int player = getControllerPlayer(ev.getDeviceId());
                unpoke(player);
                return true;
        }
        return false;
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        L("generic: %s", ev);
        return false;
    }
    
    private void poke(int playerIndex) {
        poke(playerIndex, -1, -1);
    }
    
    private void poke(int playerIndex, float x, float y) {
        L("poke(%d)", playerIndex);
        if (mFrozen) return;
        if (!mAnimating) {
            reset();
        }
        if (!mPlaying) {
            start(true);
        } else {
            final Player p = getPlayer(playerIndex);
            if (p == null) return;
            playSound(R.raw.jump);
            p.boost(x, y);
            mTaps++;
            if (DEBUG) {
                p.dv *= DEBUG_SPEED_MULTIPLIER;
                p.animate().setDuration((long) (200 / DEBUG_SPEED_MULTIPLIER));
            }
        }
    }
    
    private void unpoke(int playerIndex) {
        L("unboost(%d)", playerIndex);
        if (mFrozen || !mAnimating || !mPlaying) return;
        final Player p = getPlayer(playerIndex);
        if (p == null) return;
        p.unboost();
    }
    
    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        
        if (SHOW_TOUCHES) {
            for (Player p : mPlayers) {
                if (p.mTouchX > 0) {
                    mTouchPaint.setColor(0x80FFFFFF & p.color);
                    mPlayerTracePaint.setColor(0x80FFFFFF & p.color);
                    float x1 = p.mTouchX;
                    float y1 = p.mTouchY;
                    c.drawCircle(x1, y1, 100, mTouchPaint);
                    float x2 = p.getX() + p.getPivotX();
                    float y2 = p.getY() + p.getPivotY();
                    float angle = PI_2 - (float) Math.atan2(x2-x1, y2-y1);
                    x1 += 100*Math.cos(angle);
                    y1 += 100*Math.sin(angle);
                    c.drawLine(x1, y1, x2, y2, mPlayerTracePaint);
                }
            }
        }
        
        if (!DEBUG_DRAW) return;
        
        final Paint pt = new Paint();
        pt.setColor(0xFFFFFFFF);
        for (Player p : mPlayers) {
            final int L = p.corners.length;
            final int N = L / 2;
            for (int i = 0; i < N; i++) {
                final int x = (int) p.corners[i * 2];
                final int y = (int) p.corners[i * 2 + 1];
                c.drawCircle(x, y, 4, pt);
                c.drawLine(x, y,
                        p.corners[(i * 2 + 2) % L],
                        p.corners[(i * 2 + 3) % L],
                        pt);
            }
        }
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(getResources().getDisplayMetrics().density);
        
        final int M = getChildCount();
        pt.setColor(0x8000FF00);
        for (int i=0; i<M; i++) {
            final View v = getChildAt(i);
            if (v instanceof Player) continue;
            if (!(v instanceof GameView)) continue;
            if (v instanceof Pop) {
                final Pop pop = (Pop) v;
                c.drawCircle(pop.cx, pop.cy, pop.r, pt);
            } else {
                final Rect r = new Rect();
                v.getHitRect(r);
                c.drawRect(r, pt);
            }
        }
        pt.setColor(Color.BLACK);
        final StringBuilder sb = new StringBuilder("obstacles: ");
        for (Obstacle ob : mObstaclesInPlay) {
            sb.append(ob.hitRect.toShortString());
            sb.append(" ");
        }
        pt.setTextSize(20f);
        c.drawText(sb.toString(), 20, 100, pt);
    }
    
    static final Rect sTmpRect = new Rect();
    
    private interface GameView {
        public void step(long t_ms, long dt_ms, float t, float dt);
    }
    
    // Player class
    public static class Player extends ImageView implements GameView {
        public float dv;
        public int color;
        private MLand mLand;
        private boolean mBoosting;
        public float mTouchX = -1, mTouchY = -1;
        public boolean mAlive;
        public int mScore;
        public TextView mScoreField;
        
        private final int[] sColors = new int[] {
            0xFFDB4437, 0xFF3B78E7, 0xFFF4B400, 0xFF0F9D58, 0xFF7B1880, 0xFF9E9E9E,
            0xFF795548, 0xFF607D8B, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
            0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
            0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF9C27B0, 0xFFE91E63
        };
        
        static int sNextColor = 0;
        
        private final float[] sHull = new float[] {
            0.3f,  0f,    // left antenna
            0.7f,  0f,    // right antenna
            0.92f, 0.33f, // off the right shoulder of Orion
            0.92f, 0.75f, // right hand (our right, not his right)
            0.6f,  1f,    // right foot
            0.4f,  1f,    // left foot BLUE!
            0.08f, 0.75f, // sinistram
            0.08f, 0.33f, // cold shoulder
        };
        
        public final float[] corners = new float[sHull.length];
        
        public static Player create(MLand land) {
            final Player p = new Player(land.getContext());
            p.mLand = land;
            p.reset();
            p.setVisibility(View.INVISIBLE);
            land.addView(p, new LayoutParams(PARAMS.PLAYER_SIZE, PARAMS.PLAYER_SIZE));
            return p;
        }
        
        private void setScore(int score) {
            mScore = score;
            if (mScoreField != null) {
                mScoreField.setText(DEBUG_IDDQD ? "??" : String.valueOf(score));
            }
        }
        
        public int getScore() {
            return mScore;
        }
        
        public void addScore(int incr) {
            setScore(mScore + incr);
        }
        
        public void setScoreField(TextView tv) {
            mScoreField = tv;
            if (tv != null) {
                setScore(mScore);
                mScoreField.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                mScoreField.setTextColor(luma(color) > 0.7f ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        
        public void reset() {
            setY(mLand.mHeight / 2 + (int)(Math.random() * PARAMS.PLAYER_SIZE) - PARAMS.PLAYER_SIZE / 2);
            setScore(0);
            setScoreField(mScoreField);
            mBoosting = false;
            dv = 0;
        }
        
        public Player(Context context) {
            super(context);
            setBackgroundResource(R.drawable.f1android);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
                color = sColors[(sNextColor++) % sColors.length];
                getBackground().setTint(color);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        final int w = view.getWidth();
                        final int h = view.getHeight();
                        final int ix = (int) (w * 0.3f);
                        final int iy = (int) (h * 0.2f);
                        outline.setRect(ix, iy, w - ix, h - iy);
                    }
                });
            } else {
                color = sColors[(sNextColor++) % sColors.length];
            }
        }
        
        public void prepareCheckIntersections() {
            final int inset = (PARAMS.PLAYER_SIZE - PARAMS.PLAYER_HIT_SIZE)/2;
            final int scale = PARAMS.PLAYER_HIT_SIZE;
            final int N = sHull.length/2;
            for (int i=0; i<N; i++) {
                corners[i*2]   = scale * sHull[i*2]   + inset;
                corners[i*2+1] = scale * sHull[i*2+1] + inset;
            }
            final Matrix m = getMatrix();
            m.mapPoints(corners);
        }
        
        public boolean below(int h) {
            final int N = corners.length/2;
            for (int i=0; i<N; i++) {
                final int y = (int) corners[i*2+1];
                if (y >= h) return true;
            }
            return false;
        }
        
        public void step(long t_ms, long dt_ms, float t, float dt) {
            if (!mAlive) {
                setTranslationX(getTranslationX()-PARAMS.TRANSLATION_PER_SEC*dt);
                return;
            }
            
            if (mBoosting) {
                dv = -PARAMS.BOOST_DV;
            } else {
                dv += PARAMS.G;
            }
            if (dv < -PARAMS.MAX_V) dv = -PARAMS.MAX_V;
            else if (dv > PARAMS.MAX_V) dv = PARAMS.MAX_V;
            
            final float y = getTranslationY() + dv * dt;
            setTranslationY(y < 0 ? 0 : y);
            setRotation(90 + lerp(clamp(rlerp(dv, PARAMS.MAX_V, -1 * PARAMS.MAX_V)), 90, -90));
            prepareCheckIntersections();
        }
        
        public void boost(float x, float y) {
            mTouchX = x;
            mTouchY = y;
            boost();
        }
        
        public void boost() {
            mBoosting = true;
            dv = -PARAMS.BOOST_DV;
            animate().cancel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                animate().scaleX(1.25f).scaleY(1.25f).translationZ(PARAMS.PLAYER_Z_BOOST).setDuration(100);
            } else {
                animate().scaleX(1.25f).scaleY(1.25f).setDuration(100);
            }
            setScaleX(1.25f);
            setScaleY(1.25f);
        }
        
        public void unboost() {
            mBoosting = false;
            mTouchX = mTouchY = -1;
            animate().cancel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                animate().scaleX(1f).scaleY(1f).translationZ(PARAMS.PLAYER_Z).setDuration(200);
            } else {
                animate().scaleX(1f).scaleY(1f).setDuration(200);
            }
        }
        
        public void die() {
            mAlive = false;
        }
        
        public void start() {
            mAlive = true;
        }
        
        public void changeColor() {
            int oldColor = color;
            color = sColors[(sNextColor++) % sColors.length];
            L("Player color changed from " + Integer.toHexString(oldColor) + " to " + Integer.toHexString(color));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getBackground().setTint(color);
            } else {
                getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }
    
    // Obstacle base class
    private class Obstacle extends View implements GameView {
        public float h;
        public final Rect hitRect = new Rect();
        
        public Obstacle(Context context, float h) {
            super(context);
            setBackgroundColor(0xFFFF0000);
            this.h = h;
        }
        
        public boolean intersects(Player p) {
            final int N = p.corners.length/2;
            for (int i=0; i<N; i++) {
                final int x = (int) p.corners[i*2];
                final int y = (int) p.corners[i*2+1];
                if (hitRect.contains(x, y)) return true;
            }
            return false;
        }
        
        public boolean cleared(Player p) {
            final int N = p.corners.length/2;
            for (int i=0; i<N; i++) {
                final int x = (int) p.corners[i*2];
                if (hitRect.right >= x) return false;
            }
            return true;
        }
        
        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            setTranslationX(getTranslationX()-PARAMS.TRANSLATION_PER_SEC*dt);
            getHitRect(hitRect);
        }
    }
    
    // Bonus item class
    private class BonusItem extends View implements GameView {
        public int size;
        private final Rect hitRect = new Rect();
        
        public BonusItem(Context context) {
            super(context);
            setBackgroundResource(R.drawable.scorecard); // Use scorecard instead of star_point
            size = irand(PARAMS.STAR_SIZE_MIN, PARAMS.STAR_SIZE_MAX);
            setRotation(frand(0, 360));
        }
        
        public boolean intersects(Player p) {
            final int N = p.corners.length/2;
            for (int i=0; i<N; i++) {
                final int x = (int) p.corners[i*2];
                final int y = (int) p.corners[i*2+1];
                if (hitRect.contains(x, y)) return true;
            }
            return false;
        }
        
        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            setTranslationX(getTranslationX()-PARAMS.TRANSLATION_PER_SEC*dt);
            setRotation(getRotation() + dt * 45);
            getHitRect(hitRect);
        }
    }    
// Pop obstacle class
    private class Pop extends Obstacle {
        int mRotate;
        int cx, cy, r;
        Drawable antenna, eyes, mouth;
        ObstacleType type;
        
        public Pop(Context context, float h, ObstacleType obstacleType) {
            super(context, h);
            type = obstacleType;
            
            if (type == ObstacleType.MARSHMALLOW) {
                setBackgroundResource(R.drawable.mm_head);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    antenna = context.getDrawable(pick(ANTENNAE));
                    if (frand() > 0.5f) {
                        eyes = context.getDrawable(pick(EYES));
                        if (frand() > 0.8f) {
                            mouth = context.getDrawable(pick(MOUTHS));
                        }
                    }
                    setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            final int pad = (int) (getWidth() * 1f/6);
                            outline.setOval(pad, pad, getWidth()-pad, getHeight()-pad);
                        }
                    });
                }
            } else {
                // Lollipop style - use the full POPS array with custom graphics
                int idx = randInt(0, POPS.length - 1);
                setBackgroundResource(POPS[idx]);
                setScaleX(frand() < 0.5f ? -1f : 1f);
                
                // Set rotation based on ROLLINGPOP configuration
                if (idx < ROLLINGPOP.length && ROLLINGPOP[idx] == 1) {
                    mRotate = frand() < 0.5f ? -1 : 1;
                } else {
                    mRotate = 0;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            final int pad = (int) (getWidth() * 0.02f);
                            outline.setOval(pad, pad, getWidth()-pad, getHeight()-pad);
                        }
                    });
                }
            }
        }
        
        public boolean intersects(Player p) {
            final int N = p.corners.length/2;
            for (int i=0; i<N; i++) {
                final int x = (int) p.corners[i*2];
                final int y = (int) p.corners[i*2+1];
                if (Math.hypot(x-cx, y-cy) <= r) return true;
            }
            return false;
        }
        
        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            super.step(t_ms, dt_ms, t, dt);
            if (mRotate != 0) {
                setRotation(getRotation() + dt * 45 * mRotate);
            }
            cx = (hitRect.left + hitRect.right)/2;
            cy = (hitRect.top + hitRect.bottom)/2;
            r = getWidth() / 3;
        }
        
        @Override
        public void onDraw(Canvas c) {
            super.onDraw(c);
            if (antenna != null) {
                antenna.setBounds(0, 0, c.getWidth(), c.getHeight());
                antenna.draw(c);
            }
            if (eyes != null) {
                eyes.setBounds(0, 0, c.getWidth(), c.getHeight());
                eyes.draw(c);
            }
            if (mouth != null) {
                mouth.setBounds(0, 0, c.getWidth(), c.getHeight());
                mouth.draw(c);
            }
        }
    }
    
    // Stem obstacle class
    private class Stem extends Obstacle {
        Paint mPaint = new Paint();
        Path mShadow = new Path();
        GradientDrawable mGradient = new GradientDrawable();
        boolean mDrawShadow;
        Path mJandystripe;
        Paint mPaint2;
        int id;
        
        public Stem(Context context, float h, boolean drawShadow) {
            super(context, h);
            id = mCurrentPipeId;
            mDrawShadow = drawShadow;
            setBackground(null);
            mGradient.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            mPaint.setColor(0xFF000000);
            mPaint.setColorFilter(new PorterDuffColorFilter(0x22000000, PorterDuff.Mode.MULTIPLY));
            
            if (frand() < 0.01f) {
                mGradient.setColors(new int[]{0xFFFFFFFF, 0xFFDDDDDD});
                mJandystripe = new Path();
                mPaint2 = new Paint();
                mPaint2.setColor(0xFFFF0000);
                mPaint2.setColorFilter(new PorterDuffColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY));
            } else {
                mGradient.setColors(new int[]{0xFFBCAAA4, 0xFFA1887F});
            }
        }
        
        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            setWillNotDraw(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRect(0, 0, getWidth(), getHeight());
                    }
                });
            }
        }
        
        @Override
        public void onDraw(Canvas c) {
            final int w = c.getWidth();
            final int h = c.getHeight();
            mGradient.setGradientCenter(w * 0.75f, 0);
            mGradient.setBounds(0, 0, w, h);
            mGradient.draw(c);
            
            if (mJandystripe != null) {
                mJandystripe.reset();
                mJandystripe.moveTo(0, w);
                mJandystripe.lineTo(w, 0);
                mJandystripe.lineTo(w, 2 * w);
                mJandystripe.lineTo(0, 3 * w);
                mJandystripe.close();
                for (int y=0; y<h; y+=4*w) {
                    c.drawPath(mJandystripe, mPaint2);
                    mJandystripe.offset(0, 4 * w);
                }
            }
            
            if (!mDrawShadow) return;
            mShadow.reset();
            mShadow.moveTo(0, 0);
            mShadow.lineTo(w, 0);
            mShadow.lineTo(w, PARAMS.OBSTACLE_WIDTH * 0.4f + w*1.5f);
            mShadow.lineTo(0, PARAMS.OBSTACLE_WIDTH * 0.4f);
            mShadow.close();
            c.drawPath(mShadow, mPaint);
        }
    }
    
    // Scenery base class
    private class Scenery extends FrameLayout implements GameView {
        public float z;
        public float v;
        public int h, w;
        
        public Scenery(Context context) {
            super(context);
        }
        
        @Override
        public void step(long t_ms, long dt_ms, float t, float dt) {
            setTranslationX(getTranslationX() - PARAMS.TRANSLATION_PER_SEC * dt * v);
        }
    }
    
    // Building scenery
    private class Building extends Scenery {
        public Building(Context context) {
            super(context);
            w = irand(PARAMS.BUILDING_WIDTH_MIN, PARAMS.BUILDING_WIDTH_MAX);
            h = 0; // will be setup later, along with z
        }
    }
    
    // Cactus scenery
    private class Cactus extends Building {
        public Cactus(Context context) {
            super(context);
            setBackgroundResource(pick(CACTI));
            w = h = irand(PARAMS.BUILDING_WIDTH_MAX / 4, PARAMS.BUILDING_WIDTH_MAX / 2);
        }
    }
    
    // Mountain scenery
    private class Mountain extends Building {
        public Mountain(Context context) {
            super(context);
            setBackgroundResource(pick(MOUNTAINS));
            w = h = irand(PARAMS.BUILDING_WIDTH_MAX / 2, PARAMS.BUILDING_WIDTH_MAX);
            z = 0;
        }
    }
    
    // Cloud scenery
    private class Cloud extends Scenery {
        public Cloud(Context context) {
            super(context);
            setBackgroundResource(frand() < 0.01f ? R.drawable.cloud_off : R.drawable.cloud);
            getBackground().setAlpha(0x40);
            w = h = irand(PARAMS.CLOUD_SIZE_MIN, PARAMS.CLOUD_SIZE_MAX);
            z = 0;
            v = frand(0.15f, 0.5f);
        }
    }
    
    // Star scenery
    private class Star extends Scenery {
        public Star(Context context) {
            super(context);
            setBackgroundResource(R.drawable.star); // Keep using regular star for scenery
            w = h = irand(PARAMS.STAR_SIZE_MIN, PARAMS.STAR_SIZE_MAX);
            v = z = 0;
        }
    }
    
    // Collectable Point (Star)
    private class Point extends Obstacle {
        int cx, cy, r;
        int mRotate;
        boolean collected = false; // Flag to prevent double collection
        
        public Point(Context context, float h) {
            super(context, h);
            setBackgroundResource(R.drawable.star_point); // Use vector star only
            mRotate = frand() < 0.5f ? -1 : 1;
        }
        
        public boolean intersects(Player p) {
            if (collected) return false;
            
            // Use the same collision detection as the Basis version
            final int N = p.corners.length / 2;
            for (int i = 0; i < N; i++) {
                final int x = (int) p.corners[i * 2];
                final int y = (int) p.corners[i * 2 + 1];
                if (Math.hypot(x - cx, y - cy) <= r) {
                    L("Star collision detected at cx=" + cx + " cy=" + cy + " r=" + r + " with player corner " + x + "," + y);
                    return true;
                }
            }
            return false;
        }
        
        public void step(long t_ms, long dt_ms, float t, float dt) {
            super.step(t_ms, dt_ms, t, dt);
            if (mRotate != 0 && !collected) {
                setRotation(getRotation() + (45.0f * dt * ((float) mRotate)));
            }
            // Update collision detection center and radius - CRITICAL for collision detection
            cx = (hitRect.left + hitRect.right) / 2;
            cy = (hitRect.top + hitRect.bottom) / 2;
            r = getWidth() / 3; // Same as Basis version
        }
    }
}