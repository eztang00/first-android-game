package com.github.eztang00.firstandroidgame.gamelogic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstract class to be extended by the RippleGolfGame class
 * and maybe another simple maze game I'm considering making
 */
public abstract class Game {

    public static final long SECOND_MS = 1000;
    public static final long MINUTE_MS = 60 * SECOND_MS;
    public static final long HOUR_MS = 60 * MINUTE_MS;

    public int width = 0;
    public int height = 0;
    public int level;

    public TreeMap<Long, GameFadeableText> levelText;
    public TreeMap<Long, GameFadeableText> levelTextAtEnd;

    public long levelIntroducingTime;
    protected long levelFadeInTime;
    protected long levelFadeOutTime;
    protected Runnable levelSpecialRules;

    protected ArrayList<GameListener> gameListeners;
    protected GameWonSpecialAnimation gameSpecialAnimation;

    public GameState gameState;

    private transient Thread lastThread = null;

    protected long timeWhenEnteredCurrentGameState;
    public long timeWhenStartedShowingText;
    protected long lastPotentialPauseStartTime = Integer.MAX_VALUE;

    public Game(boolean justPreview) {
        levelText = new TreeMap<>();
        levelTextAtEnd = new TreeMap<>();
        gameListeners = new ArrayList<>();
        gameState = justPreview ? GameState.PREVIEW_LEVEL : GameState.INTRODUCING_LEVEL;
        level = 1;
//        level = 2;
    }

    protected abstract void initiateLevel(Context context, int level, boolean isBecauseRestart);

    protected <T extends Game> void initiateLevel(Context context, GameLevel<T> gameLevelObject, int level, boolean isBecauseRestart) {
        if (!(gameLevelObject instanceof GameLevelWon)) {
            this.level = level;
        }

        if (width != 0 && height != 0) {
            long now = System.currentTimeMillis();

            clearLastLevel();

            if (!(gameLevelObject instanceof GameLevelWon)) {

                boolean wonLastLevel = (gameState == GameState.FINISHING_LEVEL || gameState == GameState.SPECIAL_ANIMATION);
                if (gameState != GameState.PREVIEW_LEVEL) {
                    gameState = GameState.INTRODUCING_LEVEL;
                }
                timeWhenEnteredCurrentGameState = timeWhenStartedShowingText = now;
                lastPotentialPauseStartTime = now;
                // don't process a pause from the last potential pause start time
                // because start times reset

                if (wonLastLevel) {
                    levelIntroducingTime = 1 * SECOND_MS;
                    levelFadeInTime = 1 * SECOND_MS;
                } else {
                    levelIntroducingTime = SECOND_MS / 2;
                    levelFadeInTime = 0; //no point doing it when arrived at level from menu or restart
                }
                levelFadeOutTime = 1 * SECOND_MS; //do before initiate level: default
                gameLevelObject.initiateLevel(context, (T) this, level);
                if (isBecauseRestart) {
                    levelIntroducingTime = 0; //do after initiate level: force
                }
            } else {
                gameSpecialAnimation = ((GameLevelWon) gameLevelObject).getAnimation(width, height);
                gameState = GameState.SPECIAL_ANIMATION;
                timeWhenEnteredCurrentGameState = timeWhenStartedShowingText = now;
                lastPotentialPauseStartTime = now;
                gameLevelObject.initiateLevel(context, (T) this, level);
            }
        }
    }

    protected void update(Context context) {
        long now = System.currentTimeMillis();
        Thread thread = Thread.currentThread();
        if (thread != lastThread) {
            lastThread = thread;
            if (lastPotentialPauseStartTime != Integer.MAX_VALUE) {
                long pauseTime = now - lastPotentialPauseStartTime;
                timeWhenEnteredCurrentGameState += pauseTime;
                timeWhenStartedShowingText += pauseTime;
                processPause(pauseTime);
            }
        }
        switch (gameState) {
            case INTRODUCING_LEVEL:
            case PLAYING_LEVEL:
            case FINISHING_LEVEL:
            case SPECIAL_ANIMATION:
                Iterator<Map.Entry<Long, GameFadeableText>> textIterator = levelText.entrySet().iterator();
                while (textIterator.hasNext()) {
                    Map.Entry<Long, GameFadeableText> text = textIterator.next();
                    if (now - timeWhenStartedShowingText >= text.getKey() + text.getValue().duration) {
                        textIterator.remove();
                    } else if (now - timeWhenStartedShowingText < text.getKey()) {
                        break;
                    }
                }
                break;
        }

        switch (gameState) {
            case INTRODUCING_LEVEL:
                if (now - timeWhenEnteredCurrentGameState >= levelIntroducingTime) {
                    gameState = GameState.PLAYING_LEVEL;
                    timeWhenEnteredCurrentGameState = now;
                }
                break;
            case PLAYING_LEVEL:
                if (levelSpecialRules != null) {
                    levelSpecialRules.run();
                }

                break;
            case FINISHING_LEVEL:
                if (levelText.isEmpty() && now - timeWhenEnteredCurrentGameState >= levelFadeOutTime) {
                    initiateLevel(context, level + 1, false);
                }
                break;
            case SPECIAL_ANIMATION:
                gameSpecialAnimation.updateAnimation();
                break;
        }
        lastPotentialPauseStartTime = now;
    }

    protected void processPause(long pauseTime) {
        if (gameSpecialAnimation != null) {
            gameSpecialAnimation.processPause(pauseTime);
        }
    }

    protected void clearLastLevel() {
        levelText.clear();
        levelTextAtEnd.clear();
        levelSpecialRules = null;
        gameSpecialAnimation = null;
    }

    public void setLevelSpecialRules(Runnable runnable) {
        levelSpecialRules = runnable;
    }

    protected void winLevel(long now) {
        gameState = GameState.FINISHING_LEVEL;
        timeWhenEnteredCurrentGameState = timeWhenStartedShowingText = now;
        levelText.clear();
        levelText.putAll(levelTextAtEnd);
    }

    protected abstract void draw(Canvas canvas);

    protected void drawStuffOnTop(Canvas canvas) {
        if (canvas != null) {
            long now = System.currentTimeMillis();
            switch (gameState) {
                case INTRODUCING_LEVEL:
                case PLAYING_LEVEL:
                case FINISHING_LEVEL:
                case PREVIEW_LEVEL:
                    break;
                case SPECIAL_ANIMATION:
                    gameSpecialAnimation.drawAnimation(canvas);
                    break;
            }
            switch (gameState) {
                case INTRODUCING_LEVEL:
                    if (now - timeWhenEnteredCurrentGameState < levelFadeInTime) {
                        double visibility = ((double) (now - timeWhenEnteredCurrentGameState)) / levelFadeInTime;
                        canvas.drawColor(Color.argb((int) (255 * (1 - visibility)), 255, 255, 255));
                    }
                    break;
                case PLAYING_LEVEL:
                case PREVIEW_LEVEL:
                case SPECIAL_ANIMATION:
                    break;
                case FINISHING_LEVEL:
                    if (now - timeWhenEnteredCurrentGameState < levelFadeOutTime) {
                        double faintness = ((double) (now - timeWhenEnteredCurrentGameState)) / levelFadeOutTime;
                        canvas.drawColor(Color.argb((int) (255 * faintness), 255, 255, 255));
                    } else {
                        canvas.drawColor(Color.rgb(255, 255, 255));
                    }
                    break;
            }
            switch (gameState) {
                case INTRODUCING_LEVEL:
                case PLAYING_LEVEL:
                case FINISHING_LEVEL:
                case SPECIAL_ANIMATION:
                    for (Map.Entry<Long, GameFadeableText> text : levelText.entrySet()) {
                        if (text.getKey() > now - timeWhenStartedShowingText) {
                            break;
                        }
                        text.getValue().draw(now - (text.getKey() + timeWhenStartedShowingText), canvas);
                    }
                    break;
                case PREVIEW_LEVEL:
                    Log.i("me", "drawing preview game");
                    for (Map.Entry<Long, GameFadeableText> text : levelText.entrySet()) {
                        text.getValue().draw(1 * SECOND_MS, canvas);
                    }
                    break;
            }
        }
    }


    public void setSize(Context context, int w, int h) {
        boolean dimensionsChanged = (w != width || h != height);
        this.width = w;
        this.height = h;
        if (dimensionsChanged) {
            initiateLevel(context, level, false);
        }
    }


    public enum GameState {
        INTRODUCING_LEVEL,
        PLAYING_LEVEL,
        FINISHING_LEVEL,
        PREVIEW_LEVEL, // i.e. not actually playing the game
        SPECIAL_ANIMATION;
    }
}
