package com.github.eztang00.firstandroidgame.ui.game;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.github.eztang00.firstandroidgame.R;
import com.github.eztang00.firstandroidgame.SaveAndLoad;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

abstract class Game {

    static final long SECOND_MS = 1000;
    static final long MINUTE_MS = 60 * SECOND_MS;
    static final long HOUR_MS = 60 * MINUTE_MS;

    protected int width = 0;
    protected int height = 0;
    public int level;

    protected TreeMap<Long, GameFadeableText> levelText;
    protected TreeMap<Long, GameFadeableText> levelTextAtEnd;

    protected long levelIntroducingTime;
    protected long levelFadeInTime;
    protected long levelFadeOutTime;
    protected Runnable levelSpecialRules;

    protected ArrayList<GameListener> gameListeners;
    protected GameWonSpecialAnimation gameSpecialAnimation;

    protected GameState gameState;

    private transient Thread lastThread = null;

    protected long timeWhenEnteredCurrentGameState;
    protected long timeWhenStartedShowingText;
    protected long lastPotentialPauseStartTime = Integer.MAX_VALUE;

    public Game(boolean justPreview) {
        levelText = new TreeMap<>();
        levelTextAtEnd = new TreeMap<>();
        gameListeners = new ArrayList<>();
        gameState = justPreview ? GameState.PREVIEW_LEVEL : GameState.INTRODUCING_LEVEL;
        level = 1;
//        level = 2;
    }

    abstract void initiateLevel(Context context, int level, boolean isBecauseRestart);
    
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
                    levelIntroducingTime = SECOND_MS/2;
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

    void setLevelSpecialRules(Runnable runnable) {
        levelSpecialRules = runnable;
    }

    protected void winLevel(long now) {
        gameState = GameState.FINISHING_LEVEL;
        timeWhenEnteredCurrentGameState = timeWhenStartedShowingText = now;
        levelText.clear();
        levelText.putAll(levelTextAtEnd);
    }

    abstract void draw(Canvas canvas);
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


    enum GameState {
        INTRODUCING_LEVEL,
        PLAYING_LEVEL,
        FINISHING_LEVEL,
        PREVIEW_LEVEL, // i.e. not actually playing the game
        SPECIAL_ANIMATION;
    }
}

/**
 * @author Ethan
 * This is my first game for Android
 */
public class RippleGolfGame extends Game {
    static final double RIPPLE_EXPANSION_RATE = 20;
    public int strokes = 0;
    GamePolyarcgon ball;
    GamePolyarcgon hole;
    ArrayList<GameWormhole> wormholes;
    GameForceField ripple;
    double lastFrameDistanceBetweenBallAndRipple;
    int rippleAgeInFrames;
    int numberOfFramesBallIsStuckForRipple;
    int numberOfFramesBallCanBeStuckBeforeStoppingRipple;
    int maxFramesPerRipple;
    final GamePhysicsEngine gamePhysicsEngine;

    public RippleGolfGame(boolean justPreview) {
        super(justPreview);
        gamePhysicsEngine = new GamePhysicsEngine();
        wormholes = new ArrayList<>();
    }

    public void initiateLevel(Context context, int level, boolean isBecauseRestart) {
        RippleGolfGameLevel gameLevelObject = RippleGolfGameLevel.getGameLevel(level);
        super.initiateLevel(context, gameLevelObject, level, isBecauseRestart);

        if (width != 0 && height != 0) {

            notifyStrokesAndParChange(strokes, getPar(level));
        }
    }

    protected void clearLastLevel() {
        super.clearLastLevel();
        strokes = 0;
        wormholes.clear();
        gamePhysicsEngine.removeAllShapes();
        numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3*SECOND_MS/60);
        maxFramesPerRipple = (int) (10*SECOND_MS/60);
    }

    void initiateBall(double x, double y, double radius, double density, GameShapeAdditionalAttributesForDrawingEtc ballMaterial) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();

        ball = builder.withDensity(density).addCircleContour(x, y, radius, true).withAdditionalAttributes(ballMaterial).buildAndReset();
        gamePhysicsEngine.addMovableShape(ball);
    }

    void initiateHole(double x, double y, double radius) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addCircleContour(x, y, radius, true).withAdditionalAttributes(new GameShapeAdditionalAttributesForDrawingEtc(Color.BLACK, Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, GameShapeAdditionalAttributesForDrawingEtc.Specialness.NONE));
        hole = new GamePolyarcgon(builder);
    }

    static GamePolyarcgon makeWindmill(double x, double y, double outerRadius, double holeRadius, double bladeAngle, GameShapeAdditionalAttributesForDrawingEtc attributes) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        for (int i = 0; i < 4; i++) {
//            double cosFirstRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
//            double sinFirstRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
            double cosSecondRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
            double sinSecondRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
            double cosThirdRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);
            double sinThirdRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);

            builder.newContour();
            builder.lineTo(x + outerRadius * cosSecondRay, y + outerRadius * sinSecondRay);
            builder.lineTo(x + outerRadius * cosThirdRay, y + outerRadius * sinThirdRay);
            builder.lineTo(x + holeRadius * cosThirdRay, y + holeRadius * sinThirdRay);
            builder.lineTo(x + holeRadius * cosSecondRay, y + holeRadius * sinSecondRay);
        }
        builder.withAdditionalAttributes(attributes);
        return builder.buildAndReset();
    }

    static GamePolyarcgon makeSealedWindmill(double x, double y, double outerRadius, double hubRadius, double holeRadius, double bladeAngle, GameShapeAdditionalAttributesForDrawingEtc attributes) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        for (int i = 0; i < 4; i++) {
            double cosFirstRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
            double sinFirstRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
            double cosSecondRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
            double sinSecondRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
            double cosThirdRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);
            double sinThirdRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);

            builder.lineTo(x + hubRadius * cosFirstRay, y + hubRadius * sinFirstRay);
            builder.lineTo(x + hubRadius * cosSecondRay, y + hubRadius * sinSecondRay);
            builder.lineTo(x + outerRadius * cosSecondRay, y + outerRadius * sinSecondRay);
            builder.lineTo(x + outerRadius * cosThirdRay, y + outerRadius * sinThirdRay);
        }
        builder.newContour();
        for (int i = 3; i >= 0; i--) {
            double cosFirstRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
            double sinFirstRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 - 1.0 / 8.0) + bladeAngle / 2.0);
            double cosSecondRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
            double sinSecondRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) - bladeAngle / 2.0);
//            double cosThirdRay = Math.cos(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);
//            double sinThirdRay = Math.sin(2.0 * Math.PI * (((double) i) / 4.0 + 1.0 / 8.0) + bladeAngle / 2.0);

            builder.lineTo(x + holeRadius * cosSecondRay, y + holeRadius * sinSecondRay);
            builder.lineTo(x + holeRadius * cosFirstRay, y + holeRadius * sinFirstRay);
        }
        builder.withAdditionalAttributes(attributes);
        return builder.buildAndReset();
    }
    public static GamePolyarcgon makeWallFrame(int width, int height, double borderThickness, double extraWallBeyondBorder, double radius, GameShapeAdditionalAttributesForDrawingEtc attributes) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addRectangleContour(-extraWallBeyondBorder, -extraWallBeyondBorder, width+extraWallBeyondBorder, height+extraWallBeyondBorder, true);
        builder.addRoundedRectangleContour(borderThickness, borderThickness, width-borderThickness, height-borderThickness, radius, false);
        builder.withAdditionalAttributes(attributes);
        return builder.buildAndReset();
    }

    public void update(Context context) {
        super.update(context);
        long now = System.currentTimeMillis();
        switch (gameState) {
            case INTRODUCING_LEVEL:
            case FINISHING_LEVEL:
            case SPECIAL_ANIMATION:
                break;
            case PLAYING_LEVEL:
                //update ripple
                if (isRippleAlive()) {
                    double radius = ripple.affectedArea.getBoundingRadius();
                    if (0.5*radius < Math.sqrt(width * width + height * height)) {
                        ripple.affectedArea = new GamePolyarcgonBuilder().addCircleContour(ripple.affectedArea.getX(), ripple.affectedArea.getY(), radius+RIPPLE_EXPANSION_RATE, true).withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.RIPPLE_MATERIAL).buildAndReset();
                    }

                    rippleAgeInFrames++;
                    double distanceBetweenBallAndRipple = Math.sqrt((ball.x-ripple.affectedArea.getX())*(ball.x-ripple.affectedArea.getX()) + (ball.y-ripple.affectedArea.getY())*(ball.y-ripple.affectedArea.getY()));
                    if (distanceBetweenBallAndRipple < radius && distanceBetweenBallAndRipple < lastFrameDistanceBetweenBallAndRipple + 0.1*RIPPLE_EXPANSION_RATE) {
                        numberOfFramesBallIsStuckForRipple++;
                    }
                    lastFrameDistanceBetweenBallAndRipple = distanceBetweenBallAndRipple;
                    if (numberOfFramesBallIsStuckForRipple > numberOfFramesBallCanBeStuckBeforeStoppingRipple || rippleAgeInFrames > maxFramesPerRipple) {
                        gamePhysicsEngine.forceFields.remove(ripple);
                    }
                }

                for (GameWormhole wormhole : wormholes) {
                    wormhole.update(ball);
                }

                if (gamePhysicsEngine.distanceBetween(ball, hole) < hole.boundingRadius) {
                    ball.x = hole.x;
                    ball.y = hole.y;
                    winLevel(now);
                } else {
                    gamePhysicsEngine.update();
                }
                break;
        }
    }

    protected void winLevel(long now) {
        super.winLevel(now);
        for (GameListener gameListener : gameListeners) {
            gameListener.onLevelComplete(level, strokes); // TODO: instead of on level complete with level/strokes, just directly update the text. Still save though
        }
    }

    public void draw(Canvas canvas) {
        if (canvas != null) {
            long now = System.currentTimeMillis();
            switch (gameState) {
                case INTRODUCING_LEVEL:
                case PLAYING_LEVEL:
                case FINISHING_LEVEL:
                case PREVIEW_LEVEL:
                    canvas.drawColor(Color.rgb(0, 255 * 3 / 4, 0));
                    GameShapeDrawer.draw(canvas, hole, this); //hole on bottom
                    ArrayList<GameShape> shapes = gamePhysicsEngine.getAllShapes();
                    shapes.remove(hole);
                    GameShapeDrawer.drawPotentialShadow(canvas, shapes, this);
                    GameShapeDrawer.draw(canvas, shapes, this);
                    if (isRippleAlive()) {
                        GameShapeDrawer.draw(canvas, ripple.affectedArea, this);
                    }
                    for (GameWormhole wormhole : wormholes) {
                        wormhole.draw(canvas);
                    }
                    GameShapeDrawer.draw(canvas, ball, this); //draw ball again to put it on top
                    break;
                case SPECIAL_ANIMATION:
                    break;
            }
        }
        super.drawStuffOnTop(canvas);
    }

    public void onTouchEvent(MotionEvent event) {
        switch (gameState) {
            case INTRODUCING_LEVEL:
            case FINISHING_LEVEL:
                break;
            case PLAYING_LEVEL:
                if (!isRippleAlive()) {
                    if (ripple == null) {
                        ripple = GameForceField.simplePushAwayForceField(null, GameForceField.PREFERRED_STRENGTH);
                    }
                    ripple.affectedArea = new GamePolyarcgonBuilder().addCircleContour((int) (event.getX() + 0.5f), (int) (event.getY() + 0.5f), 1, true).withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.RIPPLE_MATERIAL).buildAndReset();
                    gamePhysicsEngine.forceFields.add(ripple);
                    strokes++;
                    rippleAgeInFrames = numberOfFramesBallIsStuckForRipple = 0;
                    notifyStrokesAndParChange(strokes, getPar(level));
                }
                break;
        }
    }

    public void addGameListener(GameListener gameListener) {
        gameListeners.add(gameListener);
    }

    public boolean removeGameLevelChangeListener(GameListener gameListener) {
        return gameListeners.remove(gameListener);
    }

    private void notifyStrokesAndParChange(int strokes, int par) {
        for (GameListener gameListener : gameListeners) {
            gameListener.onStrokesAndParChange(strokes, par);
        }
    }
    boolean isRippleAlive() {
        return ripple != null && gamePhysicsEngine.forceFields.contains(ripple);
    }
    public static int getPar(int level) {
        return RippleGolfGameLevel.getGameLevel(level).getPar();
    }
    public static int getPerfectPar(int level) {
        return RippleGolfGameLevel.getGameLevel(level).getPerfectPar();
    }
}

class RippleGolfGameLevel1 implements RippleGolfGameLevel {
    static RippleGolfGameLevel1 staticInstance = new RippleGolfGameLevel1();
    public static RippleGolfGameLevel1 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel1() {}
    public int getPar() {
        return getPerfectPar();
    }
    public int getPerfectPar() {
        return 1;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 10.0;

        double sideMargins = borderThickness + (longerDimension-game.height)/2.0;

        double ballRadius = shorterDimension / 20.0;
        double firstRadius = 5.0*ballRadius;
        double secondRadius = 1.2*ballRadius;

        game.initiateBall(sideMargins + firstRadius, game.height / 2.0, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(game.width - (sideMargins + secondRadius), game.height / 2.0, ballRadius);

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addRectangleContour(-extraWallBeyondBorder, -extraWallBeyondBorder, game.width+extraWallBeyondBorder, game.height+extraWallBeyondBorder, true);
        builder.lineToRoundedTurn(sideMargins + firstRadius, game.height / 2.0, firstRadius, false);
        builder.lineToRoundedTurn(game.width - sideMargins - secondRadius, game.height / 2.0, secondRadius, false);
        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon wall = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(wall);

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = Math.max(game.height/2.0 - firstRadius - 2*textSize, 0.75*textSize);
        game.levelIntroducingTime = 5 * Game.SECOND_MS; //longer for level 1
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Ripple Golf", 5 * Game.SECOND_MS, game.width/2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width/2.0, textY + textSize, game.width, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
        game.setLevelSpecialRules(new Runnable() {
            long lastTimeExplainedSomething = -1;
            boolean explainedGoal = false;
            boolean createdRipple = false;

            @Override
            public void run() {
                if (game.isRippleAlive()) {
                    createdRipple = true;
                }

                //repeatedly explain game if user idles too long
                if (game.levelText.isEmpty()) {
                    if (!createdRipple) {
                        long now = System.currentTimeMillis();
                        if (now > lastTimeExplainedSomething + 15 * Game.SECOND_MS || lastTimeExplainedSomething == -1) {
                            long duration = 6 * Game.SECOND_MS;
                            game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Press anywhere to create a ripple.", duration, game.width / 2.0, textY, game.width - 2 * borderThickness, textSize2, Color.BLACK));
                            game.timeWhenStartedShowingText = now;
                            lastTimeExplainedSomething = now + duration;
                        }
                    } else {
                        if (!game.isRippleAlive()) {
                            long now = System.currentTimeMillis();
                            if (!explainedGoal || now > lastTimeExplainedSomething + 15 * Game.SECOND_MS || lastTimeExplainedSomething == -1) {
                                long duration = 6 * Game.SECOND_MS;
                                game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Push the ball in the hole to win.", duration, game.width / 2.0, textY, game.width - 2*borderThickness, textSize2, Color.BLACK));
                                game.timeWhenStartedShowingText = now;
                                lastTimeExplainedSomething = now + duration;
                                explainedGoal = true;
                            }
                        }
                    }
                }
            }
        });
    }
}
class RippleGolfGameLevel2 implements RippleGolfGameLevelWithSpeedFactor {
    static RippleGolfGameLevel2 staticInstance = new RippleGolfGameLevel2();
    public static RippleGolfGameLevel2 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel2() {}
    public int getPar() {
        return getPerfectPar();
    }
    public int getPerfectPar() {
        return 1;
    }
    double speedFactor = 1;
    public void multiplySpeedFactor(double speedFactor) {
        this.speedFactor *= speedFactor;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 5.0;
        game.gamePhysicsEngine.addWall(game.makeWallFrame(game.width, game.height, borderThickness, extraWallBeyondBorder, borderThickness, GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL));

        double ballRadius = shorterDimension / 40.0;

        double ballX = shorterDimension/2.0;
        double ballY = 2.0 * borderThickness;
        double windmillX = shorterDimension/2.0;
        double windmillY = longerDimension/2.0;

        if (game.width != shorterDimension) {
            double swap;
            swap = ballX;
            ballX = ballY;
            ballY = swap;

            swap = windmillX;
            windmillX = windmillY;
            windmillY = swap;
        }

        double windmillRadius = (Math.min(shorterDimension, 0.75*longerDimension) - borderThickness * 2.0) / 2.0;
        game.initiateBall(ballX, ballY, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(windmillX, windmillY, 3 * ballRadius);

        double bladeAngle = Math.PI / 3.0;
        GamePolyarcgon windmill = RippleGolfGame.makeWindmill(windmillX, windmillY, windmillRadius, ballRadius * 1.1 / (Math.sin((Math.PI / 2.0 - bladeAngle) / 2.0)), bladeAngle, GameShapeAdditionalAttributesForDrawingEtc.SCI_FI_MATERIAL);
        game.gamePhysicsEngine.addWall(windmill);

        game.setLevelSpecialRules(new Runnable() {
            @Override
            public void run() {
                if (game.gameState == RippleGolfGame.GameState.PLAYING_LEVEL) {
                    windmill.rotationRadians -= speedFactor * Math.PI / 300.0;
                }
            }
        });

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = Math.min(windmillY+windmillRadius+0.75*textSize, game.height-3.0*textSize);
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Introducing mini golf windmills", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize + textSize2, game.width-2*borderThickness, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
    }
}
class RippleGolfGameLevel3 implements RippleGolfGameLevelWithSpeedFactor {
    static RippleGolfGameLevel3 staticInstance = new RippleGolfGameLevel3();
    public static RippleGolfGameLevel3 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel3() {}
    public int getPar() {
        return getPerfectPar()+1;
    }
    public int getPerfectPar() {
        return 1;
    }
    double speedFactor = 1;
    public void multiplySpeedFactor(double speedFactor) {
        this.speedFactor *= speedFactor;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        // more than usual otherwise it stops too soon
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (6* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (15* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 5.0;
        game.gamePhysicsEngine.addWall(game.makeWallFrame(game.width, game.height, borderThickness, extraWallBeyondBorder, borderThickness, GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL));

        double ballRadius = shorterDimension / 40.0;

        double ballX = shorterDimension/2.0;
        double ballY = 2.0 * borderThickness;
        double windmillX = shorterDimension/2.0;
        double windmillY = longerDimension/2.0;

        if (game.width != shorterDimension) {
            double swap;
            swap = ballX;
            ballX = ballY;
            ballY = swap;

            swap = windmillX;
            windmillX = windmillY;
            windmillY = swap;
        }

        double windmillRadius = (Math.min(shorterDimension, 0.75*longerDimension) - borderThickness * 2.0) / 2.0;
        game.initiateBall(ballX, ballY, ballRadius, 5, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(windmillX, windmillY, 3 * ballRadius);

        double bladeAngle = Math.PI / 3.0;
        double holeRadius = ballRadius * 1.1 / (Math.sin((Math.PI / 2.0 - bladeAngle) / 2.0));
        GamePolyarcgon windmill = RippleGolfGame.makeSealedWindmill(windmillX, windmillY, windmillRadius, holeRadius * 1.5, holeRadius, bladeAngle, GameShapeAdditionalAttributesForDrawingEtc.SCI_FI_MATERIAL);
        game.gamePhysicsEngine.addMovableShape(windmill);
        game.gamePhysicsEngine.addCollisionRule((shape1, shape2) -> {
            GameShape otherShape;
            if (shape1 == windmill) {
                otherShape = shape2;
            } else if (shape2 == windmill) {
                otherShape = shape1;
            } else {
                return true;
            }
            if (game.ripple != null && otherShape == game.ripple.affectedArea) {
                return false;
            } else {
                return true;
            }
        });

        game.setLevelSpecialRules(new Runnable() {
            @Override
            public void run() {
                if (game.gameState == RippleGolfGame.GameState.PLAYING_LEVEL) {
                    windmill.rotationRadians -= speedFactor * Math.PI / 300.0;
                }
            }
        });

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = Math.min(windmillY+windmillRadius+0.75*textSize, game.height-3.0*textSize);
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Introducing impossible windmills", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize + textSize2, game.width - 2 * borderThickness, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
    }
}
class RippleGolfGameLevel4 implements RippleGolfGameLevel {
    static RippleGolfGameLevel4 staticInstance = new RippleGolfGameLevel4();
    public static RippleGolfGameLevel4 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel4() {}
    public int getPar() {
        return getPerfectPar();
    }
    public int getPerfectPar() {
        return 1;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 10.0;

        double ballRadius = shorterDimension / 30.0;
        double centerThickness = shorterDimension / 5.0;
        double pathThickness = 3 * ballRadius;

        double wormholesDistanceFromCenter = centerThickness / 2.0 + pathThickness/2.0;
        double wormholesDistanceFromEdge = borderThickness + pathThickness/2.0;
        game.initiateBall(game.width / 2.0 + wormholesDistanceFromCenter, game.height / 2.0, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(game.width / 2.0, wormholesDistanceFromEdge, ballRadius);
        game.wormholes.add(new GameWormhole(game.width - wormholesDistanceFromEdge, game.height / 2.0, ballRadius, game.width / 2.0, game.height / 2.0 + wormholesDistanceFromCenter, ballRadius, "A"));
        game.wormholes.add(new GameWormhole(game.width / 2.0, game.height - wormholesDistanceFromEdge, ballRadius, game.width / 2.0 - wormholesDistanceFromCenter, game.height / 2.0, ballRadius, "B"));
        game.wormholes.add(new GameWormhole(wormholesDistanceFromEdge, game.height / 2.0, ballRadius, game.width / 2.0, game.height / 2.0 - wormholesDistanceFromCenter, ballRadius, "C"));

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addRectangleContour(-extraWallBeyondBorder, -extraWallBeyondBorder, game.width+extraWallBeyondBorder, game.height+extraWallBeyondBorder, true);
        builder.lineToRoundedTurn(wormholesDistanceFromEdge, game.height/2.0, pathThickness/2.0, false);
        builder.lineToRoundedTurn(game.width/2.0 - wormholesDistanceFromCenter, game.height/2.0, pathThickness/2.0, false);
        builder.newContour();
        builder.lineToRoundedTurn(game.width - wormholesDistanceFromEdge, game.height/2.0, pathThickness/2.0, false);
        builder.lineToRoundedTurn(game.width/2.0 + wormholesDistanceFromCenter, game.height/2.0, pathThickness/2.0, false);
        builder.newContour();
        builder.lineToRoundedTurn(game.width/2.0, wormholesDistanceFromEdge, pathThickness/2.0, false);
        builder.lineToRoundedTurn(game.width/2.0, game.height/2.0 - wormholesDistanceFromCenter, pathThickness/2.0, false);
        builder.newContour();
        builder.lineToRoundedTurn(game.width/2.0, game.height-wormholesDistanceFromEdge, pathThickness/2.0, false);
        builder.lineToRoundedTurn(game.width/2.0, game.height/2.0 + wormholesDistanceFromCenter, pathThickness/2.0, false);
        builder.newContour();
        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon wall = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(wall);

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = game.height/4.0;
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Introducing portals", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize, game.width - 2 * borderThickness, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
    }
}
class RippleGolfGameLevel5 implements RippleGolfGameLevel {
    static RippleGolfGameLevel5 staticInstance = new RippleGolfGameLevel5();
    public static RippleGolfGameLevel5 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel5() {}
    public int getPar() {
        return getPerfectPar()+1;
    }
    public int getPerfectPar() {
        return 1;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 10.0;
        double centerWallThickness = borderThickness;

        double ballRadius = shorterDimension / 30.0;

        double roundedCornerEtcRadius = 1.5 * ballRadius;

        game.initiateBall((borderThickness + game.width / 2.0 - centerWallThickness / 2.0)/2.0, borderThickness + roundedCornerEtcRadius, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(borderThickness + roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, ballRadius);
        game.wormholes.add(new GameWormhole(game.width - borderThickness - roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, ballRadius, game.width / 2.0 - centerWallThickness / 2.0 - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, ballRadius, "A"));
        game.wormholes.add(new GameWormhole(borderThickness + roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, ballRadius, borderThickness + roundedCornerEtcRadius, game.height / 2.0 - centerWallThickness / 2.0 - roundedCornerEtcRadius, ballRadius, "B"));
//        game.wormholes.add(new GameWormhole(game.width / 2.0 + centerWallThickness / 2.0 + roundedCornerEtcRadius, game.height / 2.0 - centerWallThickness / 2.0 - roundedCornerEtcRadius, ballRadius, game.width - borderThickness - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, ballRadius, "C"));
        game.wormholes.add(new GameWormhole(game.width / 2.0 + centerWallThickness / 2.0 + roundedCornerEtcRadius, game.height / 2.0, ballRadius, game.width - borderThickness - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, ballRadius, "C"));
        game.wormholes.add(new GameWormhole(borderThickness + roundedCornerEtcRadius, (borderThickness + game.height / 2.0 - centerWallThickness / 2.0) / 2.0, ballRadius, game.width - borderThickness - roundedCornerEtcRadius, (game.height / 2.0 + centerWallThickness / 2.0 + game.height - borderThickness) / 2.0, ballRadius, "D"));

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addRectangleContour(-extraWallBeyondBorder, -extraWallBeyondBorder, game.width+extraWallBeyondBorder, game.height+extraWallBeyondBorder, true);
//        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineTo(game.width/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, game.height/2.0 - centerWallThickness/2.0);
//        builder.arcToRoundedTurn(game.width/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, Math.PI, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.arcToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, Math.PI, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.newContour();
//        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
//        builder.lineToRoundedTurn(game.width/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);

        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn((borderThickness + game.width / 2.0 - centerWallThickness / 2.0)/2.0, game.height/2.0, centerWallThickness/2.0, true);
        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(game.width/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(game.width/2.0, game.height / 2.0, centerWallThickness/2.0, true);
        builder.lineToRoundedTurn(game.width/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height/2.0 + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.arcToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, game.height/2.0 - centerWallThickness/2.0 - roundedCornerEtcRadius, Math.PI, roundedCornerEtcRadius, false);

        builder.lineToRoundedTurn(game.width - borderThickness - roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);

        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon wall = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(wall);

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = Math.max((game.height/2.0 - centerWallThickness/2.0 - 4.0*textSize + borderThickness + textSize * 0.75) / 2.0, borderThickness + textSize * 0.75);
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Par 2 for most players,\npar 1 for geniuses.\n\nWarning: VERY tricky\nto get hole in 1", 15 * Game.SECOND_MS, game.width / 2.0, textY + textSize + 3*textSize2, game.width - 2 * borderThickness - 4 * roundedCornerEtcRadius, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
    }
}
class RippleGolfGameLevel6 implements RippleGolfGameLevelWithSpeedFactor {
    static RippleGolfGameLevel6 staticInstance = new RippleGolfGameLevel6();
    public static RippleGolfGameLevel6 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel6() {}
    public int getPar() {
        return getPerfectPar()+1;
    }
    public int getPerfectPar() {
        return 2;
    }
    double speedFactor = 1;
    public void multiplySpeedFactor(double speedFactor) {
        this.speedFactor *= speedFactor;
    }
    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (6* Game.SECOND_MS/60); // this needs to be longer
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        double extraWallBeyondBorder = longerDimension / 5.0;
        double borderThickness = shorterDimension / 6.0;
        double centerWallThickness = borderThickness;

        double ballRadius = shorterDimension / 40.0;

        double roundedCornerEtcRadius = 1.5 * ballRadius;

        double rotatingCRadius = 5 * roundedCornerEtcRadius;
        double rotatingC2Radius = 3 * roundedCornerEtcRadius;
        double platformLongerDimension = longerDimension/2.0 + rotatingCRadius + roundedCornerEtcRadius + longerDimension/20.0;

        double rotatingCShorterDimension = shorterDimension / 2.0;
        double rotatingCLongerDimension = longerDimension / 2.0;

        double ballX = shorterDimension/2.0;
        double ballY = longerDimension/2.0 - rotatingCRadius - roundedCornerEtcRadius - longerDimension/20.0;
        double rotatingC2ShorterDimension = shorterDimension - borderThickness - ballRadius;
        double rotatingC2LongerDimension = longerDimension - borderThickness - ballRadius;
        double holeX = rotatingC2ShorterDimension;
        double holeY = rotatingC2LongerDimension;

        if (game.width != shorterDimension) {
            double swap;
            swap = ballX;
            ballX = ballY;
            ballY = game.height-swap;
            swap = holeX;
            holeX = holeY;
            holeY = game.height-swap;
        }

        game.initiateBall(ballX, ballY, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(holeX, holeY, ballRadius);

        //build main game boundaries
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        builder.addRectangleContour(-extraWallBeyondBorder, -extraWallBeyondBorder, shorterDimension+extraWallBeyondBorder, longerDimension+extraWallBeyondBorder, true);

        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, rotatingCLongerDimension - centerWallThickness/2.0 - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(shorterDimension - borderThickness - roundedCornerEtcRadius, rotatingCLongerDimension - centerWallThickness/2.0 - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(shorterDimension - borderThickness - roundedCornerEtcRadius, borderThickness + roundedCornerEtcRadius, roundedCornerEtcRadius, false);

        builder.newContour();

        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, rotatingCLongerDimension + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(borderThickness + roundedCornerEtcRadius, longerDimension - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(shorterDimension - borderThickness - roundedCornerEtcRadius, longerDimension - borderThickness - roundedCornerEtcRadius, roundedCornerEtcRadius, false);
        builder.lineToRoundedTurn(shorterDimension - borderThickness - roundedCornerEtcRadius, rotatingCLongerDimension + centerWallThickness/2.0 + roundedCornerEtcRadius, roundedCornerEtcRadius, false);

        builder.newContour();

        builder.addCircleContour(rotatingCShorterDimension, rotatingCLongerDimension, rotatingCRadius*0.999, false);

        builder.intersectionLastContour(); // intersection with a negative contour means subtracting

        builder.addCircleContour(rotatingC2ShorterDimension, rotatingC2LongerDimension, rotatingC2Radius+2*ballRadius, false);

        builder.intersectionLastContour();

        GameMaze bentWall = new GameMaze();
        bentWall.addWall(new GameMaze.GameMazeWall(shorterDimension/2.0 - rotatingCRadius, platformLongerDimension, shorterDimension/2.0 + 0.5 * rotatingCRadius, platformLongerDimension, 0));
        bentWall.addWall(new GameMaze.GameMazeWall(shorterDimension/2.0 + 0.5 * rotatingCRadius, platformLongerDimension, shorterDimension/2.0 + rotatingCRadius, platformLongerDimension - 0.5 * rotatingCRadius, 0));
        bentWall.build(builder, 2*roundedCornerEtcRadius);

        if (game.width != shorterDimension) {
            builder.rotate(-Math.PI/2.0, shorterDimension/2.0, shorterDimension/2.0);
        }

        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon wall = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(wall);

        //build rotating C
        RippleGolfGameLevel6.buildCShape(builder, rotatingCShorterDimension, rotatingCLongerDimension, rotatingCRadius, 2.0*roundedCornerEtcRadius, 2.0*ballRadius);

        if (game.width != shorterDimension) {
            builder.rotate(-Math.PI/2.0, shorterDimension /2.0, shorterDimension /2.0);
        }

        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon rotatingC = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(rotatingC);

        //build next rotating C
        RippleGolfGameLevel6.buildCShape(builder, rotatingC2ShorterDimension, rotatingC2LongerDimension, rotatingC2Radius, 2*roundedCornerEtcRadius, 2.0*ballRadius);

        if (game.width != shorterDimension) {
            builder.rotate(-Math.PI/2.0, shorterDimension /2.0, shorterDimension /2.0);
        }

        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        GamePolyarcgon rotatingC2 = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(rotatingC2);

        game.setLevelSpecialRules(new Runnable() {
            @Override
            public void run() {
                if (game.gameState == RippleGolfGame.GameState.PLAYING_LEVEL) {
                    rotatingC.rotationRadians += speedFactor * Math.PI / 70.0;
                    rotatingC2.rotationRadians -= speedFactor * Math.PI / 70.0;
                }
            }
        });

        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = Math.max(game.height / 4.0, borderThickness + textSize * 0.75);
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("The letter C", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize, game.width - 2 * borderThickness - 4 * roundedCornerEtcRadius, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));
    }

    static void buildCShape(GamePolyarcgonBuilder builder, double x, double y, double cShapeRadius, double thickness, double gapWidth) {
        builder.newContour();
        double angleOfRotatingCMouth = 2.0 * Math.asin((thickness/2.0 + gapWidth/2.0) / (cShapeRadius - thickness/2.0));
        double rotatingCMouthXDisplacement = (cShapeRadius - thickness/2.0) * Math.sin(angleOfRotatingCMouth/2.0);
        double rotatingCMouthYDisplacement = (cShapeRadius - thickness/2.0) * Math.cos(angleOfRotatingCMouth/2.0);
        builder.arcToRoundedTurn(x - rotatingCMouthXDisplacement, y - rotatingCMouthYDisplacement, 2*Math.PI - angleOfRotatingCMouth, thickness/2.0, true);
        builder.arcToRoundedTurn(x + rotatingCMouthXDisplacement, y - rotatingCMouthYDisplacement, -(2*Math.PI - angleOfRotatingCMouth), thickness/2.0, true);
        builder.withCenterOfMass(x, y, false);

        builder.newContour();
    }
}
class RippleGolfGameLevel7 extends RippleGolfGameLevel6 {
    static RippleGolfGameLevel7 staticInstance = new RippleGolfGameLevel7();
    public static RippleGolfGameLevel7 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel7() {
        multiplySpeedFactor(2);
    }
    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        super.initiateLevel(context, game, levelNumberToDisplay);
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);
        GameFadeableText text = game.levelText.get(0 * Game.SECOND_MS+1);
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("The letter C++", text.duration, text.x, text.y, text.width, text.textSize, text.textColor));
    }
}
class RippleGolfGameLevel8 implements RippleGolfGameLevel {
    static RippleGolfGameLevel8 staticInstance = new RippleGolfGameLevel8();
    public static RippleGolfGameLevel8 getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel8() {}
    public int getPar() {
        return getPerfectPar()+1;
    }
    public int getPerfectPar() {
        return 11;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (1* Game.SECOND_MS/60); // shorter otherwise big wait
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        int radius = (int) Math.min(shorterDimension, 0.75*longerDimension);

        int rings = 8;

        double mazeWallToPathThicknessRatio = 0.6;
        double mazePathThickness = 0.5 * radius / (rings + (rings-1) * mazeWallToPathThicknessRatio);
        double mazeWallThickness = mazePathThickness * mazeWallToPathThicknessRatio;
        double ballRadius = 0.5 * mazePathThickness;

        double mazeX = shorterDimension/2.0;
        double mazeY = longerDimension/2.0;

        double holeAngle = 1.5*Math.PI;
        double holeDistance = (rings-1) * (mazeWallThickness+mazePathThickness) + 0.5*mazePathThickness;
        double holeX = mazeX+holeDistance*Math.cos(holeAngle);
        double holeY = mazeY+holeDistance*Math.sin(holeAngle);
        double ballX = mazeX;
        double ballY = mazeY;

        if (game.width != shorterDimension) { // we still need rotating otherwise the maze isn't the same
            double swap;
            swap = ballX;
            ballX = ballY;
            ballY = game.height-swap;
            swap = holeX;
            holeX = holeY;
            holeY = game.height-swap;
        }

        game.initiateBall(ballX, ballY, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(holeX, holeY, ballRadius);

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();

        GameMaze maze = GameMaze.loadMaze(context, R.raw.maze_1_perfect_par_11_seed_neg_1791509761, mazeX, mazeY, mazePathThickness);
        maze.build(builder, mazeWallThickness);
        if (game.width != shorterDimension) {
            builder.rotate(-Math.PI/2.0, shorterDimension/2.0, shorterDimension/2.0);
        }
        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        game.gamePhysicsEngine.addWall(builder.buildAndReset());


        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = 0.1*game.height;
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Introducing mazes", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize + textSize2, game.width, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));

    }
}
class RippleGolfGameLevel8Generator implements RippleGolfGameLevel {
    static RippleGolfGameLevel8Generator staticInstance = new RippleGolfGameLevel8Generator();
    public static RippleGolfGameLevel8Generator getInstance() {
        return staticInstance;
    }
    protected RippleGolfGameLevel8Generator() {}
    public int getPar() {
        return getPerfectPar()+2;
    }
    public int getPerfectPar() {
        return 10;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (1* Game.SECOND_MS/60); // shorter otherwise big wait
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        int shorterDimension = Math.min(game.width, game.height);
        int longerDimension = Math.max(game.width, game.height);

        int radius = (int) Math.min(shorterDimension, 0.75*longerDimension);

        int rings = 8;

        double mazeWallToPathThicknessRatio = 0.6;
        double mazePathThickness = 0.5 * radius / (rings + (rings-1) * mazeWallToPathThicknessRatio);
        double mazeWallThickness = mazePathThickness * mazeWallToPathThicknessRatio;
        double ballRadius = 0.5 * mazePathThickness;

        double mazeX = game.width/2.0;
        double mazeY = game.height/2.0;

        double holeAngle = 2*Math.PI*(0.75 + 1.0/64.0);
        double holeDistance = (rings-1) * (mazeWallThickness+mazePathThickness) + 0.5*mazePathThickness;
        double holeX = mazeX+holeDistance*Math.cos(holeAngle);
        double holeY = mazeY+holeDistance*Math.sin(holeAngle);

        game.initiateBall(mazeX, mazeY, ballRadius, 1, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(holeX, holeY, ballRadius);

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();

        GameMaze.GameCircularMaze maze = new GameMaze.GameCircularMaze(mazeX, mazeY, 8, mazePathThickness, mazeWallThickness, false);
        maze.randomize(mazeX, mazeY, holeX, holeY, (int) (Math.random()*Integer.MAX_VALUE)); // don't use 1/Math.random(), that somehow keeps resulting in the same mazes
//        maze.randomize(mazeX, mazeY, holeX, holeY, -1791509761);
        GameMaze.saveMaze(maze.asGameMaze(), mazeX, mazeY, mazePathThickness);
        game.levelText.put(123L, new GameFadeableText(""+maze.randomSeedUsed, Long.MAX_VALUE/10, game.width/2.0, 0.9*game.height, game.width, game.width/20.0, Color.BLACK));

//        GameMaze maze = GameMaze.loadMaze(context, R.raw.maze1, mazeX, mazeY, mazePathThickness);

//        maze.build(builder, mazeWallThickness);
        maze.build(builder);
        builder.withAdditionalAttributes(GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL);
        game.gamePhysicsEngine.addWall(builder.buildAndReset());


        double textSize = shorterDimension / 10.0;
        double textSize2 = shorterDimension / 20.0;
        double textY = 0.1*game.height;
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Level " + levelNumberToDisplay, 5 * Game.SECOND_MS, game.width / 2.0, textY, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Introducing mazes", 5 * Game.SECOND_MS, game.width / 2.0, textY + textSize + textSize2, game.width, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));

    }
}

class RippleGolfGameLevelTest implements RippleGolfGameLevel {
    static RippleGolfGameLevelTest staticInstance = new RippleGolfGameLevelTest();
    public static RippleGolfGameLevelTest getInstance() {
        return staticInstance;
    }
    private RippleGolfGameLevelTest() {}
    public int getPar() {
        return getPerfectPar();
    }
    public int getPerfectPar() {
        return 9;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        game.numberOfFramesBallCanBeStuckBeforeStoppingRipple = (int) (3* Game.SECOND_MS/60);
        game.maxFramesPerRipple = (int) (10* Game.SECOND_MS/60);

        double extraWallBeyondBorder = game.height / 5.0;
        double borderThickness = game.width / 25.0;
        game.gamePhysicsEngine.addWall(game.makeWallFrame(game.width, game.height, borderThickness, extraWallBeyondBorder, game.width/5.0, GameShapeAdditionalAttributesForDrawingEtc.DARK_GREEN_MATERIAL));

        double ballRadius = game.width / 40.0;

        double ballX = game.width/2.0;
        double ballY = 2.0 * borderThickness;
        double windmillX = game.width/2.0;
        double windmillY = game.height/2.0;

        double windmillRadius = (Math.min(0.6*game.width, 0.6*game.height) - borderThickness * 2.0) / 2.0;
        game.initiateBall(ballX, ballY, ballRadius, 5, GameShapeAdditionalAttributesForDrawingEtc.WHITE_BALL_MATERIAL);
        game.initiateHole(windmillX, windmillY, 5 * ballRadius);

        double bladeAngle = Math.PI / 3.0;
        double holeRadius = 0.2*ballRadius * 1.1 / (Math.sin((Math.PI / 2.0 - bladeAngle) / 2.0));
//        GamePolyarcgon windmill = game.makeSealedWindmill(windmillX*1.7, windmillY, windmillRadius, holeRadius * 1.5, holeRadius, bladeAngle, GameShapeAdditionalAttributesForDrawingEtc.SCI_FI_MATERIAL);
//        game.gamePhysicsEngine.addMovableShape(windmill);
//        windmill = game.makeSealedWindmill(windmillX*0.7, windmillY, 0.7*windmillRadius, holeRadius * 1.5, holeRadius, bladeAngle, GameShapeAdditionalAttributesForDrawingEtc.SCI_FI_MATERIAL);
//        game.gamePhysicsEngine.addMovableShape(windmill);

        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        game.gamePhysicsEngine.addMovableShape(builder.addRectangleContour(0.5*game.width, 0.5*game.height, 0.6*game.width, 0.9*game.height, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addRectangleContour(0.5*game.width, 0.5*game.height, 0.55*game.width, 0.6*game.height, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addRectangleContour(0.4*game.width, 0.4*game.height, 0.45*game.width, 0.6*game.height, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addRoundedRectangleContour(0.3*game.width, 0.5*game.height, 0.4*game.width, 0.8*game.height, 0.03*game.width, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addCircleContour(0.3*game.width, 0.5*game.height, 0.1*game.width, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addCircleContour(0.3*game.width, 0.6*game.height, 0.15*game.width, true).buildAndReset());
        game.gamePhysicsEngine.addMovableShape(builder.addCircleContour(0.3*game.width, 0.7*game.height, 0.2*game.width, true).buildAndReset());
        builder.addRectangleContour(0.6*game.width, 0.6*game.height, 0.7*game.width, 0.9*game.height, true);
        builder.addCircleContour(0.65*game.width, 0.9*game.height, 0.2*game.width, true);
        builder.unionLastContour();
        game.gamePhysicsEngine.addMovableShape(builder.buildAndReset());

//        GameMaze maze = new GameMaze();
//        maze.addWall(new GameMaze.GameMazeWall(0.5*game.width, 0.5*game.width, 0.7*game.width, 0.5*game.width, 0));
//        maze.addWall(new GameMaze.GameMazeWall(0.5*game.width, 0.6*game.width, 0.7*game.width, 0.6*game.width, 0));
//        maze.addWall(new GameMaze.GameMazeWall(0.5*game.width, 0.7*game.width, 0.7*game.width, 0.7*game.width, 0));
//        maze.addWall(new GameMaze.GameMazeWall(0.5*game.width, 0.5*game.width, 0.5*game.width, 0.7*game.width, 0));
//        maze.addWall(new GameMaze.GameMazeWall(0.6*game.width, 0.5*game.width, 0.6*game.width, 0.7*game.width, 0));
//        maze.build(builder, 0.04*game.width);
//        game.gamePhysicsEngine.addMovableShape(builder.buildAndReset());

        GameMaze.GameCircularMaze maze2 = new GameMaze.GameCircularMaze(0.4*game.width, 0.4*game.width, 4, 0.04*game.width, 0.03*game.width, false);
        maze2.randomize(0.4*game.width, 0.4*game.width, 0.4*game.width, 0, 0);
        maze2.build(builder);
        game.gamePhysicsEngine.addMovableShape(builder.buildAndReset());
//        builder.arcToVirtualRoundedTurn(400, 200, -Math.PI, 40, true, 0, true);
//        builder.lineToVirtualRoundedTurn(500, 200, 40, true, 0, true);
//        builder.lineToVirtualRoundedTurn(500, 400, 40, true, 0, true);
//        builder.lineToVirtualRoundedTurn(100, 400, 40, true, 0, true);
//        builder.lineToVirtualRoundedTurn(100, 200, 40, true, 0, true);
//        builder.lineToVirtualRoundedTurn(200, 200, 40, true, 0, true);
//        game.gamePhysicsEngine.addMovableShape(builder.buildAndReset());

        builder.lineToRoundedTurn(windmillX, 0.2*game.height, 0.05*game.width, true);
        builder.lineToRoundedTurn(windmillX, 0.8*game.height, 0.05*game.width, true);
        GamePolyarcgon spinWall = builder.buildAndReset();
        game.gamePhysicsEngine.addWall(spinWall);


        int textSize = game.width / 10;
        int textSize2 = game.width / 20;
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("Testing", 5 * Game.SECOND_MS, game.width / 2.0, game.height / 4.0, game.width - 2 * borderThickness, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Testing", 5 * Game.SECOND_MS, game.width / 2.0, game.height / 4.0 + textSize + textSize2, game.width - 2 * borderThickness, textSize2, Color.BLACK));
        game.levelTextAtEnd.put(0 * Game.SECOND_MS, new GameFadeableText("Well done!", 2 * Game.SECOND_MS, game.width / 2.0, game.height / 2.0, game.width, textSize, Color.BLACK));

        game.setLevelSpecialRules(() -> {
            if (game.gameState == RippleGolfGame.GameState.PLAYING_LEVEL) {
                spinWall.rotationRadians -= Math.PI / 300;
            }
        });
    }
}

/**
 * The final level of the game with fireworks
 */
class GameLevelWon implements RippleGolfGameLevel {
    static GameLevelWon staticInstance = new GameLevelWon();
    public static GameLevelWon getInstance() {
        return staticInstance;
    }
    private GameLevelWon() {}
    public int getPar() {
        return getPerfectPar();
    }
    public int getPerfectPar() {
        return 0;
    }

    public void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay) {
        int textSize = game.width / 10;
        int textSize2 = game.width / 20;
        game.levelText.put(0 * Game.SECOND_MS, new GameFadeableText("You Win!", 5 * Game.SECOND_MS, game.width / 2.0, game.height / 4.0, game.width, textSize, Color.BLACK));
        game.levelText.put(0 * Game.SECOND_MS + 1, new GameFadeableText("Congratulations", 5 * Game.SECOND_MS, game.width / 2.0, game.height / 4.0 + textSize + textSize2, game.width, textSize2, Color.BLACK));
    }

    public GameWonSpecialAnimation getAnimation(int width, int height) {
        return new GameWonSpecialAnimation(width, height, 5 * Game.SECOND_MS);
    }
}
class GameWonSpecialAnimation {
    static final int AVERAGE_DURATION_BETWEEN_FIREWORKS_FRAMES = 20;
    int projectionDistance;
    int width;
    int height;
    long fadeDuration;
    long fadeStart;
    ArrayList<Firework> fireworks = new ArrayList<>();
    public GameWonSpecialAnimation(int width, int height, long fadeDuration) {
        this.width = width;
        this.height = height;
        projectionDistance = 5*Math.max(width, height);
        this.fadeDuration = fadeDuration;
        fadeStart = Long.MIN_VALUE;
    }
    public void updateAnimation() {
        if (Math.random() < 1.0/AVERAGE_DURATION_BETWEEN_FIREWORKS_FRAMES) {
            Firework newFirework;
            do {
                newFirework = new Firework(width, height);
            } while (!newFirework.willExplodeInScreen());
            fireworks.add(newFirework);
        }
        ListIterator<Firework> fireworkListIterator = fireworks.listIterator();
        while (fireworkListIterator.hasNext()) {
            fireworkListIterator.next().update(fireworkListIterator);
        }
    }
    Comparator<Firework> comparatorToSortFarthestFirst = new Comparator<Firework>() {
        @Override
        public int compare(Firework firework1, Firework firework2) {
            return -Double.compare(firework1.y, firework2.y);
            //because the words are flat in the x and z dimensions
            //only the y dimension determines whether they are in fron
//            return -Double.compare(firework1.x*firework1.x + (firework1.y+projectionDistance)*(firework1.y+projectionDistance) + firework1.z*firework1.z, firework2.x*firework2.x + (firework2.y+projectionDistance)*(firework2.y+projectionDistance) + firework2.z*firework2.z);
        }
    };
    public void drawAnimation(Canvas canvas) {
        int[] colorsGradient;

        long now = System.currentTimeMillis();
        if (fadeStart == Long.MIN_VALUE) {
            fadeStart = now;
        }
        if (now >= fadeStart+fadeDuration) {
            colorsGradient = new int[]{
                    Color.rgb(0, 0, 0),
                    Color.rgb(0, 0, 0x40)};
        } else {
            double fadeProgress = (now-fadeStart)/(double) fadeDuration;
            int fadeToZeroValues = GameShapeDrawer.weightedAverage(0xFF, 0, fadeProgress);
            int fadeToDarkValues = GameShapeDrawer.weightedAverage(0xFF, 0x40, fadeProgress);
            colorsGradient = new int[]{
                    Color.rgb(fadeToZeroValues, fadeToZeroValues, fadeToZeroValues),
                    Color.rgb(fadeToZeroValues, fadeToZeroValues, fadeToDarkValues)};
        }
        float[] stopsGradient = new float[] {0, 1};
        LinearGradient linearGradient = new LinearGradient(0, 0, 0, height, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

        //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
        Paint paint = new Paint();
        paint.setDither(true);
        paint.setAntiAlias(true);
        paint.setShader(linearGradient);
        canvas.drawRect(0, 0, width, height, paint);

        fireworks.sort(comparatorToSortFarthestFirst);
        for (Firework firework : fireworks) {
            firework.draw(canvas, projectionDistance);
        }
    }

    void processPause(long pauseTime) {
        fadeStart += pauseTime;
    }

    class Firework {
        static final double INITIAL_VELOCITY_X = 0.02;
        static final double INITIAL_VELOCITY_Y = 0.02;
        static final double INITIAL_VELOCITY_Z = 0.02;
        static final double INITIAL_POSITION_X = 2.0;
        static final double INITIAL_POSITION_Y = 5.0;
        static final double EXPLOSION_VELOCITY = 0.02;
        static final double TEXT_SIZE = 0.05;
        static final int MAX_FRAMES_BEFORE_EXPLODING = 150;
        static final int MIN_FRAMES_BEFORE_EXPLODING = 50;
        static final int FRAMES_BEFORE_FADING = 20;
        static final int FRAMES_DIMMING_BEFORE_FADING = 10;
        static final int NUMBER_OF_FIREWORK_FRAGMENTS = 20;
        static final double GRAVITY = 0.0002;
        static final int FIREWORK_FRAGMENT_COLOUR_VARIATION = 0x30;
        static final int FIREWORK_COLOUR_VARIATION = 0xFF;
        double x;
        double y;
        double z;
        double explosionVelocity;
        double gravity;
        double velocityX;
        double velocityY;
        double velocityZ;
        int color;
        int framesBeforeExploding;
        int framesBeforeFading;
        public Firework(int width, int height) {
            int shorterDimension = Math.min(width, height);

            x = INITIAL_POSITION_X*width*(2*Math.random()-1);
            y = INITIAL_POSITION_Y*width*(2*Math.random());
            z = 0;
            this.color = Color.rgb(randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION), randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION), randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION));
            velocityX = width*INITIAL_VELOCITY_X*(2*Math.random()-1);
            velocityY = width*INITIAL_VELOCITY_Y*(2*Math.random()-1);
            velocityZ = height*INITIAL_VELOCITY_Z;
            framesBeforeExploding = GameShapeDrawer.weightedAverage(MIN_FRAMES_BEFORE_EXPLODING, MAX_FRAMES_BEFORE_EXPLODING, Math.random());
            framesBeforeFading = Integer.MAX_VALUE;

            explosionVelocity = shorterDimension*EXPLOSION_VELOCITY;
            gravity = height*GRAVITY;
        }
        public Firework(double x, double y, double z, int color, double gravity, double explosionVelocity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = Color.rgb(randomizeByte(Color.red(color), FIREWORK_FRAGMENT_COLOUR_VARIATION), randomizeByte(Color.green(color), FIREWORK_FRAGMENT_COLOUR_VARIATION), randomizeByte(Color.blue(color), FIREWORK_FRAGMENT_COLOUR_VARIATION));
            this.gravity = gravity;
            this.explosionVelocity = explosionVelocity;
            //yeah I know the space of velocities is a square instead of a circle, who cares
            velocityX = explosionVelocity*(2*Math.random()-1);
            velocityY = explosionVelocity*(2*Math.random()-1);
            velocityZ = explosionVelocity*(2*Math.random()-1);
            framesBeforeExploding = Integer.MAX_VALUE;
            framesBeforeFading = FRAMES_BEFORE_FADING;
        }
        public int randomizeByte(int byteToRandomize, int randomness) {
            byteToRandomize = (int) (byteToRandomize + randomness*(2*Math.random()-1));
            if (byteToRandomize < 0) {
                return 0;
            } else if (byteToRandomize >= 0x100) {
                return 0xFF;
            } else {
                return byteToRandomize;
            }
        }
        public void update(ListIterator<Firework> fireworkListIterator) {
            velocityZ -= gravity;

            x += velocityX;
            y += velocityY;
            z += velocityZ;

            framesBeforeFading--;
            framesBeforeExploding--;
            if (framesBeforeFading <= 0) {
                fireworkListIterator.remove();
            } else if (framesBeforeExploding <= 0) {
                fireworkListIterator.remove();
                for (int i=0; i<NUMBER_OF_FIREWORK_FRAGMENTS; i++) {
                    fireworkListIterator.add(new Firework(x, y, z, color, gravity, explosionVelocity));
                }
            }
        }

        public void draw(Canvas canvas, int projectionDistance) {
            double distanceScaling = ((double) projectionDistance)/(y+projectionDistance);
            double xProjected = canvas.getWidth()/2 + x*distanceScaling;
            double yProjected = canvas.getHeight() - z*distanceScaling;
            int shorterDimension = Math.min(width, height);
            if (framesBeforeFading < FRAMES_DIMMING_BEFORE_FADING) {
                int transparentColor = Color.argb(0xFF*framesBeforeFading/FRAMES_DIMMING_BEFORE_FADING, Color.red(color), Color.green(color), Color.blue(color));
                GameFadeableText.drawText("Victory", xProjected, yProjected, width, shorterDimension*distanceScaling*TEXT_SIZE, transparentColor, canvas);
            } else {
                GameFadeableText.drawText("Victory", xProjected, yProjected, width, shorterDimension*distanceScaling*TEXT_SIZE, color, canvas);
            }
        }

        public boolean willExplodeInScreen() {
            double xFinal = x + velocityX*framesBeforeExploding;
            double yFinal = y + velocityY*framesBeforeExploding;
            double zFinal = z + velocityZ*framesBeforeExploding - gravity*framesBeforeExploding*(1.0+framesBeforeExploding)/2.0;

            double distanceScaling = ((double) projectionDistance)/(yFinal+projectionDistance);
            double xProjected = width/2.0 + xFinal*distanceScaling;
            double yProjected = height - zFinal*distanceScaling;
            return xProjected >= 0 && xProjected < width && yProjected >= 0 && yProjected <= height;
        }
    }
}

/**
 * Teleports the ball between two points
 */
class GameWormhole {
    static final double SIZE_FACTOR = 1.5;
    GamePolyarcgon circle1;
    GamePolyarcgon circle2;
    String text;
    private boolean ballJustTeleported = false;
    public GameWormhole(double x1, double y1, double radius1, double x2, double y2, double radius2, String text) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        circle1 = builder.addCircleContour(x1, y1, radius1, true).buildAndReset();
        circle2 = builder.addCircleContour(x2, y2, radius2, true).buildAndReset();
        this.text = text;
    }
    public void draw(Canvas canvas) {
        for (int i=0; i<2; i++) {
            GamePolyarcgon circle = (i==0) ? circle1 : circle2;
            int[] colorsGradient = new int[]{
                    Color.argb(0, 0, 0, 0),
                    Color.argb(255, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)};
            float[] stopsGradient = new float[] {0.8f, 0.9f, 1};
            RadialGradient radialGradient = new RadialGradient((float) circle.x, (float) circle.y, (float) circle.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

            //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
            Paint paint = new Paint();
            paint.setDither(true);
            paint.setAntiAlias(true);
            paint.setShader(radialGradient);

            canvas.drawCircle((float) circle.x, (float) circle.y, (float) circle.boundingRadius, paint);
            GameFadeableText.drawText(text, circle.x, circle.y, (int) (1.6*circle.boundingRadius), circle.boundingRadius, Color.rgb(0, 0, 0), canvas);
        }
    }
    public void update(GamePolyarcgon ball) {
        double distance1 = Math.sqrt((ball.x-circle1.x)*(ball.x-circle1.x) + (ball.y-circle1.y)*(ball.y-circle1.y));
        double distance2 = Math.sqrt((ball.x-circle2.x)*(ball.x-circle2.x) + (ball.y-circle2.y)*(ball.y-circle2.y));
        if (ballJustTeleported) {
            if (distance1 > ball.boundingRadius + circle1.boundingRadius*SIZE_FACTOR && distance2 > ball.boundingRadius + circle2.boundingRadius*SIZE_FACTOR) {
                ballJustTeleported = false;
            }
        } else {
            if (distance1 - circle1.boundingRadius*SIZE_FACTOR < distance2 - circle2.boundingRadius*SIZE_FACTOR && distance1 < circle1.boundingRadius*SIZE_FACTOR) {
                ball.x = circle2.x;
                ball.y = circle2.y;
                ballJustTeleported = true;
            } else if (distance2 < circle2.boundingRadius*SIZE_FACTOR) {
                ball.x = circle1.x;
                ball.y = circle1.y;
                ballJustTeleported = true;
            }
        }
    }
}

/**
 * Text in the game that can fade in and out
 */
class GameFadeableText {
    final String text;
    final long duration;
    final double x;
    final double y;
    final int width;
    final double textSize;
    final int textColor;
    final long DEFAULT_TEXT_FADE_IN_DURATION = 1* Game.SECOND_MS;
    final long DEFAULT_TEXT_FADE_OUT_DURATION = 1* Game.SECOND_MS;

    public GameFadeableText(String text, long duration, double x, double y, double width, double textSize, int textColor) {
        this.text = text;
        this.duration = duration;
        this.x = x;
        this.y = y;
        this.width = (int) (width+0.5);
        this.textSize = textSize;
        this.textColor = textColor;
    }

    public void draw(long timeSinceTextAppearedMs, Canvas canvas) {
        double visibility = Math.min(timeSinceTextAppearedMs / ((double) DEFAULT_TEXT_FADE_IN_DURATION), (duration-timeSinceTextAppearedMs) / ((double) DEFAULT_TEXT_FADE_OUT_DURATION));
        if (duration < DEFAULT_TEXT_FADE_IN_DURATION + DEFAULT_TEXT_FADE_OUT_DURATION) {
            visibility = visibility / ((double) duration / (DEFAULT_TEXT_FADE_IN_DURATION + DEFAULT_TEXT_FADE_OUT_DURATION));
        }
        int color;
        if (visibility < 1) {
            if (visibility <= 0) {
                return; //otherwise glitches for short times
            }
            color = Color.argb((int) (Color.alpha(textColor)*visibility), Color.red(textColor), Color.green(textColor), Color.blue(textColor));
        } else {
            color = textColor;
        }

        drawText(text, x, y, width, textSize, color, canvas);
    }
    //    public void draw(Canvas canvas) {
//        drawText(text, x, y, width, textSize, textColor, canvas);
//    }
    static void drawText(String text, double x, double y, int width, double textSize, int color, Canvas canvas) {
        //thanks to https://medium.com/over-engineering/drawing-multiline-text-to-canvas-on-android-9b98f0bfa16a

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize((float) textSize);
        textPaint.setColor(color);
        StaticLayout staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        Rect bounds = null;
        for (int i=0; i<staticLayout.getLineCount(); i++) {
            Rect rect = new Rect();
            staticLayout.getLineBounds(i, rect);
            if (bounds == null) {
                bounds = rect;
            } else {
                bounds.union(rect);
            }
        }
        double translateX = x-(bounds.left+bounds.right)/2.0;
        double translateY = y-(bounds.top+bounds.bottom)/2.0;

        canvas.translate((float) translateX, (float) translateY);
        staticLayout.draw(canvas);
        canvas.translate(-(float) translateX, -(float) translateY);


        //this version doesn't support multi line text etc.
//        Paint paint = new Paint();
//        paint.setColor(color);
//        paint.setTextSize(textSize);
//        Rect bounds = new Rect();
//        paint.getTextBounds(text, 0, text.length(), bounds);
//        canvas.drawText(text, x-(bounds.left+bounds.right)/2, y-(bounds.top+bounds.bottom)/2, paint);


    }
}

class GamePhysicsEngine {
    // 1 does not push things out of walls (thanks to more shape collisions than ripple collision simulations
    static final double RIPPLE_COLLISION_FACTOR = 1;
//    static final double RIPPLE_COLLISION_FACTOR = 0.000001;

    static final double PREFERRED_MAX_ANGLE_BETWEEN_NET_FORCE_AND_FORCE_FIELDS_FORCE = Math.PI * 3 / 4;
    // try 0.5 since not that much risk pushing into walls?
    // no 0.5 too much because long rectangle can stab into walls being pushed by ripple alone
    static final double RIPPLE_WEAKENING_FACTOR_WHEN_MOVING = 0.2;

    // want 30 because otherwise movement resolution too low things don't move smoothly
    static final int SHAPE_COLLISION_SIMULATIONS_PER_FRAME = 30;

    // 0.499 sometimes pushes things sorta deep into walls though not through
//    static final int SHAPE_RIPPLE_COLLISION_SIMULATIONS_PER_FRAME = (int) (0.499 * SHAPE_COLLISION_SIMULATIONS_PER_FRAME);
    // 0.3 I feel is the best because avoid ever pushing things too deep into walls
    // now 0.8 because reduced force
    static final int SHAPE_COLLISION_SIMULATIONS_WITH_FORCE_FIELDS_PER_FRAME = (int) (0.8 * SHAPE_COLLISION_SIMULATIONS_PER_FRAME);

    // want 100 otherwise things don't move far enough stop before hitting corner
    static final double SHAPE_COLLISION_SIMULATION_MOVEMENT_SPEED = ((double) 100)/SHAPE_COLLISION_SIMULATIONS_PER_FRAME;
    final ArrayList<GameShape> unmovableShapes;
    final ArrayList<GameShape> movableShapes;
    final ArrayList<GameForceField> forceFields;
    final ArrayList<BiPredicate<GameShape, GameShape>> collisionRules;

    public GamePhysicsEngine() {
        unmovableShapes = new ArrayList<>();
        movableShapes = new ArrayList<>();
        forceFields = new ArrayList<>();
        collisionRules = new ArrayList<>();
    }

    public void addWall(GameShape shape) {
        unmovableShapes.add(shape);
    }
    public void addMovableShape(GameShape shape) {
        movableShapes.add(shape);
    }

    public void update() {

        //collision
        for (int collisionSimulations=0; collisionSimulations<SHAPE_COLLISION_SIMULATIONS_PER_FRAME; collisionSimulations++) {
            boolean includeForceFieldsInSimulation = (collisionSimulations < SHAPE_COLLISION_SIMULATIONS_WITH_FORCE_FIELDS_PER_FRAME);
            boolean collided = updateCollisionSimulationAndReturnWhetherCollided(includeForceFieldsInSimulation);
            if (!collided) {
                break;
            }
        }
    }
    private boolean updateCollisionSimulationAndReturnWhetherCollided(boolean applyForceFields) {
        /*
        TODO: replace collision algorithm with one that enforces objects pushing away never move towards each other
         */
        HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData = new HashMap<>();
        for (GameShape shape : movableShapes) {
            otherShapesLoop: for (GameShape otherShape : unmovableShapes) {
                for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                    if (!collisionRule.test(shape, otherShape)) {
                        continue otherShapesLoop;
                    }
                }
                OverlapGradientForceCalculator collision = new OverlapGradientForceCalculator(shape, otherShape);
                shape.collision(otherShape, true, false, true, collision);
                if (!collision.isAlmostZero()) {
//                    Log.i("me", String.format("%.2f, %.2f", collision.repulsionForceAwayFromShape1x, collision.repulsionForceAwayFromShape1y));
                    addCollisionToTemporaryCollisionData(collision, shape, temporaryCollisionData);
                }
            }
            otherShapesLoop: for (GameShape otherShape : movableShapes) {
                if (otherShape == shape) {
                    continue;
                } else {
                    for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                        if (!collisionRule.test(shape, otherShape)) {
                            continue otherShapesLoop;
                        }
                    }
                    OverlapGradientForceCalculator collision = new OverlapGradientForceCalculator(shape, otherShape);
                    shape.collision(otherShape, true, true, true, collision);
                    if (!collision.isAlmostZero()) {
                        addCollisionToTemporaryCollisionData(collision, shape, temporaryCollisionData);
                        addCollisionToTemporaryCollisionData(collision, otherShape, temporaryCollisionData);
                    }
                }
            }
            if (applyForceFields) {
                collisionsOnShapeFromForceFields(shape, temporaryCollisionData);
            }
        }
        boolean collided = !temporaryCollisionData.isEmpty();
        while (!temporaryCollisionData.isEmpty()) {
            Iterator<Map.Entry<GameShape, ArrayList<OverlapGradientForceCalculator>>> iterator = temporaryCollisionData.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<GameShape, ArrayList<OverlapGradientForceCalculator>> e = iterator.next();
                HashMap<GameShape, ForceAndTorque> relatedCollisions = new HashMap<>();
                double maxMovement = recursivelyCalculateRelatedCollisionsAndFindMaxMovement(relatedCollisions, e.getKey(), e.getValue(), 0.001, temporaryCollisionData);
//                Log.i("me", String.format("max movement: %.2f", maxMovement));
                for (Map.Entry<GameShape, ForceAndTorque> push : relatedCollisions.entrySet()) {
                    push.getValue().multiplyIntensity(SHAPE_COLLISION_SIMULATION_MOVEMENT_SPEED/maxMovement);
                    push.getKey().receiveForce(push.getValue());
                }
                if (relatedCollisions.size() == 1) {
                    iterator.remove();
                } else {
                    for (GameShape shape : relatedCollisions.keySet()) {
                        temporaryCollisionData.remove(shape);
                    }
                    break;
                }
            }
        }
        return collided;
    }
    private double recursivelyCalculateRelatedCollisionsAndFindMaxMovement(HashMap<GameShape, ForceAndTorque> relatedCollisions, GameShape shape, ArrayList<OverlapGradientForceCalculator> collisions, double maxMovement, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ForceAndTorque totalCollision = new ForceAndTorque(0, 0, 0, 0, shape);
        relatedCollisions.put(shape, totalCollision);
        for (OverlapGradientForceCalculator collision : collisions) {
            totalCollision.addForceAndTorque(collision);
            GameShape other;
            if (shape == collision.firstShape) {
                other = collision.otherShape;
            } else {
                other = collision.firstShape;
            }
            if (other != null && movableShapes.contains(other) && !relatedCollisions.containsKey(other)) {
                maxMovement = recursivelyCalculateRelatedCollisionsAndFindMaxMovement(relatedCollisions, other, temporaryCollisionData.get(other), maxMovement, temporaryCollisionData);
            }
        }
        double translationMovement = Math.sqrt(totalCollision.forceActingOnShapeX *totalCollision.forceActingOnShapeX + totalCollision.forceActingOnShapeY *totalCollision.forceActingOnShapeY) / shape.getMass();
        double rotationMovement = Math.abs(totalCollision.torqueActingOnShape /shape.getMomentOfInertia()*shape.getBoundingRadius());
        double movement = translationMovement+rotationMovement;
        if (movement > maxMovement) {
            return movement;
        } else {
            return maxMovement;
        }
    }
    public void draw(Canvas canvas) {
        if (canvas != null) {
            for (GameShape s : unmovableShapes) {
                s.draw(canvas);
            }
            for (GameShape s : movableShapes) {
                s.draw(canvas);
            }
            for (GameForceField f: forceFields) {
                f.affectedArea.draw(canvas);
            }
        }
    }

    //TODO: make force strong enough the object doesn't move towards ripple at too direct an angle
    //this ensures either there is always something moving rather than just back and forth, or
    //the ripple continuously applies force without doing anything
    //avoids cases where the ball is against the wall at too direct an angle and refuses to move,
    //or the ball is against a heavy shape that's able to move but refuses to push it
    //maybe angle can be 180 degrees?

    //also how much it pushes an object should depend on whether other things
    //are pushing that object in the same direction. This prevents the ripple
    //from piling a bunch of objects and exerting a small force on each one of them
    //adding up to a huge force that shoves the last object through the wall
    private void collisionsOnShapeFromForceFields(GameShape shape, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ForceAndTorque forceFromFields = null;
        forceFieldsLoop : for (GameForceField forceField : forceFields) {
            for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                if (!collisionRule.test(shape, forceField.affectedArea)) {
                    continue forceFieldsLoop;
                }
            }
            if (forceFromFields == null) {
                forceFromFields = forceField.apply(shape);
            } else {
                forceFromFields.addForceAndTorque(forceField.apply(shape));
            }
        }

        if (forceFromFields != null && !forceFromFields.isAlmostZero()) {
            ArrayList<OverlapGradientForceCalculator> otherCollisions = temporaryCollisionData.get(shape);
            if (otherCollisions == null) {
                addCollisionToTemporaryCollisionData(forceFromFields.asOverlapGradientForceCalculator(), shape, temporaryCollisionData);
            } else {
                double repulsionCollisionMagnitude = Math.sqrt(forceFromFields.forceActingOnShapeX * forceFromFields.forceActingOnShapeX + forceFromFields.forceActingOnShapeY * forceFromFields.forceActingOnShapeY);
                double netForceX = 0;
                double netForceY = 0;
                double grossForceParallelToRepulsionCollision = 0;
                for (OverlapGradientForceCalculator otherCollision : otherCollisions) {
                    double forceX;
                    double forceY;
                    if (otherCollision.firstShape == shape) {
                        forceX = otherCollision.overlapGradientForceX;
                        forceY = otherCollision.overlapGradientForceY;
                    } else {
                        forceX = -otherCollision.overlapGradientForceX;
                        forceY = -otherCollision.overlapGradientForceY;
                    }
                    netForceX += forceX;
                    netForceY += forceY;
                    double dotProduct = forceX * forceFromFields.forceActingOnShapeX + forceY * forceFromFields.forceActingOnShapeY;
                    if (dotProduct >= 0) {
                        grossForceParallelToRepulsionCollision += dotProduct / repulsionCollisionMagnitude;
                    }
                }
                double netForceCrossProduct = Math.abs(netForceX * forceFromFields.forceActingOnShapeY + netForceY * forceFromFields.forceActingOnShapeX);
                double netForceDotProduct = netForceX * forceFromFields.forceActingOnShapeX + netForceY * forceFromFields.forceActingOnShapeY;
                double preferredRepulsionForce = (netForceCrossProduct / Math.tan(PREFERRED_MAX_ANGLE_BETWEEN_NET_FORCE_AND_FORCE_FIELDS_FORCE) + netForceDotProduct) / repulsionCollisionMagnitude;
                if (preferredRepulsionForce < repulsionCollisionMagnitude) {
                    if (preferredRepulsionForce > repulsionCollisionMagnitude * RIPPLE_WEAKENING_FACTOR_WHEN_MOVING) {
                        forceFromFields.multiplyIntensity(preferredRepulsionForce / repulsionCollisionMagnitude);
                        repulsionCollisionMagnitude *= preferredRepulsionForce / repulsionCollisionMagnitude;
                    } else {
                        forceFromFields.multiplyIntensity(RIPPLE_WEAKENING_FACTOR_WHEN_MOVING);
                        repulsionCollisionMagnitude *= RIPPLE_WEAKENING_FACTOR_WHEN_MOVING;
                    }
                }
                if (grossForceParallelToRepulsionCollision < repulsionCollisionMagnitude) {
                    if (grossForceParallelToRepulsionCollision > 0) {
                        forceFromFields.multiplyIntensity(1 - grossForceParallelToRepulsionCollision / repulsionCollisionMagnitude);
                    }
                    addCollisionToTemporaryCollisionData(forceFromFields.asOverlapGradientForceCalculator(), shape, temporaryCollisionData);
                }
            }
        }
    }
    private static void addCollisionToTemporaryCollisionData(OverlapGradientForceCalculator collision, GameShape shape, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ArrayList<OverlapGradientForceCalculator> list = temporaryCollisionData.get(shape);
        if (list == null) {
            list = new ArrayList<>();
            temporaryCollisionData.put(shape, list);
        }
        list.add(collision);
    }

    public double distanceBetween(GameShape shape1, GameShape shape2) {
        double xDifference = shape2.getX() - shape1.getX();
        double yDifference = shape2.getY() - shape1.getY();
        return Math.sqrt(xDifference*xDifference + yDifference*yDifference);
    }

    public void removeAllShapes() {
        unmovableShapes.clear();
        movableShapes.clear();
        forceFields.clear();
        collisionRules.clear();
    }

    public void addCollisionRule(BiPredicate<GameShape, GameShape> rule) {
        collisionRules.add(rule);
    }

    public ArrayList<GameShape> getAllShapes() {
        ArrayList<GameShape> list = new ArrayList<>();
        list.addAll(unmovableShapes);
        list.addAll(movableShapes);
        return list;
    }

}
interface GameShape {
    void collision(GameShape shape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapHandler overlapHandler);

    void draw(Canvas canvas);

    void receiveForce(ForceAndTorque collision);

//    void receiveForce(double forceX, double forceY, double torque);

    double getMass();
    double getArea();
    double getBoundingRadius();

    double getX();
    double getY();

    double getMomentOfInertia();

    void setPos(double x, double y);

    void setRotation(double rotationRadians);
}

/**
 * A point representing a corner or inflection point in a "polyarcgon".
 * x and y represents where the point is.
 * arcAngleChange represents how curved the line segment or arc before the point is.
 */
class PolyarcgonPoint {
    public double x;
    public double y;
    public double arcAngleChange; // positive if shape curves outwards negative if shape curves inwards
    boolean isMoveToWithoutLineEtc; //no line or arc just move to this point

    public PolyarcgonPoint(double x, double y, double arcAngleChange, boolean isMoveToWithoutLineEtc) {
        this.x = x;
        this.y = y;
        this.arcAngleChange = arcAngleChange;
        this.isMoveToWithoutLineEtc = isMoveToWithoutLineEtc;
    }

    public boolean isAlmostStraight() {
        return isAlmostStraight(arcAngleChange);
    }
    public static boolean isAlmostStraight(double arcAngleChange) {
        return Math.abs(arcAngleChange) <= Math.PI / 1000000.0;
    }
    // note we use "arcAngleChange" rather than curvature
    // because there are 4 possible arcs between every two points
    // with the same curvature, so even a signed curvature still needs
    // complicated conditions
}

/**
 * A class for static methods for drawing shapes
 */
class GameShapeDrawer {
    public static int weightedAverage(int int1, int int2, double weightTowardsInt2) {
        return int1 + (int) ((int2-int1)*weightTowardsInt2 + 0.499);
    }

    public static void draw(Canvas canvas, GameShape shape, RippleGolfGame game) {
        if (shape instanceof GamePolyarcgon) {
            GamePolyarcgon shape1 = (GamePolyarcgon) shape;
            if (!(shape1.additionalAttributes instanceof GameShapeAdditionalAttributesForDrawingEtc)) { // also checks for null
                shape1.draw(canvas);
                return;
            }
            GameShapeAdditionalAttributesForDrawingEtc attributes = (GameShapeAdditionalAttributesForDrawingEtc) shape1.additionalAttributes;
            if (attributes.specialness == GameShapeAdditionalAttributesForDrawingEtc.Specialness.RIPPLE) {
                int[] colorsGradient = new int[]{
                        Color.argb(0, 255, 255, 0),
                        Color.argb(255, 255, 255, 0),
                        Color.argb(0, 255, 255, 0)};
                float[] stopsGradient = new float[] {0.8f, 0.9f, 1};
                RadialGradient radialGradient = new RadialGradient((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

                //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
                Paint paint = new Paint();
                paint.setDither(true);
                paint.setAntiAlias(true);
                paint.setShader(radialGradient);

                canvas.drawCircle((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, paint);

            } else if (attributes.specialness == GameShapeAdditionalAttributesForDrawingEtc.Specialness.BALL) {
                double edgeBrightness = 0.5;
                int[] colorsGradient = new int[]{
                        attributes.color,
                        attributes.color,
                        Color.rgb((int) (Color.red(attributes.color)*edgeBrightness), (int) (Color.blue(attributes.color)*edgeBrightness), (int) (Color.green(attributes.color)*edgeBrightness))};
                float[] stopsGradient = new float[]{0, 0.5f, 1};
                RadialGradient radialGradient = new RadialGradient((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

                //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
                Paint paint = new Paint();
                paint.setDither(true);
                paint.setAntiAlias(true);
                paint.setShader(radialGradient);

                canvas.drawCircle((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, paint);
            } else {
                Path path = shape1.getPathForDrawing();

                Paint paint = new Paint();
                if (Color.alpha(attributes.color) > 0) {
                    paint.setColor(attributes.color);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(path, paint);
                }

                if (attributes.edgeThickness > 0 && Color.alpha(attributes.edgeColor) > 0) {
                    paint.setColor(attributes.edgeColor);
                    paint.setDither(true);
                    paint.setAntiAlias(true);
                    int multiplier = Math.min(game.width, game.height);
                    MaskFilter filter = paint.getMaskFilter();
                    paint.setMaskFilter(new BlurMaskFilter((float) attributes.edgeThickness * multiplier, BlurMaskFilter.Blur.INNER));
                    canvas.drawPath(path, paint);
                    paint.setMaskFilter(filter);
                }
            }
        } else {
            shape.draw(canvas);
            //do this later
        }
    }
    public static void drawPotentialShadow(Canvas canvas, GameShape shape, RippleGolfGame game) {
        if (shape instanceof GamePolyarcgon) {
            GamePolyarcgon shape1 = (GamePolyarcgon) shape;
            if (!(shape1.additionalAttributes instanceof GameShapeAdditionalAttributesForDrawingEtc)) { // also checks for null
                return;
            }
            GameShapeAdditionalAttributesForDrawingEtc attributes = (GameShapeAdditionalAttributesForDrawingEtc) shape1.additionalAttributes;
            if (attributes.shadowThickness > 0 && Color.alpha(attributes.shadowColor) > 0) {
                Path path = shape1.getPathForDrawing();
                Paint paint = new Paint();
                paint.setColor(attributes.shadowColor);
                paint.setDither(true);
                paint.setAntiAlias(true);
                int multiplier = Math.min(game.width, game. height);
                MaskFilter filter = paint.getMaskFilter();
                paint.setMaskFilter(new BlurMaskFilter((float) attributes.shadowThickness * multiplier, BlurMaskFilter.Blur.OUTER));
                canvas.drawPath(path, paint);
                paint.setMaskFilter(filter);
            }
        }
    }

    public static void drawPotentialShadow(Canvas canvas, ArrayList<GameShape> shapes, RippleGolfGame game) {
        for (GameShape s : shapes) {
            drawPotentialShadow(canvas, s, game);
        }
    }
    public static void draw(Canvas canvas, ArrayList<GameShape> shapes, RippleGolfGame game) {
        for (GameShape s : shapes) {
            draw(canvas, s, game);
        }
    }
}

/**
 * Attributes of a shape that helps with drawing
 */
class GameShapeAdditionalAttributesForDrawingEtc {
    public static final GameShapeAdditionalAttributesForDrawingEtc WHITE_BALL_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.WHITE, Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.BALL);
    public static final GameShapeAdditionalAttributesForDrawingEtc DARK_GREEN_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.rgb(0x00, 0x80, 0x00), Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.NONE);
    public static final GameShapeAdditionalAttributesForDrawingEtc SCI_FI_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.TRANSPARENT, Color.TRANSPARENT, 0, Color.CYAN, 0.05, Specialness.NONE);
    public static final GameShapeAdditionalAttributesForDrawingEtc RIPPLE_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.TRANSPARENT, Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.RIPPLE);
    public final int color;
    public final int edgeColor;
    public final double edgeThickness;
    public final int shadowColor;
    public final double shadowThickness;
    public final Specialness specialness;
    enum Specialness {
        RIPPLE,
        BALL,
        NONE;
    }

    GameShapeAdditionalAttributesForDrawingEtc(int color, int edgeColor, double edgeThickness, int shadowColor, double shadowThickness, Specialness specialness) {
        this.color = color;
        this.edgeColor = edgeColor;
        this.edgeThickness = edgeThickness;
        this.shadowColor = shadowColor;
        this.shadowThickness = shadowThickness;
        this.specialness = (specialness != null) ? specialness : Specialness.NONE;
    }
}
class GameMaze {
    static class GameCircularMaze {
        int randomSeedUsed = 0;
        double pathThickness;
        double wallThickness;
        ArrayList<GameCircularMazeCell> cells;
        ArrayList<GameMazeWall> mainWalls;
        ArrayList<GameMazeWall> outerWalls;
        static class GameCircularMazeCell {
            double approximateX;
            double approximateY;
            LinkedHashMap<GameCircularMazeCell, GameMazeWall> neighbours;

            public GameCircularMazeCell(double approximateX, double approximateY) {
                this.approximateX = approximateX;
                this.approximateY = approximateY;
                this.neighbours = new LinkedHashMap<>();
            }

            public GameCircularMazeCell() {
                this(0, 0);
            }

            public void connect(GameCircularMazeCell other, GameMazeWall wallInBetween) {
                neighbours.put(other, wallInBetween);
                other.neighbours.put(this, wallInBetween);
            }
        }
        public GameCircularMaze(double x, double y, int rings, double pathThickness, double wallThickness, boolean smallCenter) {
            this.pathThickness = pathThickness;
            this.wallThickness = wallThickness;
            cells = new ArrayList<>();
            mainWalls = new ArrayList<>();
            outerWalls = new ArrayList<>();
            cells.add(new GameCircularMazeCell(x, y));
            int prevSubDivisions = 1;
            double wallRadius = smallCenter ? pathThickness/2.0 + wallThickness/2.0 : pathThickness + wallThickness/2.0;
            for (int i=0; i<rings; i++) {
                if (i < rings-1) {
                    double nextWallRadius = wallRadius + pathThickness + wallThickness;
                    double cellRadius = (wallRadius + nextWallRadius)/2.0;

                    double numberOfSubDivisionsThatFit = 2 * Math.PI / (2 * Math.asin((pathThickness + wallThickness) / (2 * wallRadius)));
                    int nextSubDivisions = (int) Math.pow(2, (int) (Math.log(numberOfSubDivisionsThatFit) / Math.log(2) + MAX_ROUNDING_ERROR)); // need rounding error here otherwise 2.0 becomes 1.0
                    int cellsBeforeThisRing = cells.size();

                    // first create the cells so can connect them in a loop
                    for (int j = 0; j < nextSubDivisions; j++) {
                        cells.add(new GameCircularMazeCell());
                    }

                    GameCircularMazeCell lastCell = cells.get(cells.size() - 1);
                    for (int j = 0; j < nextSubDivisions; j++) {
                        double startAngle = 2.0 * Math.PI * (((double) j) / nextSubDivisions);
                        double endAngle = 2.0 * Math.PI * (((double) j + 1.0) / nextSubDivisions);
                        GameCircularMazeCell newCell = cells.get(cellsBeforeThisRing + j);
                        newCell.approximateX = x + cellRadius * Math.cos((startAngle + endAngle) / 2);
                        newCell.approximateY = y + cellRadius * Math.sin((startAngle + endAngle) / 2);
                        GameCircularMazeCell connectedInnerCell = cells.get(cellsBeforeThisRing - prevSubDivisions + prevSubDivisions * j / nextSubDivisions);
                        double cosStartAngle = Math.cos(startAngle);
                        double sinStartAngle = Math.sin(startAngle);
                        double cosEndAngle = (j == nextSubDivisions - 1) ? 1 : Math.cos(endAngle); //avoid rounding error otherwise points aren't equal
                        double sinEndAngle = (j == nextSubDivisions - 1) ? 0 : Math.sin(endAngle);
                        GameMazeWall innerWall = new GameMazeWall(x + wallRadius * cosStartAngle, y + wallRadius * sinStartAngle, x + wallRadius * cosEndAngle, y + wallRadius * sinEndAngle, endAngle - startAngle);
                        newCell.connect(connectedInnerCell, innerWall);
                        mainWalls.add(innerWall);

                        GameMazeWall radialWall = new GameMazeWall(innerWall.start.x, innerWall.start.y, x + nextWallRadius * cosStartAngle, y + nextWallRadius * sinStartAngle, 0);
                        newCell.connect(lastCell, radialWall);
                        mainWalls.add(radialWall);
                        lastCell = newCell;
                    }

                    wallRadius = nextWallRadius;
                    prevSubDivisions = nextSubDivisions;
                } else {
//                    outerWall1 = new GameMazeWall(x-wallRadius, y, x+wallRadius, y, Math.PI);
//                    outerWall2 = new GameMazeWall(x+wallRadius, y, x-wallRadius, y, Math.PI);
                    for (int j = 0; j < prevSubDivisions; j++) {
                        double startAngle = 2.0 * Math.PI * (((double) j) / prevSubDivisions);
                        double endAngle = 2.0 * Math.PI * (((double) j + 1.0) / prevSubDivisions);
                        double cosStartAngle = Math.cos(startAngle);
                        double sinStartAngle = Math.sin(startAngle);
                        double cosEndAngle = (j == prevSubDivisions - 1) ? 1 : Math.cos(endAngle); //avoid rounding error otherwise points aren't equal
                        double sinEndAngle = (j == prevSubDivisions - 1) ? 0 : Math.sin(endAngle);
                        GameMazeWall outerWall = new GameMazeWall(x + wallRadius * cosStartAngle, y + wallRadius * sinStartAngle, x + wallRadius * cosEndAngle, y + wallRadius * sinEndAngle, endAngle - startAngle);
                        outerWalls.add(outerWall);
                    }
                }
            }
        }
        public void randomize(double startX, double startY, double finishX, double finishY, int seed) {
            double closestDistanceSqToStart = Double.MAX_VALUE;
            double closestDistanceSqToFinish = Double.MAX_VALUE;
            GameCircularMazeCell startCell = null;
            GameCircularMazeCell finishCell = null;
            for (GameCircularMazeCell cell : cells) {
                double distanceSqToStart = (cell.approximateX-startX)*(cell.approximateX-startX) + (cell.approximateY-startY)*(cell.approximateY-startY);
                double distanceSqToFinish = (cell.approximateX-finishX)*(cell.approximateX-finishX) + (cell.approximateY-finishY)*(cell.approximateY-finishY);
                if (distanceSqToStart < closestDistanceSqToStart && distanceSqToFinish < closestDistanceSqToFinish) {
                    if (distanceSqToStart/closestDistanceSqToStart < distanceSqToFinish/closestDistanceSqToFinish) {
                        startCell = cell;
                        closestDistanceSqToStart = distanceSqToStart;
                    } else {
                        finishCell = cell;
                        closestDistanceSqToFinish = distanceSqToFinish;
                    }
                } else if (distanceSqToStart < closestDistanceSqToStart) {
                    startCell = cell;
                    closestDistanceSqToStart = distanceSqToStart;
                } else if (distanceSqToFinish < closestDistanceSqToFinish) {
                    finishCell = cell;
                    closestDistanceSqToFinish = distanceSqToFinish;
                }
            }

            ArrayList<GameMazeWall> wallsToRemove = new ArrayList<>();
            // use linked because want to generate same maze for each seed
            // do not use ArraySet, it did not iterate the same way
            LinkedHashSet<GameCircularMazeCell> cellsAlreadyConnected = new LinkedHashSet<>();
            Random random = null;
//int testing = 0;
            int maxAttempts = 100000;
            attemptsLoop: for (int attempt=0; attempt<maxAttempts; attempt++) {
                randomSeedUsed = (random==null) ? seed : random.nextInt();
                random = new Random(randomSeedUsed);
                wallsToRemove.clear();
                cellsAlreadyConnected.clear();
                GameCircularMazeCell currentCell = startCell;
                cellsAlreadyConnected.add(currentCell);
                while (currentCell != finishCell) {
//if (testing++ < 100) {
//    String connected = "";
//    for (GameCircularMazeCell celly : cellsAlreadyConnected) {
//        connected += cells.indexOf(celly) + ", ";
//    }
//    Log.i("me", seed + ", " + connected);
//}
                    currentCell = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, true, random);
                    if (currentCell == null) {
                        continue attemptsLoop; // no unfinished neighbours, the path trapped itself
                    }
                }
                if (cellsAlreadyConnected.size() >= 0.3*cells.size() && cellsAlreadyConnected.size() <= 0.6*cells.size()) {
                    // good maze, no short path to finish, also no ridiculously long path like a labyrinth
                    // 30%-60% of maze for correct path, rest for wrong paths and "closets" along correct path.
                    break;
                }
            }

            ArrayList<GameCircularMazeCell> cellsNotYetConnected = new ArrayList<>(cells);
            while (cellsAlreadyConnected.size() < cells.size()) {
                cellsNotYetConnected.removeAll(cellsAlreadyConnected);
                int start = random.nextInt(cellsNotYetConnected.size());
                GameCircularMazeCell currentCell = cellsNotYetConnected.get(start);
                GameCircularMazeCell neighbour = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, false, random);
                if (neighbour == null) {
                    continue; // no neighbours
                }
                cellsAlreadyConnected.add(currentCell);
                do {
                    //try to make a branch
                    currentCell = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, true, random);
                } while (currentCell != null); // don't stop until trapped to use up as many cells in long paths
            }

            mainWalls.removeAll(wallsToRemove);
        }


        private static GameCircularMazeCell findAndProcessNeighbour(GameCircularMazeCell currentCell, LinkedHashSet<GameCircularMazeCell> cellsAlreadyConnected, ArrayList<GameMazeWall> wallsToRemove, boolean pickNewNeighbour, Random random) {
            ArrayList<GameCircularMazeCell> pickableNeighbours = new ArrayList<>();
            for (GameCircularMazeCell cell : currentCell.neighbours.keySet()) {
                if (pickNewNeighbour == !cellsAlreadyConnected.contains(cell)) {
                    pickableNeighbours.add(cell);
                }
            }
            if (pickableNeighbours.isEmpty()) {
                // whoops we're trapped
                return null;
            }
            int neighbour = random.nextInt(pickableNeighbours.size());
            GameCircularMazeCell neighbourCell = pickableNeighbours.get(neighbour);
            wallsToRemove.add(currentCell.neighbours.get(neighbourCell));
            if (pickNewNeighbour) {
                cellsAlreadyConnected.add(neighbourCell);
            }
            return neighbourCell;
        }

        public GameMaze asGameMaze() {
            GameMaze maze = new GameMaze();
            maze.walls.addAll(mainWalls);
            maze.walls.addAll(outerWalls);
//            maze.addWall(outerWall1);
//            maze.addWall(outerWall2);
            return maze;
        }
        public void build(GamePolyarcgonBuilder builder) {
            asGameMaze().build(builder, wallThickness);
        }

    }
    static final double MAX_ROUNDING_ERROR = 1000000.0*Double.MIN_VALUE/Double.MIN_NORMAL;

    ArrayList<GameMazeWall> walls;

    public static void saveMaze(GameMaze maze, double x, double y, double pathThickness) {
        ArrayList<GameMazeWall> templateWalls = new ArrayList<>();
        for (GameMazeWall wall : maze.walls) {
            templateWalls.add(new GameMazeWall((wall.start.x-x)/pathThickness, (wall.start.y-y)/pathThickness, (wall.end.x-x)/pathThickness, (wall.end.y-y)/pathThickness, wall.arcAngleChange));
        }

        Gson gson = new Gson(); // Or use new GsonBuilder().create();
        String json = gson.toJson(templateWalls); // serializes target to JSON

//        Log.i("me", json);
        System.out.println(json);
    }

    public static GameMaze loadMaze(Context context, int id, double x, double y, double pathThickness) {
        ArrayList<GameMazeWall> templateWalls = SaveAndLoad.gsonLoadRawResource(context, id, new TypeToken<ArrayList<GameMazeWall>>(){}.getType());
        GameMaze maze = new GameMaze();
        for (GameMazeWall wall : templateWalls) {
            maze.walls.add(new GameMazeWall(wall.start.x*pathThickness+x, wall.start.y*pathThickness+y, wall.end.x*pathThickness+x, wall.end.y*pathThickness+y, wall.arcAngleChange));
        }
        return maze;
    }

    public GameMaze() {
        walls = new ArrayList<>();
    }

    public void addWall(GameMazeWall wall) {
        //TODO: check for intersections
        HashMap<GameMazeWall, TreeMap<Double, DoublePoint>> intersections = new HashMap<>();
        for (GameMazeWall prevWall : walls) {
            getIntersections(intersections, wall, prevWall);
        }
        boolean cutWall = false;
        for (Map.Entry<GameMazeWall, TreeMap<Double, DoublePoint>> intersectionDataEntry : intersections.entrySet()) {
            GameMazeWall wallToCut = intersectionDataEntry.getKey();
            if (wallToCut == wall) {
                cutWall = true;
            } else {
                walls.remove(wallToCut);
            }
            DoublePoint subWallStart = wallToCut.start;
            double subWallStartFraction = 0;
            for (Map.Entry<Double, DoublePoint> intersection : intersectionDataEntry.getValue().entrySet()) {
                DoublePoint subWallEnd = intersection.getValue();
                double subWallEndFraction = intersection.getKey();
                if (subWallEndFraction > MAX_ROUNDING_ERROR || subWallStartFraction != 0) {
                    walls.add(new GameMazeWall(subWallStart, subWallEnd, wall.arcAngleChange*(subWallEndFraction-subWallStartFraction)));
                }
                subWallStart = subWallEnd;
                subWallStartFraction = subWallEndFraction;
            }
            if (subWallStartFraction < 1-MAX_ROUNDING_ERROR) {
                walls.add(new GameMazeWall(subWallStart, wallToCut.end, wall.arcAngleChange*(1-subWallStartFraction)));
            }
        }
        if (!cutWall) {
            walls.add(wall);
        }
    }


    private static void getIntersections(HashMap<GameMazeWall, TreeMap<Double, DoublePoint>> intersections, GameMazeWall segment1, GameMazeWall segment2) {
        GetPolyarcgonPointAndCacheFromWall segment1PointCache = new GetPolyarcgonPointAndCacheFromWall(segment1);
        GetPolyarcgonPointAndCacheFromWall segment2PointCache = new GetPolyarcgonPointAndCacheFromWall(segment2);
        OverlapHandler handler = new OverlapHandler() {

            @Override
            public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
                if (!isRealIntersection) {
                    return;
                }

                final GameMazeWall segmentToMark = lineSegmentIsFirstShape ? segment1 : segment2;
                final GetPolyarcgonPointAndCacheFromWall segmentToMarkPointCache = lineSegmentIsFirstShape ? segment1PointCache : segment2PointCache;
                final double distance = Math.sqrt((endX-startX)*(endX-startX) + (endY-startY)*(endY-startY));
                final double fullDistance = Math.sqrt((segmentToMarkPointCache.endPoint.x-segmentToMarkPointCache.startPoint.x)*(segmentToMarkPointCache.endPoint.x-segmentToMarkPointCache.startPoint.x) + (segmentToMarkPointCache.endPoint.y-segmentToMarkPointCache.startPoint.y)*(segmentToMarkPointCache.endPoint.y-segmentToMarkPointCache.startPoint.y));
                final double fraction = distance/fullDistance;
                TreeMap<Double, DoublePoint> intersectionData = intersections.get(segmentToMark);
                if (intersectionData == null) {
                    intersectionData = new TreeMap<>();
                    intersections.put(segmentToMark, intersectionData);
                }
                intersectionData.put(fraction, new DoublePoint(endX, endY));
            }

            @Override
            public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
                if (!isRealIntersection) {
                    return;
                }

                final GameMazeWall segmentToMark = arcIsFirstShape ? segment1 : segment2;
                final double fraction = segmentToMark.arcAngleChange/arcAngleChange;
                TreeMap<Double, DoublePoint> intersectionData = intersections.get(segmentToMark);
                if (intersectionData == null) {
                    intersectionData = new TreeMap<>();
                    intersections.put(segmentToMark, intersectionData);
                }
                intersectionData.put(fraction, new DoublePoint(arcEndX, arcEndY));
            }
        };
        if (segment1PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
            if (segment2PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment2PointCache.endPointCache, MAX_ROUNDING_ERROR);
            } else {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithArcToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment2PointCache.endPointCache, true, MAX_ROUNDING_ERROR);
            }
        } else {
            if (segment2PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithArcToOverlap(handler, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment2PointCache.endPointCache, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment1PointCache.endPointCache, false, MAX_ROUNDING_ERROR);
            } else {
                GamePolyarcgon.addPotentialArcIntersectionWithArcToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment2PointCache.endPointCache, MAX_ROUNDING_ERROR);
            }
        }
    }
    static class GetPolyarcgonPointAndCacheFromWall {
        final PolyarcgonPoint startPoint;
        final PolyarcgonPoint endPoint;
        final GamePolyarcgon.PolyarcgonPointCache startPointCache;
        final GamePolyarcgon.PolyarcgonPointCache endPointCache;
        GetPolyarcgonPointAndCacheFromWall(GameMazeWall wall) {
            startPoint = new PolyarcgonPoint(wall.start.x, wall.start.y, wall.arcAngleChange, false);
            endPoint = new PolyarcgonPoint(wall.end.x, wall.end.y, wall.arcAngleChange, false);
            startPointCache = startPoint.isAlmostStraight() ? new GamePolyarcgon.PolyarcgonStraightPointCache(startPoint) : new GamePolyarcgon.PolyarcgonArcedPointCache(startPoint);
            endPointCache = endPoint.isAlmostStraight() ? new GamePolyarcgon.PolyarcgonStraightPointCache(endPoint) : new GamePolyarcgon.PolyarcgonArcedPointCache(endPoint);

            startPointCache.updatePositionCache(0, 0, 1, 0);
            endPointCache.updatePositionCache(0, 0, 1, 0);

            endPointCache.updateLineOrArcCache(startPointCache);
        }
    }
//    private static ArrayList<ContourlessSegment> convertToContourlessSegments(ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints) {
//        ArrayList<ContourlessSegment> contourlessSegments = new ArrayList<>();
//        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
//            if (!contour.isEmpty()) {
//                ContourlessSegment firstContourlessSegment = null;
//                ContourlessSegment lastContourlessSegment = null;
//                for (GamePolyarcgonBuilderPoint point : contour) {
//                    ContourlessSegment contourlessSegment = new ContourlessSegment(point, null);
//                    contourlessSegments.add(contourlessSegment);
//                    if (lastContourlessSegment != null) {
//                        lastContourlessSegment.nextPoint = contourlessSegment;
//                    } else {
//                        firstContourlessSegment = contourlessSegment;
//                    }
//                    lastContourlessSegment = contourlessSegment;
//                }
//                lastContourlessSegment.nextPoint = firstContourlessSegment;
//            }
//        }
//        return contourlessSegments;
//    }
//    private static ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> convertToContoursOfPoints(ArrayList<ContourlessSegment> contourlessSegments) {
//        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints = new ArrayList<>();
//        while (!contourlessSegments.isEmpty()) {
//            ArrayList<GamePolyarcgonBuilderPoint> contour = new ArrayList<>();
//            contoursOfPoints.add(contour);
//            ContourlessSegment contourlessSegment = contourlessSegments.remove(0);
//            contour.add(contourlessSegment.thisPoint);
//            ContourlessSegment nextContourlessSegment = contourlessSegment.nextPoint;
//            while (nextContourlessSegment != contourlessSegment) {
//                contourlessSegments.remove(nextContourlessSegment);
//                contour.add(nextContourlessSegment.thisPoint);
//                nextContourlessSegment = nextContourlessSegment.nextPoint;
//            }
//        }
//        return contoursOfPoints;
//    }


    static class AnglePlus implements Comparable<AnglePlus> {
        double angle;
        double curvature;

        public AnglePlus(double angle, double curvature) {
            this.angle = angle;
            this.curvature = curvature;
        }

        @Override
        public int compareTo(AnglePlus other) {
            if (this == other) {
                return 0;
            } else if (angle > other.angle + MAX_ROUNDING_ERROR) {
                return 1;
            } else if (angle < other.angle - MAX_ROUNDING_ERROR) {
                return -1;
            } else if (curvature > other.curvature + MAX_ROUNDING_ERROR) {
                return 1;
            } else if (curvature < other.curvature - MAX_ROUNDING_ERROR) {
                return -1;
            } else {
                return 0;
            }
        }
//        public static boolean almost180DegreesApart(AnglePlus angle1, AnglePlus angle2) {
//        }
    }
    static class GameMazeWall {
        @NonNull
        DoublePoint start;
        @NonNull
        DoublePoint end;
        double arcAngleChange;

        public GameMazeWall(double x1, double y1, double x2, double y2, double arcAngleChange) {
            this(new DoublePoint(x1, y1), new DoublePoint(x2, y2), arcAngleChange);
        }

        public GameMazeWall(@NonNull DoublePoint start, @NonNull DoublePoint end, double arcAngleChange) {
            this.start = start;
            this.end = end;
            this.arcAngleChange = arcAngleChange;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GameMazeWall that = (GameMazeWall) o;
            return Double.compare(that.arcAngleChange, arcAngleChange) == 0 && start.equals(that.start) && end.equals(that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end, arcAngleChange);
        }
    }
    static class NodeInfo {
        HashMap<GameMazeWall, AnglePlus> anglesOfConnectedWalls1;
        TreeMap<AnglePlus, GameMazeWall> anglesOfConnectedWalls2;
        // Really having two maps is unnecessary since each node has very few walls.
        // But TreeMap/HashMap have no methods for getting a key from a value

        NodeInfo() {
            anglesOfConnectedWalls1 = new HashMap<>();
            anglesOfConnectedWalls2 = new TreeMap<>();
        }
        void addWall(GameMazeWall wall, boolean nodeIsAtWallStartInsteadOfEnd) {
            double angle;
            int factor;
            if (nodeIsAtWallStartInsteadOfEnd) {
                factor = 1;
                angle = Math.atan2(wall.end.y -wall.start.y, wall.end.x -wall.start.x) - wall.arcAngleChange / 2.0;
            } else {
                factor = -1;
                angle = Math.atan2(wall.start.y -wall.end.y, wall.start.x -wall.end.x) + wall.arcAngleChange / 2.0;
            }
            AnglePlus anglePlus;
            if (PolyarcgonPoint.isAlmostStraight(wall.arcAngleChange)) {
                anglePlus = new AnglePlus(angle, 0);
            } else {
                anglePlus = new AnglePlus(angle, 1.0 * factor / GamePolyarcgon.getArcCenterAndSignedRadius(wall.start.x, wall.start.y, wall.end.x, wall.end.y, wall.arcAngleChange)[2]);
            }
            anglesOfConnectedWalls2.put(anglePlus, wall);
            anglesOfConnectedWalls1.put(wall, anglePlus);
        }

        private Map.Entry<AnglePlus, GameMazeWall> pickNextWallClockwise(GameMazeWall initialWall, boolean counterClockwiseInstead) {
            final AnglePlus angle = anglesOfConnectedWalls1.get(initialWall);
            Map.Entry<AnglePlus, GameMazeWall> next = counterClockwiseInstead ? anglesOfConnectedWalls2.lowerEntry(angle) : anglesOfConnectedWalls2.higherEntry(angle);
            if (next == null) {
                next = counterClockwiseInstead ? anglesOfConnectedWalls2.lastEntry() : anglesOfConnectedWalls2.firstEntry();
            }
            return next;
        }

    }
    public void build(GamePolyarcgonBuilder builder, double wallThickness) {
        builder.newContour();

        HashMap<DoublePoint, NodeInfo> nodes = new HashMap<>();
        for (GameMazeWall wall : walls) {
            NodeInfo startNodeInfo = nodes.get(wall.start);
            if (startNodeInfo == null) {
                startNodeInfo = new NodeInfo();
                nodes.put(wall.start, startNodeInfo);
            }
            startNodeInfo.addWall(wall, true);
            NodeInfo endNodeInfo = nodes.get(wall.end);
            if (endNodeInfo == null) {
                endNodeInfo = new NodeInfo();
                nodes.put(wall.end, endNodeInfo);
            }
            endNodeInfo.addWall(wall, false);
        }

        HashSet<GameMazeWall> rightSideCompletedWalls = new HashSet<>();
        HashSet<GameMazeWall> leftSideCompletedWalls = new HashSet<>();

        for (GameMazeWall wall : walls) {
            for (int direction=-1; direction==-1 || direction==1; direction+=2) {
                DoublePoint directedStart;
                DoublePoint directedEnd;
                if (direction == 1) {
                    if (rightSideCompletedWalls.contains(wall)) {
                        continue;
                    }
                    directedStart = wall.start;
                    directedEnd = wall.end;
                } else {
                    if (leftSideCompletedWalls.contains(wall)) {
                        continue;
                    }
                    directedStart = wall.end;
                    directedEnd = wall.start;
                }
                // first, we back up to the "real" starting point:
                GetNextTurn getPrevTurn = new GetNextTurn(directedEnd, wall, nodes, true);

                if (getPrevTurn.isEndlessLoop) {
                    // if it's an endless loop, we're done just add a circle
                    rightSideCompletedWalls.addAll(getPrevTurn.rightSideWallsIncluded);
                    leftSideCompletedWalls.addAll(getPrevTurn.leftSideWallsIncluded);

                    double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(directedStart.x, directedStart.y, directedEnd.x, directedEnd.y, direction*wall.arcAngleChange);
                    builder.addCircleContour(arcCenterAndSignedRadius[0], arcCenterAndSignedRadius[1], Math.abs(arcCenterAndSignedRadius[2] + wallThickness/2.0), arcCenterAndSignedRadius[2] > 0);
                } else {
                    DoublePoint lastTurn = getPrevTurn.nextTurn;
                    GameMazeWall wallAfterLastTurn = getPrevTurn.wallBeforeNextTurn;

                    // next, we find the next turn:
                    do {
                        GetNextTurn getNextTurn = new GetNextTurn(lastTurn, wallAfterLastTurn, nodes, false);

                        rightSideCompletedWalls.addAll(getNextTurn.rightSideWallsIncluded);
                        leftSideCompletedWalls.addAll(getNextTurn.leftSideWallsIncluded);

                        double arcAngleChange = getNextTurn.totalArcAngleChange;
                        if (Math.abs(Math.abs(getNextTurn.totalArcAngleChange) - 2*Math.PI) < MAX_ROUNDING_ERROR) {
                            //i.e. we've gone full circle and need an artificial point in between to avoid 360 degree arc angle change
                            DoublePoint pointRightAfterLastTurn;
                            double wallAfterLastTurnArcAngleChange;
                            if (lastTurn.equals(wallAfterLastTurn.start)) {
                                pointRightAfterLastTurn = wallAfterLastTurn.end;
                                wallAfterLastTurnArcAngleChange = wallAfterLastTurn.arcAngleChange;
                            } else {
                                pointRightAfterLastTurn = wallAfterLastTurn.start;
                                wallAfterLastTurnArcAngleChange = -wallAfterLastTurn.arcAngleChange;
                            }
                            builder.arcToVirtualRoundedTurn(pointRightAfterLastTurn.x, pointRightAfterLastTurn.y, wallAfterLastTurnArcAngleChange, 0, false, wallThickness/2.0, true);
                            arcAngleChange -= wallAfterLastTurnArcAngleChange;
                        }
                        if (Math.abs(getNextTurn.turnAngle) < MAX_ROUNDING_ERROR) {
                            // i.e. it's an inflection point not a real turn
                            builder.arcToVirtualRoundedTurn(getNextTurn.nextTurn.x, getNextTurn.nextTurn.y, arcAngleChange, 0, false, wallThickness/2.0, true);
                        } else {
                            builder.arcToVirtualRoundedTurn(getNextTurn.nextTurn.x, getNextTurn.nextTurn.y, arcAngleChange, wallThickness/2.0, getNextTurn.turnAngle > 0, wallThickness/2.0, true);
                        }
                        lastTurn = getNextTurn.nextTurn;
                        wallAfterLastTurn = getNextTurn.wallAfterNextTurn;
                    } while (!lastTurn.equals(getPrevTurn.nextTurn) || wallAfterLastTurn != getPrevTurn.wallBeforeNextTurn);
                    // We have to test both the turn and the wall because it's possible for either equality to be true
                    // without finishing the loop. e.g. at the end of each wall, you repeat the same wall twice but with
                    // a different turn. e.g. at the point of a Q shaped loop, you repeat the same point twice but
                    // without having finished looping the tip of the Q.

                    builder.newContour();
                }
            }
        }
    }
    static class GetNextTurn {
        final DoublePoint nextTurn;
        final GameMazeWall wallBeforeNextTurn;
        final GameMazeWall wallAfterNextTurn;
        final double totalArcAngleChange;
        final double turnAngle;
        final boolean isEndlessLoop; //Note the total arc angle change might be 360 degrees even if not endless loop
        final HashSet<GameMazeWall> rightSideWallsIncluded = new HashSet<>();
        final HashSet<GameMazeWall> leftSideWallsIncluded = new HashSet<>();
        GetNextTurn(DoublePoint startingTurn, GameMazeWall wallAfterStartingTurn, HashMap<DoublePoint, NodeInfo> nodes, boolean counterClockwiseInstead) {
            DoublePoint nextPoint = startingTurn;
            GameMazeWall wall = wallAfterStartingTurn;
            double subtotalArcAngleChange = 0;
            while (true) {
                // make next point refer to the point on the other side of the wall
                // also, calculate subtotalArcAngleChange
                if (wall.start.equals(nextPoint)) {
                    nextPoint = wall.end;
                    subtotalArcAngleChange += wall.arcAngleChange;
                    (counterClockwiseInstead ? leftSideWallsIncluded : rightSideWallsIncluded).add(wall);
                } else {
                    nextPoint = wall.start;
                    subtotalArcAngleChange -= wall.arcAngleChange;
                    (counterClockwiseInstead ? rightSideWallsIncluded : leftSideWallsIncluded).add(wall);
                }

                NodeInfo nextNode = nodes.get(nextPoint);
                assert nextNode != null;
                AnglePlus thisAngle = nextNode.anglesOfConnectedWalls1.get(wall);
                assert thisAngle != null;
                Map.Entry<AnglePlus, GameMazeWall> nextAngleAndWall = nextNode.pickNextWallClockwise(wall, counterClockwiseInstead);
                AnglePlus nextAngle = nextAngleAndWall.getKey();
                GameMazeWall nextWall = nextAngleAndWall.getValue();
                double currentTurnAngle;
                if (wall != nextWall) {
                    currentTurnAngle = ((nextAngle.angle - thisAngle.angle + 10 * Math.PI) % (2 * Math.PI)) - Math.PI;
                } else {
                    currentTurnAngle = counterClockwiseInstead ? -Math.PI : Math.PI;
                }

                // if next angle and this angle are almost 180 degrees apart
                if (Math.abs(currentTurnAngle) < MAX_ROUNDING_ERROR) {
                    if (Math.abs(nextAngle.curvature + thisAngle.curvature) < MAX_ROUNDING_ERROR) {
                        wall = nextWall;
                        // We only declare an endless loop if the wall is the same as before,
                        // not if the node is the same as before.
                        // Even if the node is the same as before it might not be an endless loop,
                        // if another wall comes out of the node.
                        if (wall == wallAfterStartingTurn) {
                            nextTurn = null;
                            wallBeforeNextTurn = null;
                            wallAfterNextTurn = null;
                            totalArcAngleChange = subtotalArcAngleChange;
                            turnAngle = 0;
                            isEndlessLoop = true;
                            return;
                        } else {
                            continue;
                        }
                    } else {
                        turnAngle = 0;
                    }
                } else {
                    turnAngle = currentTurnAngle;
                }
                nextTurn = nextPoint;
                wallBeforeNextTurn = wall;
                wallAfterNextTurn = nextWall;
                totalArcAngleChange = subtotalArcAngleChange;
                isEndlessLoop = false;
                break;
            }

        }
    }

}

/**
 * A class for conveniently building "polyarcgon" shapes
 */
class GamePolyarcgonBuilder {
    static class GamePolyarcgonBuilderPoint implements Cloneable {
        double x;
        double y;
        double arcAngleChange;
        double signedRoundedTurnRadius; // is positive if clockwise (or counterclockwise from the y is up frame of reference), negative otherwise
        double signedVirtualRoundedTurnRadius;
        // virtual rounded turn radius is useful for creating an offset
        // e.g. if signedVirtualRoundedTurnRadius == signedRoundedTurnRadius, then x and y are in the middle of the rounded turn circle
        // e.g. if signedVirtualRoundedTurnRadius == 0, then x and y are at where the "corner" would be if the rounded turn was sharpened
        // e.g. if signedVirtualRoundedTurnRadius == -signedRoundedTurnRadius, x and y would be even further towards the corner than the corner
        // e.g. twice as far assuming we have straight lines.
        // The negative version is useful for creating walls with corners which are both rounded on the outside and rounded on the inside.
        //  ______
        // (____  \
        //      \ |
        //      | |
        //      \_/
        // The inside rounded turn and the outside rounded turn can have the same x and y.
        // The outside rounded turn has signedVirtualRoundedTurnRadius == signedRoundedTurnRadius,
        // The inside rounded turn has signedVirtualRoundedTurnRadius == -signedRoundedTurnRadius.

        // These use the signedVirtualRoundedTurnRadius, not signedRoundedTurnRadius, though in the end these two should be equal
        // Beware these angles are offset by 180 degrees when we're using a negative signed radius.
        // This ensures that (cos(angle), sin(angle)) * signedRadius is correct.
        double roundedTurnStartAngle;
        double roundedTurnEndAngle;

        double roundedTurnStartX;
        double roundedTurnStartY;

        double roundedTurnEndX;
        double roundedTurnEndY;

        @NonNull
        public GamePolyarcgonBuilderPoint clone() {
            try {
                return (GamePolyarcgonBuilderPoint) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange) {
            this(x, y, arcAngleChange, 0, 0);
        }
        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange, double signedRoundedTurnRadius) {
            this(x, y, arcAngleChange, signedRoundedTurnRadius, signedRoundedTurnRadius);
        }
        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange, double signedRoundedTurnRadius, double signedVirtualRoundedTurnRadius) {
            this.x = x;
            this.y = y;
            this.roundedTurnStartX = x; // by default these are the same so that if the signedRoundedTurnRadius/signedVirtualRoundedTurnRadius are 0, things still work
            this.roundedTurnStartY = y;
            this.roundedTurnEndX = x;
            this.roundedTurnEndY = y;
            this.arcAngleChange = arcAngleChange;
            this.signedRoundedTurnRadius = signedRoundedTurnRadius;
            this.signedVirtualRoundedTurnRadius = signedVirtualRoundedTurnRadius;
        }
    }

    static final double MAX_ROUNDING_ERROR = 1000000 * Double.MIN_VALUE / Double.MIN_NORMAL;
    public ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints;

    public double density;
    public Object additionalAttributes;

    public double centerOfMassX;
    public double centerOfMassY;
    public boolean centerOfMassIsRelativeToDefaultCenterOfMass;

    public GamePolyarcgonBuilder() {
        reset();
    }

    ArrayList<PolyarcgonPoint> generatePoints() {

        ArrayList<PolyarcgonPoint> generatedPoints = new ArrayList<>();

        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            if (!contour.isEmpty()) {
                ArrayList<GamePolyarcgonBuilderPoint> contourClone = new ArrayList<>(contour);
                contourClone.replaceAll(GamePolyarcgonBuilderPoint::clone);

                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint prevPoint = contour.get((i + contour.size() - 1) % contour.size());
                    GamePolyarcgonBuilderPoint nextPoint = contour.get(i);
                    if (prevPoint.signedVirtualRoundedTurnRadius != 0 || nextPoint.signedVirtualRoundedTurnRadius != 0) {
                        processLineOrArcBetweenOneOrTwoRoundedTurns(prevPoint, nextPoint);
                    }
                }
                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint point = contour.get(i);
                    if (point.signedVirtualRoundedTurnRadius != point.signedRoundedTurnRadius) {
                        GamePolyarcgonBuilderPoint prevPoint = contour.get((i + contour.size() - 1) % contour.size());
                        GamePolyarcgonBuilderPoint nextPoint = contour.get((i + 1) % contour.size());
                        movePointToMakeVirtualRoundedTurnEqualRoundedTurn(point, prevPoint, nextPoint);
                    }
                }

                if (contoursOfPoints.size() >= 2) {
                    GamePolyarcgonBuilderPoint lastPoint = contour.get(contour.size() - 1);
                    generatedPoints.add(new PolyarcgonPoint(lastPoint.roundedTurnEndX, lastPoint.roundedTurnEndY, 0, true));
                }
                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint point = contour.get(i);
                    generatedPoints.add(new PolyarcgonPoint(point.roundedTurnStartX, point.roundedTurnStartY, point.arcAngleChange, false));
                    if (point.signedRoundedTurnRadius != 0) {
                        generatedPoints.add(new PolyarcgonPoint(point.roundedTurnEndX, point.roundedTurnEndY, (point.roundedTurnEndAngle - point.roundedTurnStartAngle + Math.copySign(10 * Math.PI, point.signedRoundedTurnRadius)) % (2 * Math.PI), false));
                    }
                }
            }
        }
        return generatedPoints;
    }

    public GamePolyarcgonBuilder addRectangleContour(double x1, double y1, double x2, double y2, boolean isClockwise) {
        newContour();
        lineTo(x1, y1);
        if (isClockwise) {
            lineTo(x2, y1);
            lineTo(x2, y2);
            lineTo(x1, y2);
        } else {
            lineTo(x1, y2);
            lineTo(x2, y2);
            lineTo(x2, y1);
        }
        newContour();
        return this;
    }
    public GamePolyarcgonBuilder addRoundedRectangleContour(double x1, double y1, double x2, double y2, double radius, boolean isClockwise) {
        newContour();
        lineToRoundedTurn(x1+radius, y1+radius, radius, isClockwise);
        if (isClockwise) {
            lineToRoundedTurn(x2-radius, y1+radius, radius, true);
            lineToRoundedTurn(x2-radius, y2-radius, radius, true);
            lineToRoundedTurn(x1+radius, y2-radius, radius, true);
        } else {
            lineToRoundedTurn(x1+radius, y2-radius, radius, false);
            lineToRoundedTurn(x2-radius, y2-radius, radius, false);
            lineToRoundedTurn(x2-radius, y1+radius, radius, false);
        }
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder withCenterOfMass(double x, double y, boolean isRelativeToDefaultCenterOfMass) {
        this.centerOfMassX = x;
        this.centerOfMassY = y;
        this.centerOfMassIsRelativeToDefaultCenterOfMass = isRelativeToDefaultCenterOfMass;
        return this;
    }
    public GamePolyarcgonBuilder withDensity(double density) {
        this.density = density;
        return this;
    }
    public GamePolyarcgonBuilder withAdditionalAttributes(Object additionalAttributes) {
        this.additionalAttributes = additionalAttributes;
        return this;
    }
    public GamePolyarcgonBuilder newContour() {
        if (!contoursOfPoints.get(contoursOfPoints.size()-1).isEmpty()) {
            contoursOfPoints.add(new ArrayList<>());
        }
        return this;
    }
    public GamePolyarcgonBuilder lineTo(double x, double y) {
        return arcTo(x, y, 0);
    }
    public GamePolyarcgonBuilder arcTo(double x, double y, double arcAngleChange) {
        contoursOfPoints.get(contoursOfPoints.size()-1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange));
        return this;
    }
    public GamePolyarcgonBuilder lineToRoundedTurn(double x, double y, double roundedTurnRadius, boolean isClockwise) {
        return arcToRoundedTurn(x, y, 0, roundedTurnRadius, isClockwise);
    }
    public GamePolyarcgonBuilder arcToRoundedTurn(double x, double y, double arcAngleChange, double roundedTurnRadius, boolean isClockwise) {
        contoursOfPoints.get(contoursOfPoints.size()-1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange, isClockwise ? roundedTurnRadius : -roundedTurnRadius));
        return this;
    }
    public GamePolyarcgonBuilder lineToVirtualRoundedTurn(double x, double y, double roundedTurnRadius, boolean isClockwise, double virtualRoundedTurnRadius, boolean isVirtualTurnClockwise) {
        return arcToVirtualRoundedTurn(x, y, 0, roundedTurnRadius, isClockwise, virtualRoundedTurnRadius, isVirtualTurnClockwise);
    }
    public GamePolyarcgonBuilder arcToVirtualRoundedTurn(double x, double y, double arcAngleChange, double roundedTurnRadius, boolean isClockwise, double virtualRoundedTurnRadius, boolean isVirtualTurnClockwise) {
        contoursOfPoints.get(contoursOfPoints.size()-1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange, isClockwise ? roundedTurnRadius : -roundedTurnRadius, isVirtualTurnClockwise ? virtualRoundedTurnRadius : -virtualRoundedTurnRadius));
        return this;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurn(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        boolean firstIsStraight = PolyarcgonPoint.isAlmostStraight(pointToMove.arcAngleChange);
        boolean secondIsStraight = PolyarcgonPoint.isAlmostStraight(nextPoint.arcAngleChange);
        if (firstIsStraight && secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothStraight(pointToMove, prevPoint, nextPoint);
        } else if (firstIsStraight && !secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(pointToMove, prevPoint, nextPoint, true);
        } else if (!firstIsStraight && secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(pointToMove, nextPoint, prevPoint, false);
        } else {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothArced(pointToMove, prevPoint, nextPoint);
        }
        // we have to update each rounded turn after moving it, otherwise the formulas for moving rounded turns won't work
        processLineOrArcBetweenOneOrTwoRoundedTurns(prevPoint, pointToMove);
        processLineOrArcBetweenOneOrTwoRoundedTurns(pointToMove, nextPoint);
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothStraight(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double firstUnitVectorX = pointToMove.roundedTurnStartX-prevPoint.roundedTurnEndX;
        double firstUnitVectorY = pointToMove.roundedTurnStartY-prevPoint.roundedTurnEndY;
        double secondUnitVectorX = pointToMove.roundedTurnEndX-nextPoint.roundedTurnStartX;
        double secondUnitVectorY = pointToMove.roundedTurnEndY-nextPoint.roundedTurnStartY;
        double firstVectorLength = Math.sqrt(firstUnitVectorX*firstUnitVectorX + firstUnitVectorY*firstUnitVectorY);
        double secondVectorLength = Math.sqrt(secondUnitVectorX*secondUnitVectorX + secondUnitVectorY*secondUnitVectorY);

        // normalize
        firstUnitVectorX /= firstVectorLength;
        firstUnitVectorY /= firstVectorLength;
        secondUnitVectorX /= secondVectorLength;
        secondUnitVectorY /= secondVectorLength;

        double averageUnitVectorX = firstUnitVectorX+secondUnitVectorX;
        double averageUnitVectorY = firstUnitVectorY+secondUnitVectorY;
        double averageVectorLength = Math.sqrt(averageUnitVectorX*averageUnitVectorX + averageUnitVectorY*averageUnitVectorY);

        averageUnitVectorX /= averageVectorLength;
        averageUnitVectorY /= averageVectorLength;

        double sinHalfAngleBetweenUnitVectors = -(firstUnitVectorX * averageUnitVectorY - firstUnitVectorY * averageUnitVectorX);
        double signedDistanceToMove = -(pointToMove.signedRoundedTurnRadius - pointToMove.signedVirtualRoundedTurnRadius) / sinHalfAngleBetweenUnitVectors;

        pointToMove.x = pointToMove.x + averageUnitVectorX * signedDistanceToMove;
        pointToMove.y = pointToMove.y + averageUnitVectorY * signedDistanceToMove;
        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint straightNeighbour, GamePolyarcgonBuilderPoint arcedNeighbour, boolean straightNeighbourIsPrevArcedNeighbourIsNext) {
        double straightNeighbourX;
        double straightNeighbourY;
        double pointToMoveRoundedTurnToStraightNeighbourX;
        double pointToMoveRoundedTurnToStraightNeighbourY;
        double arcAngleChange;
        double arcCenterX;
        double arcCenterY;
        double arcSignedRadius;
        if (straightNeighbourIsPrevArcedNeighbourIsNext) {
            straightNeighbourX = straightNeighbour.roundedTurnEndX;
            straightNeighbourY = straightNeighbour.roundedTurnEndY;
            pointToMoveRoundedTurnToStraightNeighbourX = pointToMove.roundedTurnStartX;
            pointToMoveRoundedTurnToStraightNeighbourY = pointToMove.roundedTurnStartY;
            arcAngleChange = arcedNeighbour.arcAngleChange;
            double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(pointToMove.roundedTurnEndX, pointToMove.roundedTurnEndY, arcedNeighbour.roundedTurnStartX, arcedNeighbour.roundedTurnStartY, arcAngleChange);
            arcCenterX = arcCenterAndSignedRadius[0];
            arcCenterY = arcCenterAndSignedRadius[1];
            arcSignedRadius = arcCenterAndSignedRadius[2];
        } else {
            straightNeighbourX = straightNeighbour.roundedTurnStartX;
            straightNeighbourY = straightNeighbour.roundedTurnStartY;
            pointToMoveRoundedTurnToStraightNeighbourX = pointToMove.roundedTurnEndX;
            pointToMoveRoundedTurnToStraightNeighbourY = pointToMove.roundedTurnEndY;
            arcAngleChange = pointToMove.arcAngleChange;
            double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(arcedNeighbour.roundedTurnEndX, arcedNeighbour.roundedTurnEndY, pointToMove.roundedTurnStartX, pointToMove.roundedTurnStartY, arcAngleChange);
            arcCenterX = arcCenterAndSignedRadius[0];
            arcCenterY = arcCenterAndSignedRadius[1];
            arcSignedRadius = arcCenterAndSignedRadius[2];
        }
        double straightSegmentUnitVectorX = pointToMoveRoundedTurnToStraightNeighbourX - straightNeighbourX;
        double straightSegmentUnitVectorY = pointToMoveRoundedTurnToStraightNeighbourY - straightNeighbourY;
        double straightSegmentVectorLength = Math.sqrt(straightSegmentUnitVectorX * straightSegmentUnitVectorX + straightSegmentUnitVectorY * straightSegmentUnitVectorY);

        straightSegmentUnitVectorX /= straightSegmentVectorLength;
        straightSegmentUnitVectorY /= straightSegmentVectorLength;

        double orthogonalDistanceBetweenLineAndArcCenter = straightSegmentUnitVectorX * (arcCenterY - straightNeighbourY) - straightSegmentUnitVectorY * (arcCenterX - straightNeighbourX);
        double orthogonalDistanceBetweenArcCenterAndNewPoint = (straightNeighbourIsPrevArcedNeighbourIsNext ? 1 : -1) * pointToMove.signedRoundedTurnRadius - orthogonalDistanceBetweenLineAndArcCenter;
        double parallelDistanceBetweenArcCenterAndOldPoint = (pointToMove.x - arcCenterX) * straightSegmentUnitVectorX + (pointToMove.y - arcCenterY) * straightSegmentUnitVectorY;
        double parallelDistanceBetweenArcCenterAndNewPoint = Math.copySign(Math.sqrt((arcSignedRadius - pointToMove.signedRoundedTurnRadius)*(arcSignedRadius - pointToMove.signedRoundedTurnRadius) - orthogonalDistanceBetweenArcCenterAndNewPoint*orthogonalDistanceBetweenArcCenterAndNewPoint), parallelDistanceBetweenArcCenterAndOldPoint);

        pointToMove.x = arcCenterX + parallelDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorX - orthogonalDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorY;
        pointToMove.y = arcCenterY + parallelDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorY + orthogonalDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorX;

        double newArcAngleChange = Math.atan2(arcedNeighbour.y - arcCenterY, arcedNeighbour.x - arcCenterX) - Math.atan2(pointToMove.y - arcCenterY, pointToMove.x - arcCenterX);
        if (straightNeighbourIsPrevArcedNeighbourIsNext) {
            arcedNeighbour.arcAngleChange = (newArcAngleChange + Math.copySign(10*Math.PI, arcedNeighbour.arcAngleChange)) % (2*Math.PI);
        } else {
            pointToMove.arcAngleChange = (-newArcAngleChange + Math.copySign(10*Math.PI, pointToMove.arcAngleChange)) % (2*Math.PI);
        }

        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothArced(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double[] firstArcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(prevPoint.roundedTurnEndX, prevPoint.roundedTurnEndY, pointToMove.roundedTurnStartX, pointToMove.roundedTurnStartY, pointToMove.arcAngleChange);
        double firstArcCenterX = firstArcCenterAndSignedRadius[0];
        double firstArcCenterY = firstArcCenterAndSignedRadius[1];
        double firstSignedRadius = firstArcCenterAndSignedRadius[2];
        double[] secondArcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(pointToMove.roundedTurnEndX, pointToMove.roundedTurnEndY, nextPoint.roundedTurnStartX, nextPoint.roundedTurnStartY, nextPoint.arcAngleChange);
        double secondArcCenterX = secondArcCenterAndSignedRadius[0];
        double secondArcCenterY = secondArcCenterAndSignedRadius[1];
        double secondSignedRadius = secondArcCenterAndSignedRadius[2];

        double firstCenterToSecondCenterX = secondArcCenterX-firstArcCenterX;
        double firstCenterToSecondCenterY = secondArcCenterY-firstArcCenterY;

        double distanceBetweenCentersSq = firstCenterToSecondCenterX*firstCenterToSecondCenterX + firstCenterToSecondCenterY*firstCenterToSecondCenterY;
//        double distanceBetweenCenters = Math.sqrt(distanceBetweenCentersSq);
        double distanceFromFirstCenterToNewRoundedTurnSq = (firstSignedRadius-pointToMove.signedRoundedTurnRadius)*(firstSignedRadius-pointToMove.signedRoundedTurnRadius);
        double distanceFromSecondCenterToNewRoundedTurnSq = (secondSignedRadius-pointToMove.signedRoundedTurnRadius)*(secondSignedRadius-pointToMove.signedRoundedTurnRadius);

        double oldRoundedTurnCrossProduct = firstCenterToSecondCenterX * (pointToMove.y-firstArcCenterY) - firstCenterToSecondCenterY * (pointToMove.x-firstArcCenterX);

        double relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn = (distanceBetweenCentersSq + distanceFromFirstCenterToNewRoundedTurnSq - distanceFromSecondCenterToNewRoundedTurnSq) / (2 * distanceBetweenCentersSq);
        double relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn = Math.copySign(Math.sqrt(distanceFromFirstCenterToNewRoundedTurnSq/distanceBetweenCentersSq - relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn*relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn), oldRoundedTurnCrossProduct);

        pointToMove.x = firstArcCenterX + relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterX - relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterY;
        pointToMove.y = firstArcCenterY + relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterY + relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterX;

        pointToMove.arcAngleChange = (Math.atan2(pointToMove.y - firstArcCenterY, pointToMove.x - firstArcCenterX) - Math.atan2(prevPoint.y - firstArcCenterY, prevPoint.x - firstArcCenterX) + Math.copySign(10*Math.PI, pointToMove.arcAngleChange)) % (2*Math.PI);
        nextPoint.arcAngleChange = (Math.atan2(nextPoint.y - secondArcCenterY, nextPoint.x - secondArcCenterX) - Math.atan2(pointToMove.y - secondArcCenterY, pointToMove.x - secondArcCenterX) + Math.copySign(10*Math.PI, nextPoint.arcAngleChange)) % (2*Math.PI);

        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void processLineOrArcBetweenOneOrTwoRoundedTurns(GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter = Math.atan2(nextPoint.y - prevPoint.y, nextPoint.x - prevPoint.x);
        double distanceFromPrevRoundedTurnCenterToNextRoundedTurnCenter = Math.sqrt((nextPoint.x - prevPoint.x)*(nextPoint.x - prevPoint.x) + (nextPoint.y - prevPoint.y)*(nextPoint.y - prevPoint.y));
        // note the arc angle change between the two points is nextPoint.arcAngleChange, prevPoint.arcAngleChange is irrelevant
        double angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd = Math.asin(Math.cos(nextPoint.arcAngleChange/2.0)*(nextPoint.signedVirtualRoundedTurnRadius - prevPoint.signedVirtualRoundedTurnRadius)/distanceFromPrevRoundedTurnCenterToNextRoundedTurnCenter);
        if (prevPoint.signedVirtualRoundedTurnRadius != 0) {
            double angleAtPrevRoundedTurnCenterBetweenNextRoundedTurnCenterAndPrevRoundedTurnEnd = -nextPoint.arcAngleChange / 2.0 - Math.PI / 2.0 - angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd;
            prevPoint.roundedTurnEndAngle = angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter + angleAtPrevRoundedTurnCenterBetweenNextRoundedTurnCenterAndPrevRoundedTurnEnd;

            prevPoint.roundedTurnEndX = prevPoint.x + prevPoint.signedVirtualRoundedTurnRadius * Math.cos(prevPoint.roundedTurnEndAngle);
            prevPoint.roundedTurnEndY = prevPoint.y + prevPoint.signedVirtualRoundedTurnRadius * Math.sin(prevPoint.roundedTurnEndAngle);
        }
        if (nextPoint.signedVirtualRoundedTurnRadius != 0) {
            double angleAtNextRoundedTurnCenterBetweenPrevRoundedTurnCenterAndNextRoundedTurnStart = nextPoint.arcAngleChange / 2.0 + Math.PI / 2.0 - angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd;
            nextPoint.roundedTurnStartAngle = (angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter + Math.PI) + angleAtNextRoundedTurnCenterBetweenPrevRoundedTurnCenterAndNextRoundedTurnStart;

            nextPoint.roundedTurnStartX = nextPoint.x + nextPoint.signedVirtualRoundedTurnRadius * Math.cos(nextPoint.roundedTurnStartAngle);
            nextPoint.roundedTurnStartY = nextPoint.y + nextPoint.signedVirtualRoundedTurnRadius * Math.sin(nextPoint.roundedTurnStartAngle);
        }
    }

    public GamePolyarcgonBuilder addCircleContour(double x, double y, double radius, boolean isClockwise) {
        newContour();
        arcTo(x-radius, y, isClockwise ? Math.PI : -Math.PI);
        arcTo(x+radius, y, isClockwise ? Math.PI : -Math.PI);
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder rotate(double clockwiseRotationAmount, double rotationCenterX, double rotationCenterY) {
        double cosClockwiseRotationAmount = Math.cos(clockwiseRotationAmount);
        double sinClockwiseRotationAmount = Math.sin(clockwiseRotationAmount);
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            for (GamePolyarcgonBuilderPoint point : contour) {
                double newX = rotationCenterX + (point.x - rotationCenterX) * cosClockwiseRotationAmount - (point.y - rotationCenterY) * sinClockwiseRotationAmount;
                double newY = rotationCenterY + (point.y - rotationCenterY) * cosClockwiseRotationAmount + (point.x - rotationCenterX) * sinClockwiseRotationAmount;
                point.x = point.roundedTurnStartX = point.roundedTurnEndX = newX;
                point.y = point.roundedTurnStartY = point.roundedTurnEndY = newY;
            }
        }
        double rotationCenterXOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : rotationCenterX;
        double rotationCenterYOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : rotationCenterY;
        double newCenterOfMassX = rotationCenterXOr0 + (centerOfMassX - rotationCenterXOr0) * cosClockwiseRotationAmount - (centerOfMassY - rotationCenterYOr0) * sinClockwiseRotationAmount;
        double newCenterOfMassY = rotationCenterYOr0 + (centerOfMassY - rotationCenterYOr0) * cosClockwiseRotationAmount + (centerOfMassX - rotationCenterXOr0) * sinClockwiseRotationAmount;
        centerOfMassX = newCenterOfMassX;
        centerOfMassY = newCenterOfMassY;
        return this;
    }
    public GamePolyarcgonBuilder mirror(double mirrorLineAngle, double mirrorLinePosition) {
        double cosMirrorLineAngle = Math.cos(mirrorLineAngle);
        double sinMirrorLineAngle = Math.sin(mirrorLineAngle);
        double cos2MirrorLineAngle = Math.cos(2*mirrorLineAngle);
        double sin2MirrorLineAngle = Math.sin(2*mirrorLineAngle);
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            for (GamePolyarcgonBuilderPoint point : contour) {
                double newX = point.x * cos2MirrorLineAngle + point.y * sin2MirrorLineAngle - 2 * mirrorLinePosition * sinMirrorLineAngle;
                double newY = point.x * sin2MirrorLineAngle - point.y * cos2MirrorLineAngle + 2 * mirrorLinePosition * cosMirrorLineAngle;
                point.x = point.roundedTurnStartX = point.roundedTurnEndX = newX;
                point.y = point.roundedTurnStartY = point.roundedTurnEndY = newY;
            }
        }
        double mirrorLinePositionOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : mirrorLinePosition;
        double newCenterOfMassX = centerOfMassX * cos2MirrorLineAngle + centerOfMassY * sin2MirrorLineAngle - 2 * mirrorLinePositionOr0 * sinMirrorLineAngle;
        double newCenterOfMassY = centerOfMassX * sin2MirrorLineAngle - centerOfMassY * cos2MirrorLineAngle + 2 * mirrorLinePositionOr0 * cosMirrorLineAngle;
        centerOfMassX = newCenterOfMassX;
        centerOfMassY = newCenterOfMassY;
        invert(true);
        return this;
    }
    public GamePolyarcgonBuilder invert(boolean justReverse) {
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            GamePolyarcgonBuilderPoint pointToSwap;
            int size = contour.size();
            for (int i=0; i<size/2; i++) {
                pointToSwap = contour.get(i);
                contour.set(i, contour.get(size-1-i));
                contour.set(size-1-i, pointToSwap);
            }

            double firstX = contour.get(0).x;
            double firstY = contour.get(0).y;
            for (int i=0; i<size; i++) {
                GamePolyarcgonBuilderPoint point = contour.get(i);
                if (!justReverse) {
                    point.arcAngleChange *= -1;
                }
                if (i+1 < size) {
                    GamePolyarcgonBuilderPoint nextPoint = contour.get(i+1);
                    point.x = point.roundedTurnStartX = point.roundedTurnEndX = nextPoint.x;
                    point.y = point.roundedTurnStartY = point.roundedTurnEndY = nextPoint.y;
                } else {
                    point.x = point.roundedTurnStartX = point.roundedTurnEndX = firstX;
                    point.y = point.roundedTurnStartY = point.roundedTurnEndY = firstY;
                }
            }
        }
        return this;
    }
//    static class ContourlessSegment {
//        GamePolyarcgonBuilderPoint thisPoint;
//        ContourlessSegment nextPoint;
//        TreeMap<Double, Boolean> intersections; //key is fraction along the curve, value is whether it's a start or an end
//
//        public ContourlessSegment(GamePolyarcgonBuilderPoint thisPoint, ContourlessSegment nextPoint) {
//            this.thisPoint = thisPoint;
//            this.nextPoint = nextPoint;
//        }
//    }
    public GamePolyarcgonBuilder unionLastContour() {
        if (contoursOfPoints.get(contoursOfPoints.size()-1).isEmpty()) {
            contoursOfPoints.remove(contoursOfPoints.size()-1);
        }
        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> lastContour = new ArrayList<>();
        lastContour.add(contoursOfPoints.remove(contoursOfPoints.size()-1));
        contoursOfPoints = unionOrIntersection(contoursOfPoints, lastContour, true);
        newContour();
        return this;
    }
    public GamePolyarcgonBuilder intersectionLastContour() {
        if (contoursOfPoints.get(contoursOfPoints.size()-1).isEmpty()) {
            contoursOfPoints.remove(contoursOfPoints.size()-1);
        }
        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> lastContour = new ArrayList<>();
        lastContour.add(contoursOfPoints.remove(contoursOfPoints.size()-1));
        contoursOfPoints = unionOrIntersection(contoursOfPoints, lastContour, false);
        newContour();
        return this;
    }

    private static ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> unionOrIntersection(ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> shape1, ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> shape2, boolean isUnion) {
        /*
        Algorithm for union and intersections:
        Don't create a turn each crossing between two edges, which can be inaccurate due to
        an edge crossing the other shape exactly at one of its corners (or a corner of each shape being at the same point),
        causing the edge to intersect two edges in the other shape (or no edges in the other shape).

        Instead break each edge into pieces inside the other shape and pieces outside. Only keep the pieces outside (or vice versa depending on union/intersection)
        and then try to create a path which goes from the end of one piece to the start of another piece as close as possible.

        Though this might still have pieces "doubly inside" or not inside... I guess having pieces "doubly inside" is okay,
        so maybe make the collision algorithm more greedily seek collisions?

        Each time a piece's winding number is greater than 1 it is reduced to 1, vice versa for -1. This way +1 +1 -1 adds to 0 instead of +1.

         */

//        ArrayList<ContourlessSegment> shape1Segments = convertToContourlessSegments(shape1);
//        ArrayList<ContourlessSegment> shape2Segments = convertToContourlessSegments(shape2);
//        ArrayList<ContourlessSegment> unionSegments = new ArrayList<>();
//        for (ContourlessSegment shape1Segment : shape1Segments) {
//            for (ContourlessSegment shape2Segment : shape2Segments) {
//            }
//        }
//        return convertToContoursOfPoints(unionSegments);

        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> result = new ArrayList<>();

        GamePolyarcgonBuilder builder1 = new GamePolyarcgonBuilder();
        builder1.contoursOfPoints = shape1;
        GamePolyarcgonBuilder builder2 = new GamePolyarcgonBuilder();
        builder2.contoursOfPoints = shape2;
        GamePolyarcgon newShape1 = builder1.buildAndReset();
        GamePolyarcgon newShape2 = builder2.buildAndReset();
        IntersectionsCalculator unionCalculator = new IntersectionsCalculator(isUnion, newShape1, newShape2);
        GamePolyarcgon.collision(newShape1, newShape2, unionCalculator);
        GamePolyarcgon.PolyarcgonPointCache[] shape1Points = newShape1.pointsCache.get();
        GamePolyarcgon.PolyarcgonPointCache[] shape2Points = newShape2.pointsCache.get();
        GamePolyarcgon.PolyarcgonPointCache[][] allPoints = new GamePolyarcgon.PolyarcgonPointCache[][]{shape1Points, shape2Points};

        // first we fix the intersection list in each point so it's made up of
        // start end pairs
        // also delete start/end pairs too close together (but not end/start pairs)
        for (GamePolyarcgon.PolyarcgonPointCache[] points : allPoints) {
            GamePolyarcgon.PolyarcgonPointCache lastPoint = points[points.length-1];
            for (GamePolyarcgon.PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                    IntersectionsCalculator.UnionIntersectionData data = unionCalculator.intersections.get(nextPoint);
                    assert data != null;
                    //greedy assume each segment is included because can exclude later if it doesn't form a loop
                    double intersectionFraction;
                    double lastIntersectionFraction = 0;
                    boolean lastIntersectionWasStart = false; // has to be false by default to avoid triggering adding an end if the loop is empty
                    double lastLastIntersectionFraction = 0;
                    // don't iterate entry set because modifying list as we go
                    for (Map.Entry<Double, Intersection> intersectionEntry = data.intersectionList.firstEntry(); intersectionEntry != null; intersectionEntry = data.intersectionList.higherEntry(intersectionFraction)) {
                        intersectionFraction = intersectionEntry.getKey();
                        Intersection intersection = intersectionEntry.getValue();
                        boolean intersectionWasStart = intersection.windingNumber < 0;
                        if (intersectionWasStart) {
                            //i.e. this intersection is a start
                            intersection.windingNumber = -1;
                            if (lastIntersectionWasStart) {
                                data.intersectionList.remove(intersectionFraction);
//                                intersectionWasStart = lastIntersectionWasStart;
                                intersectionFraction = lastIntersectionFraction;
                            }
                        } else {
                            //i.e. this intersection is an end
                            intersection.windingNumber = 1;
                            if (lastIntersectionWasStart) {
                                // check if the last intersection was too short
                                if (intersectionFraction - lastIntersectionFraction <= MAX_ROUNDING_ERROR) {
                                    data.intersectionList.remove(intersectionFraction);
                                    data.intersectionList.remove(lastIntersectionFraction);
                                    intersectionFraction = lastLastIntersectionFraction;
                                }
                            } else {
                                if (lastIntersectionFraction == 0) {
                                    // add start point at very start if needed
                                    data.intersectionList.put(0.0, new Intersection(lastPoint.getX(), lastPoint.getY(), -1));
                                } else {
                                    data.intersectionList.remove(lastIntersectionFraction); //remove the last end because there's an end after
                                    lastIntersectionFraction = lastLastIntersectionFraction;
                                }
                            }
                        }

                        lastLastIntersectionFraction = lastIntersectionFraction;
                        lastIntersectionWasStart = intersectionWasStart;
                        lastIntersectionFraction = intersectionFraction;
                    }
                    // add ending at very end if needed
                    if (lastIntersectionWasStart) {
                        if (lastIntersectionFraction >= 1 - MAX_ROUNDING_ERROR) {
                            data.intersectionList.remove(lastIntersectionFraction);
                        } else {
                            data.intersectionList.put(1.0, new Intersection(nextPoint.getX(), nextPoint.getY(), 1));
                        }
                    }
                }
                lastPoint = nextPoint;
            }
        }

        // next, we iterate over every pair of start and end between every pair of points, trying to form a contour loop out of it,
        // until there are none left
        for (GamePolyarcgon.PolyarcgonPointCache[] points : allPoints) {
            for (GamePolyarcgon.PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                    IntersectionsCalculator.UnionIntersectionData data = unionCalculator.intersections.get(nextPoint);
                    assert data != null;

                    // this loop keeps trying to build a new contour by using up intersections,
                    // starting with intersections in this point,
                    // until this point has no more intersections left
                    while (!data.intersectionList.isEmpty()) {

                        ArrayList<GamePolyarcgon.PolyarcgonPointCache> newContourPoints = new ArrayList<>();
                        ArrayList<Map.Entry<Double, Intersection>> newContourStarts = new ArrayList<>();
                        ArrayList<Map.Entry<Double, Intersection>> newContourEnds = new ArrayList<>();


                        GamePolyarcgon.PolyarcgonPointCache newContourNextPoint = nextPoint;
                        Map.Entry<Double, Intersection> newContourNextStart = data.intersectionList.firstEntry();
                        assert newContourNextStart != null;
                        Map.Entry<Double, Intersection> newContourNextEnd = data.intersectionList.higherEntry(newContourNextStart.getKey());

                        // this loop keeps trying to add a segment to the new contour, until the new contour forms a loop
                        // or fails to do so (in which case we delete the last segment and start over)
                        int loopsAt = -1;
                        while (true) {
                            assert newContourNextEnd != null;

                            newContourPoints.add(newContourNextPoint);
                            newContourStarts.add(newContourNextStart);
                            newContourEnds.add(newContourNextEnd);

                            Intersection endIntersection = newContourNextEnd.getValue();

                            double closestDistanceSq = Double.MAX_VALUE;

                            // search all points again for what segment to add to the new contour
                            for (GamePolyarcgon.PolyarcgonPointCache[] otherPoints : allPoints) {
                                for (GamePolyarcgon.PolyarcgonPointCache otherNextPoint : otherPoints) {
                                    if (!otherNextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                                        IntersectionsCalculator.UnionIntersectionData otherData = unionCalculator.intersections.get(otherNextPoint);
                                        assert otherData != null;
                                        for (Map.Entry<Double, Intersection> otherIntersectionEntry : otherData.intersectionList.entrySet()) {
                                            Intersection otherIntersection = otherIntersectionEntry.getValue();
                                            if (otherIntersection.windingNumber < 0) { // i.e. a start not an end
                                                double distanceSq = (otherIntersection.x-endIntersection.x)*(otherIntersection.x-endIntersection.x) + (otherIntersection.y-endIntersection.y)*(otherIntersection.y-endIntersection.y);
                                                if (distanceSq < 1000*1000*MAX_ROUNDING_ERROR*MAX_ROUNDING_ERROR && distanceSq < closestDistanceSq) {
                                                    closestDistanceSq = distanceSq;
                                                    newContourNextPoint = otherNextPoint;
                                                    newContourNextStart = otherIntersectionEntry;
                                                    newContourNextEnd = otherData.intersectionList.higherEntry(otherIntersectionEntry.getKey());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (closestDistanceSq == Double.MAX_VALUE) {
                                break;
                            }

                            // check for a finished loop, not necessarily connected to the first point in the new contour
                            for (int i=0; i<newContourPoints.size(); i++) {
                                if (newContourPoints.get(i).equals(newContourNextPoint) && newContourStarts.get(i).equals(newContourNextStart)) {
                                    // we have a loop, so create a new contour
                                    loopsAt = i;
                                    break;
                                }
                            }

                            if (loopsAt != -1) {
                                break;
                            }
                        }
                        ArrayList<GamePolyarcgonBuilderPoint> newContour;
                        int removeFrom;
                        if (loopsAt != -1) {
                            // we have a loop, so create a new contour and remove the segments from the set
                            newContour = new ArrayList<>();
                            result.add(newContour);
                            removeFrom = loopsAt;
                        } else {
                            // this never loops and just ends, so no new contour but still remove the last segment from the set
                            newContour = null;
                            removeFrom = newContourPoints.size()-1;
                        }

                        for (int i=removeFrom; i<newContourPoints.size(); i++) {
                            double start = newContourStarts.get(i).getKey();
                            Map.Entry<Double, Intersection> newEndEntry = newContourEnds.get(i);
                            double end = newEndEntry.getKey();

                            GamePolyarcgon.PolyarcgonPointCache newContourPoint = newContourPoints.get(i);
                            IntersectionsCalculator.UnionIntersectionData currentData = unionCalculator.intersections.get(newContourPoint);
                            assert currentData != null;

                            currentData.intersectionList.remove(start);
                            currentData.intersectionList.remove(end);

                            if (newContour != null) {
                                Intersection intersection = newEndEntry.getValue();
                                newContour.add(new GamePolyarcgonBuilderPoint(intersection.x, intersection.y, (end - start) * newContourPoint.getNonCachePoint().arcAngleChange));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
    static class IntersectionsCalculator implements OverlapHandler {
        final boolean isUnion;
        final int shape1DefaultWindingNumber;
        final int shape2DefaultWindingNumber;
        final HashMap<GamePolyarcgon.PolyarcgonPointCache, UnionIntersectionData> intersections;

        public IntersectionsCalculator(boolean isUnion, GamePolyarcgon shape1, GamePolyarcgon shape2) {
            this.isUnion = isUnion;
            shape1DefaultWindingNumber = ((shape2.getMass() > 0) == !isUnion) ? 0 : 1;
            shape2DefaultWindingNumber = ((shape1.getMass() > 0) == !isUnion) ? 0 : 1;
            intersections = new HashMap<>();

            //if union:
                //If both shapes positive, only keep parts of each shape outside the other shape. Each segment starts as 1, is reduced to 0 when circled clockwise
                //If second shape negative, only keep parts of each shape "outside" the other shape.
                    //Each segment in first shape starts as 0, is increased to 1 when circled counterclockwise
                    //Each segment in second shape starts as 1, is decreased to 0 when circled clockwise
                //If both shapes negative, only keep parts of each shape "outside" the other shape. Each segment starts as 0, is increased to 0 when circled counterclockwise
            //if intersection:
                //If both shapes positive, only keep parts of each shape inside the other shape. Each segment starts as 0, is increased to 1 when circled clockwise
                //If second shape negative, only keep parts of each shape "inside" the other shape.
                    //Each segment in first shape starts as 1, is decreased to 0 when circled counterclockwise
                    //Each segment in second shape starts as 0, is increased to 1 when circled clockwise
                //If both shapes negative, only keep parts of each shape "inside" the other shape. Each segment starts as 1, is decreased to 0 when circled counterclockwise
            for (GamePolyarcgon.PolyarcgonPointCache point : shape1.pointsCache.get()) {
                UnionIntersectionData data = new UnionIntersectionData();
                if (shape1DefaultWindingNumber != 0) {
                    data.intersectionList.put(1.0, new Intersection(point.getX(), point.getY(), shape1DefaultWindingNumber));
                }
                intersections.put(point, data);
            }
            for (GamePolyarcgon.PolyarcgonPointCache point : shape2.pointsCache.get()) {
                UnionIntersectionData data = new UnionIntersectionData();
                if (shape2DefaultWindingNumber != 0) {
                    data.intersectionList.put(1.0, new Intersection(point.getX(), point.getY(), shape2DefaultWindingNumber));
                }
                intersections.put(point, data);
            }
        }

        static class UnionIntersectionData {
            TreeMap<Double, Intersection> intersectionList = new TreeMap<>(); //key is contour, value's key is fraction along the curve
        }

        @Override
        public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {

            final double nextX = nextPoint.getX();
            final double nextY = nextPoint.getY();
            final double distance = Math.sqrt((endX-startX)*(endX-startX) + (endY-startY)*(endY-startY));
            final double fullDistance = Math.sqrt((nextX-startX)*(nextX-startX) + (nextY-startY)*(nextY-startY));

            addLineSegmentOrArc(endX, endY, windingFactor, nextPoint, lineSegmentIsFirstShape, distance/fullDistance);

        }

        @Override
        public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {

            addLineSegmentOrArc(arcEndX, arcEndY, windingFactor, nextPoint, arcIsFirstShape, arcAngleChange/nextPoint.getNonCachePoint().arcAngleChange);

        }

        private void addLineSegmentOrArc(double endX, double endY, int windingFactor, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean lineSegmentIsFirstShape, double fraction) {

                /*
                Assuming we are iterating over the edges in order, the only reason we should have containing intersections at any time
                is if it's a glitch where a corner of one shape intersects with a segment of another shape twice
                (the segment before the corner and after the corner both intersects with the other segment)

                 */

            UnionIntersectionData data = intersections.get(nextPoint);
            assert data != null;

            if (isUnion) {
                windingFactor = -windingFactor;
            }

            //need to ensure it's between -1 and 1
            //or no even stricter, it needs to be no two adds or subtracts in a row. So it can go 0 1 0 1 0 1? Or no
            //it can have two adds or subtracts in a row actually.
            //Ahh maybe give up on policing the partially complete list
            //instead you have to just allow anything in the partially complete list, but after it is complete you
            //remove redundant intersections

            //but then actually the intersections on the ray need to be ordered rather than all being seen at the same point at the end
            //and the closest one should have the final say

            //or maybe intersections on the ray of a line segment can be like that,
            //while for the intersections on the ray of an arc, you avoid two in a row in the same direction
            //if the start and end angles aren't far enough

            //or maybe just ignore errors for intersecting a ray too many times. It'll lead to extra segments
            //but maybe we can just delete all segments which don't form a loop.
            Intersection currentIntersection = data.intersectionList.get(fraction);
            if (currentIntersection == null) {
                data.intersectionList.put(fraction, new Intersection(endX, endY, windingFactor));
            } else {
                currentIntersection.windingNumber += windingFactor;
                if (currentIntersection.windingNumber == 0) {
                    data.intersectionList.remove(fraction);
                }
            }
        }
    }

    public GamePolyarcgon build() {
        return new GamePolyarcgon(this);
    }

    /**
     * Builds the "polyarcgon"
     * Also deletes everything in this builder and allows it to be used again.
     * @return the polyarcgon
     */
    public GamePolyarcgon buildAndReset() {
        GamePolyarcgon gamePolyarcgon = new GamePolyarcgon(this);
        reset();
        return gamePolyarcgon;
    }

    /**
     * Deletes everything in this builder and allows it to be used again
     * @return this
     */
    public GamePolyarcgonBuilder reset() {
        if (contoursOfPoints == null) {
            contoursOfPoints = new ArrayList<>();
        } else {
            contoursOfPoints.clear();
        }
        contoursOfPoints.add(new ArrayList<>());
        centerOfMassX = 0;
        centerOfMassY = 0;
        centerOfMassIsRelativeToDefaultCenterOfMass = true;
        density = 1;
        additionalAttributes = null;
        return this;
    }

    /**
     * Unimportant function that removes redundant points.
     */
    private void removeRedundantPoints() {
        // Redundant points may happen for circles, which technically have 0 points,
        // but need 2 points in order to define their position and size.
        // If a circle is intersected with another shape and gains more
        // points, those points can be removed.
        // Or if a circle is actually not a full circle, but acts as
        // a circle initially due to the start point and end point being
        // the same point (with a signedVirtualRoundedTurnRadius that
        // will later move them apart).
        //TODO not done
    }
}

/**
 * Intersection between two lines or arcs
 */
class Intersection extends DoublePoint {
    public int windingNumber;

    public Intersection(double x, double y, int windingNumber) {
        super(x, y);
        this.windingNumber = windingNumber;
    }
}

/**
 * A generalization of a polygon, which can have arcs instead of just straight edges.
 * It's more limited than Android's Path class, but has methods for collision physics,
 * center of mass, etc.
 */
class GamePolyarcgon implements GameShape {
    double x;
    double y;
    double boundingRadius;
    double rotationRadians;

    final Object additionalAttributes;
    private double density;
    private double mass;
    private double momentOfInertia;

    private final PolyarcgonPoint[] templatePoints;
    private final Path templatePathForDrawing;
    final Cache<PolyarcgonPointCache[]> pointsCache;
    private final Cache<Path> pathForDrawingCache;

    GamePolyarcgon(GamePolyarcgonBuilder gamePolyarcgonBuilder) {

        this.additionalAttributes = gamePolyarcgonBuilder.additionalAttributes;
        density = gamePolyarcgonBuilder.density;
        templatePoints = gamePolyarcgonBuilder.generatePoints().toArray(new PolyarcgonPoint[0]);

        PolyarcgonPointCache[] pointsCachePoints = new PolyarcgonPointCache[templatePoints.length];
        pointsCache = new Cache<>(pointsCachePoints, ((newX, newY, newRotation) -> {
            double cosRotation = Math.cos(rotationRadians);
            double sinRotation = Math.sin(rotationRadians);
            for (PolyarcgonPointCache pointCache : pointsCachePoints) {
                pointCache.updatePositionCache(x, y, cosRotation, sinRotation);
            }
            PolyarcgonPointCache lastPointCache = pointsCachePoints[pointsCachePoints.length - 1];
            for (PolyarcgonPointCache pointCache : pointsCachePoints) {
                pointCache.updateLineOrArcCache(lastPointCache);
                lastPointCache = pointCache;
            }
        }));
        for (int i=0; i<templatePoints.length; i++) {
            if (templatePoints[i].isAlmostStraight()) {
                pointsCachePoints[i] = new PolyarcgonStraightPointCache(templatePoints[i]);
            } else {
                pointsCachePoints[i] = new PolyarcgonArcedPointCache(templatePoints[i]);
            }
        }

        initiateXYMassMomentOfInertiaBoundingRadiusAndTemplatePoints(gamePolyarcgonBuilder.centerOfMassX, gamePolyarcgonBuilder.centerOfMassY, gamePolyarcgonBuilder.centerOfMassIsRelativeToDefaultCenterOfMass); //needs to be after pointsCache exists

        templatePathForDrawing = initiatePath(); //needs to be after pointsCache exists
        Path pathForDrawingCachePath = new Path();
        pathForDrawingCache = new Cache<>(pathForDrawingCachePath, ((newX, newY, newRotation) -> {
            Matrix matrix = new Matrix();
            matrix.setTranslate((float) newX, (float) newY);
            matrix.preRotate((float) (newRotation*180.0/Math.PI));
            pathForDrawingCachePath.set(templatePathForDrawing);
            pathForDrawingCachePath.transform(matrix);
        }));
    }

    private void initiateXYMassMomentOfInertiaBoundingRadiusAndTemplatePoints(double centerOfMassX, double centerOfMassY, boolean centerOfMassIsRelativeToDefaultCenterOfMass) {
        OverlapAreaIntegralCalculator selfOverlap = new OverlapAreaIntegralCalculator(this, this);

        PolyarcgonPointCache[] gotPointsCache = pointsCache.get();

        PolyarcgonPointCache lastPoint = gotPointsCache[gotPointsCache.length - 1];
        for (PolyarcgonPointCache nextPoint : gotPointsCache) {
            if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                if (nextPoint instanceof PolyarcgonStraightPointCache) {
                    selfOverlap.addLineSegmentToOverlap(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY(), 1, false, nextPoint, true);
                } else if (nextPoint instanceof PolyarcgonArcedPointCache) {
                    PolyarcgonArcedPointCache nextPointArc = (PolyarcgonArcedPointCache) nextPoint;
                    selfOverlap.addArcToOverlap(nextPointArc.radiusOfCurvature, nextPointArc.arcCenterX, nextPointArc.arcCenterY, lastPoint.getX(), lastPoint.getY(), nextPointArc.x, nextPointArc.y, nextPointArc.nonCachePoint.arcAngleChange, 1, false, nextPoint, true);
                }
            }
            lastPoint = nextPoint;
        }

        double defaultCenterOfMassX = selfOverlap.overlapXAreaIntegral / selfOverlap.overlapArea;
        double defaultCenterOfMassY = selfOverlap.overlapYAreaIntegral / selfOverlap.overlapArea;

        if (centerOfMassIsRelativeToDefaultCenterOfMass) {
            x = defaultCenterOfMassX + centerOfMassX;
            y = defaultCenterOfMassY + centerOfMassY;
        } else {
            x = centerOfMassX;
            y = centerOfMassY;
        }

        mass = density * selfOverlap.overlapArea;

        // don't worry about whether x and y equal default center of mass when calculating moment of inertia
        momentOfInertia = density * (selfOverlap.overlapXSqPlusYSqAreaIntegral - (defaultCenterOfMassX*defaultCenterOfMassX + defaultCenterOfMassY*defaultCenterOfMassY)*selfOverlap.overlapArea);

        boundingRadius = 0;
        for (PolyarcgonPointCache point : gotPointsCache) {
            double distance = 0;
            if (point instanceof PolyarcgonStraightPointCache) {
                PolyarcgonStraightPointCache straightPoint = (PolyarcgonStraightPointCache) point;
                straightPoint.nonCachePoint.x = straightPoint.x-x;
                straightPoint.nonCachePoint.y = straightPoint.y-y;

                distance = Math.sqrt(straightPoint.nonCachePoint.x*straightPoint.nonCachePoint.x + straightPoint.nonCachePoint.y*straightPoint.nonCachePoint.y);
            } else if (point instanceof PolyarcgonArcedPointCache) {
                PolyarcgonArcedPointCache arcedPoint = (PolyarcgonArcedPointCache) point;
                arcedPoint.nonCachePoint.x = arcedPoint.x-x;
                arcedPoint.nonCachePoint.y = arcedPoint.y-y;
                if (arcedPoint.nonCachePoint.isMoveToWithoutLineEtc) {
                    distance = Math.sqrt(arcedPoint.nonCachePoint.x*arcedPoint.nonCachePoint.x + arcedPoint.nonCachePoint.y*arcedPoint.nonCachePoint.y);
                } else {
                    double relArcCenterX = arcedPoint.arcCenterX-x;
                    double relArcCenterY = arcedPoint.arcCenterY-y;
                    if (relArcCenterX == 0 && relArcCenterY == 0) { //i.e. this is a circle
                        distance = arcedPoint.radiusOfCurvature;
                    } else {
                        double potentialDistance = arcedPoint.radiusOfCurvature + Math.sqrt(relArcCenterX * relArcCenterX + relArcCenterY * relArcCenterY);
                        if (potentialDistance > boundingRadius) {
                            double potentialDistanceAngle = Math.atan2(arcedPoint.arcCenterY - y, arcedPoint.arcCenterX - x);
                            boolean arcPassesPotentialDistanceAngle = true;
                            if (arcedPoint.nonCachePoint.arcAngleChange > 0) {
                                if ((potentialDistanceAngle - arcedPoint.startAngle + 10 * Math.PI) % (2 * Math.PI) >= arcedPoint.nonCachePoint.arcAngleChange) {
                                    arcPassesPotentialDistanceAngle = false;
                                }
                            } else {
                                if ((potentialDistanceAngle - arcedPoint.startAngle - 10 * Math.PI) % (2 * Math.PI) <= arcedPoint.nonCachePoint.arcAngleChange) {
                                    arcPassesPotentialDistanceAngle = false;
                                }
                            }
                            if (arcPassesPotentialDistanceAngle) {
                                boundingRadius = potentialDistance;
                            } else {
                                distance = Math.sqrt(arcedPoint.nonCachePoint.x*arcedPoint.nonCachePoint.x + arcedPoint.nonCachePoint.y*arcedPoint.nonCachePoint.y);
                            }
                        }
                    }
                }
            }
            if (distance > boundingRadius) {
                boundingRadius = distance;
            }
        }
    }

    class Cache <T> {
        private double xWhenLastUpdatedCache = Double.NaN;
        private double yWhenLastUpdatedCache = Double.NaN;
        private double rotationWhenLastUpdatedCache = Double.NaN;
        private final T cache;
        private final CacheUpdateFunction cacheUpdateFunction;

        Cache(T cache, CacheUpdateFunction cacheUpdateFunction) {
            this.cache = cache;
            this.cacheUpdateFunction = cacheUpdateFunction;
        }
        public T get() {
            if (xWhenLastUpdatedCache != x || yWhenLastUpdatedCache != y || rotationWhenLastUpdatedCache != rotationRadians) {
                cacheUpdateFunction.updateCache(x, y, rotationRadians);
                xWhenLastUpdatedCache = x;
                yWhenLastUpdatedCache = y;
                rotationWhenLastUpdatedCache = rotationRadians;
            }
            return cache;
        }
    }
    interface CacheUpdateFunction {
        void updateCache(double newX, double newY, double newRotation);
    }

    public PolyarcgonPoint[] getTemplatePoints() {
        return templatePoints;
    }

    public void collision(GameShape otherShape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapHandler overlapHandler) {
        if (otherShape instanceof GamePolyarcgon) {
            if (thisIsFirstShape) {
                collision(this, (GamePolyarcgon) otherShape, overlapHandler);
            } else {
                collision((GamePolyarcgon) otherShape, this, overlapHandler);
            }
        } else {
            otherShape.collision(this, isOtherShapeMovable, isThisMovable, !thisIsFirstShape, overlapHandler);
        }
    }
    public Path getPathForDrawing() {
        return pathForDrawingCache.get();
    }
    @Override
    public void draw(Canvas canvas) {
        Path path = pathForDrawingCache.get();
        path.setFillType(Path.FillType.EVEN_ODD);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }
    private Path initiatePath() {
        Path path = new Path();
        PolyarcgonPointCache[] gotPointsCache = pointsCache.get();
        for (int i=0; i <= gotPointsCache.length; i++) {
            PolyarcgonPointCache pointI = gotPointsCache[i%gotPointsCache.length];
            if (i==0 || pointI.getNonCachePoint().isMoveToWithoutLineEtc) {
                if (i != gotPointsCache.length) {
                    path.moveTo((float) pointI.getNonCachePoint().x, (float) pointI.getNonCachePoint().y);
                }
            } else {
                if (pointI instanceof PolyarcgonStraightPointCache) {
                    PolyarcgonPointCache pointI1 = gotPointsCache[(i+1)%gotPointsCache.length];
                    if (!(pointI1 instanceof PolyarcgonArcedPointCache && i != gotPointsCache.length)) {
                        path.lineTo((float) pointI.getNonCachePoint().x, (float) pointI.getNonCachePoint().y);
                    }
                } else if (pointI instanceof PolyarcgonArcedPointCache) {
                    PolyarcgonArcedPointCache pointIArc = (PolyarcgonArcedPointCache) pointI;
                    path.arcTo((float) (pointIArc.arcCenterX-x-pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterY-y-pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterX-x+pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterY-y+pointIArc.radiusOfCurvature), (float) (pointIArc.startAngle*180.0/Math.PI), (float) (pointIArc.nonCachePoint.arcAngleChange*180.0/Math.PI), false);
                }
            }
        }
//        path.close(); not necessary
        return path;
    }

    @Override
    public void receiveForce(ForceAndTorque collision) {
//        if (this == collision.shape1) {
            //because inertia-less, don't have velocity or angular speed, just add acceleration to position instead of velocity and likewise for angular acceleration

            x += collision.forceActingOnShapeX / mass;
            y += collision.forceActingOnShapeY / mass;

            rotationRadians += collision.torqueActingOnShape / momentOfInertia;
//        }
    }
//    @Override
//    public void receiveForce(double forceX, double forceY, double torque) {
//        x += forceX / mass;
//        y += forceY / mass;
//        rotationRadians += torque / momentOfInertia;
//    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public double getArea() {
        return mass/density;
    }

    @Override
    public double getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getMomentOfInertia() {
        return momentOfInertia;
    }

    @Override
    public void setPos(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setRotation(double rotationRadians) {
        this.rotationRadians = rotationRadians;
    }

    public static void collision(GamePolyarcgon firstShape, GamePolyarcgon otherShape, OverlapHandler handler) {
        double distSq = (firstShape.x-otherShape.x)*(firstShape.x-otherShape.x) + (firstShape.y-otherShape.y)*(firstShape.y-otherShape.y);
        if (Math.sqrt(distSq) < firstShape.boundingRadius+otherShape.boundingRadius) {
            //maybe at some point add ability to check if bounding radius of one shape collides with another shape at all (mostly for a wall)?
            //though won't help much

            /*
            We use the shoelace formula (see https://en.wikipedia.org/wiki/Shoelace_formula)
            To calculate the area and area integrals
             */

            PolyarcgonPointCache[] points = firstShape.pointsCache.get();
            PolyarcgonPointCache[] otherPoints = otherShape.pointsCache.get();

            PolyarcgonPointCache lastPoint = points[points.length - 1];
            for (PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) { // make sure the line/arc from last point to next point exists
//                double pointToPointX = nextPoint.x - lastPoint.x;
//                double pointToPointY = nextPoint.y - lastPoint.y;
//                double pointToPointDistance = Math.sqrt(pointToPointX*pointToPointX + pointToPointY*pointToPointY);


                    PolyarcgonPointCache otherLastPoint = otherPoints[otherPoints.length - 1];
                    for (PolyarcgonPointCache otherNextPoint : otherPoints) {
                        if (!otherNextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
//                        double otherPointToPointX = otherNextPoint.x - otherLastPoint.x;
//                        double otherPointToPointY = otherNextPoint.y - otherLastPoint.y;
//                    double otherPointToPointDistance = Math.sqrt(otherPointToPointX*otherPointToPointX + otherPointToPointY*otherPointToPointY);


                            if (nextPoint instanceof PolyarcgonStraightPointCache) {
                                if (otherNextPoint instanceof PolyarcgonStraightPointCache) {
                                    addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(handler, lastPoint, (PolyarcgonStraightPointCache) nextPoint, otherLastPoint, (PolyarcgonStraightPointCache) otherNextPoint, 0);
                                } else if (otherNextPoint instanceof PolyarcgonArcedPointCache) {
                                    addPotentialLineSegmentIntersectionWithArcToOverlap(handler, lastPoint, (PolyarcgonStraightPointCache) nextPoint, otherLastPoint, (PolyarcgonArcedPointCache) otherNextPoint, true, 0);
                                }
                            } else if (nextPoint instanceof PolyarcgonArcedPointCache) {
                                if (otherNextPoint instanceof PolyarcgonStraightPointCache) {
                                    addPotentialLineSegmentIntersectionWithArcToOverlap(handler, otherLastPoint, (PolyarcgonStraightPointCache) otherNextPoint, lastPoint, (PolyarcgonArcedPointCache) nextPoint, false, 0);
                                } else if (otherNextPoint instanceof PolyarcgonArcedPointCache) {
                                    addPotentialArcIntersectionWithArcToOverlap(handler, lastPoint, (PolyarcgonArcedPointCache) nextPoint, otherLastPoint, (PolyarcgonArcedPointCache) otherNextPoint, 0);
                                }
                            }
                        }
                        otherLastPoint = otherNextPoint;
                    }
                }
                lastPoint = nextPoint;
            }
        }
    }

    static void addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(OverlapHandler overlap, PolyarcgonPointCache lastPoint, PolyarcgonStraightPointCache nextPoint, PolyarcgonPointCache otherLastPoint, PolyarcgonStraightPointCache otherNextPoint, double maxRoundingErrorForNearCollisions) {
        double edgeCrossProduct = nextPoint.pointToPointX * otherNextPoint.pointToPointY - nextPoint.pointToPointY * otherNextPoint.pointToPointX;
        double lastPointX = lastPoint.getX();
        double lastPointY = lastPoint.getY();
        double otherLastPointX = otherLastPoint.getX();
        double otherLastPointY = otherLastPoint.getY();
        double edgeDistanceCrossProduct = otherNextPoint.pointToPointX * (lastPointY- otherLastPointY) - otherNextPoint.pointToPointY * (lastPointX- otherLastPointX);
        double otherEdgeDistanceCrossProduct = nextPoint.pointToPointX * (lastPointY- otherLastPointY) - nextPoint.pointToPointY * (lastPointX- otherLastPointX);

        //check whether the two edges are parallel
        if ((Math.abs(edgeDistanceCrossProduct) + Math.abs(otherEdgeDistanceCrossProduct)) / 1000000 >= Math.abs(edgeCrossProduct)) {
            //almost parallel, cannot find intercept without dividing by zero
            //so assume neither line segment intersects the other line
        } else {
            double newInterceptRelativeToPointToPoint = edgeDistanceCrossProduct / edgeCrossProduct;

            boolean lineSegmentIntersectsOtherRay = true;
            boolean otherLineSegmentIntersectsRay = true;
            if (newInterceptRelativeToPointToPoint <= 0-maxRoundingErrorForNearCollisions) {
//                                    newInterceptRelativeToPointToPoint = 0;
                lineSegmentIntersectsOtherRay = false;
                otherLineSegmentIntersectsRay = false;
            } else if (newInterceptRelativeToPointToPoint >= 1+maxRoundingErrorForNearCollisions) {
                newInterceptRelativeToPointToPoint = 1;
                lineSegmentIntersectsOtherRay = false;
            }

            double newInterceptRelativeToOtherPointToPoint = otherEdgeDistanceCrossProduct / edgeCrossProduct;
            if (newInterceptRelativeToOtherPointToPoint <= 0-maxRoundingErrorForNearCollisions) {
//                                    newInterceptRelativeToOtherPointToPoint = 0;
                otherLineSegmentIntersectsRay = false;
                lineSegmentIntersectsOtherRay = false;
            } else if (newInterceptRelativeToOtherPointToPoint >= 1+maxRoundingErrorForNearCollisions) {
                newInterceptRelativeToOtherPointToPoint = 1;
                otherLineSegmentIntersectsRay = false;
            }

            if (lineSegmentIntersectsOtherRay || otherLineSegmentIntersectsRay) {
                double newInterceptX = 0;
                double newInterceptY = 0;
                if (lineSegmentIntersectsOtherRay && otherLineSegmentIntersectsRay) {
                    newInterceptX = lastPointX + nextPoint.pointToPointX * newInterceptRelativeToPointToPoint;
                    newInterceptY = lastPointY + nextPoint.pointToPointY * newInterceptRelativeToPointToPoint;
                } else if (lineSegmentIntersectsOtherRay) {
                    newInterceptX = otherNextPoint.x;
                    newInterceptY = otherNextPoint.y;
                } else if (otherLineSegmentIntersectsRay) {
                    newInterceptX = nextPoint.x;
                    newInterceptY = nextPoint.y;
                }
                int windingFactor;
                if (edgeCrossProduct > 0) {
                    windingFactor = 1;
                } else {
                    windingFactor = -1;
                }
                if (otherLineSegmentIntersectsRay) {
                    overlap.addLineSegmentToOverlap(lastPointX, lastPointY, newInterceptX, newInterceptY, windingFactor, true, nextPoint, lineSegmentIntersectsOtherRay);
                }
                if (lineSegmentIntersectsOtherRay) {
                    overlap.addLineSegmentToOverlap(otherLastPointX, otherLastPointY, newInterceptX, newInterceptY, -windingFactor, false, otherNextPoint, otherLineSegmentIntersectsRay);
                }
            }
        }
    }

    static void addPotentialLineSegmentIntersectionWithArcToOverlap(OverlapHandler overlap, PolyarcgonPointCache lineSegmentLastPoint, PolyarcgonStraightPointCache lineSegmentNextPoint, PolyarcgonPointCache arcLastPoint, PolyarcgonArcedPointCache arcNextPoint, boolean lineSegmentIsFirstShape, double maxRoundingErrorForNearCollisions) {
        double pointToPointDistance = Math.sqrt(lineSegmentNextPoint.pointToPointX * lineSegmentNextPoint.pointToPointX + lineSegmentNextPoint.pointToPointY * lineSegmentNextPoint.pointToPointY);

        double lineSegmentLastPointX = lineSegmentLastPoint.getX();
        double lineSegmentLastPointY = lineSegmentLastPoint.getY();
        double arcLastPointX = arcLastPoint.getX();
        double arcLastPointY = arcLastPoint.getY();

        double pointToArcCenterX = arcNextPoint.arcCenterX- lineSegmentLastPointX;
        double pointToArcCenterY = arcNextPoint.arcCenterY- lineSegmentLastPointY;

        double lastPointAngleFromArcCenter = Math.atan2(lineSegmentLastPointY - arcNextPoint.arcCenterY, lineSegmentLastPointX - arcNextPoint.arcCenterX);
        double nextPointAngleFromArcCenter = Math.atan2(lineSegmentNextPoint.y - arcNextPoint.arcCenterY, lineSegmentNextPoint.x - arcNextPoint.arcCenterX);

        double crossProduct = lineSegmentNextPoint.pointToPointX *pointToArcCenterY - lineSegmentNextPoint.pointToPointY *pointToArcCenterX;
        double centerPerpendicularSignedDistance = crossProduct/pointToPointDistance;

        boolean lineSegmentIntersectsArcRay = false; //this boolean, unlike others, is false until proven true because there are multiple ways for it to be true
        boolean lineSegmentIntersectsCircle1 = true;
        boolean lineSegmentIntersectsCircle2 = true;
        boolean lineSegmentFullyInsideCircle = true;

        if (maxRoundingErrorForNearCollisions > 0) {
            if (Math.abs(centerPerpendicularSignedDistance) >= arcNextPoint.radiusOfCurvature && Math.abs(centerPerpendicularSignedDistance) < arcNextPoint.radiusOfCurvature + maxRoundingErrorForNearCollisions) {
                centerPerpendicularSignedDistance -= Math.copySign(maxRoundingErrorForNearCollisions, centerPerpendicularSignedDistance);
            }
        }

        if (Math.abs(centerPerpendicularSignedDistance) >= arcNextPoint.radiusOfCurvature) {
            //line does not go through circle at all
            lineSegmentIntersectsCircle1 = false;
            lineSegmentIntersectsCircle2 = false;
            lineSegmentFullyInsideCircle = false;
        } else {
            double dotProduct = pointToArcCenterX* lineSegmentNextPoint.pointToPointX + pointToArcCenterY* lineSegmentNextPoint.pointToPointY;
            double centerParallelDistance = dotProduct/pointToPointDistance;
            double interceptParallelDistance = Math.sqrt(arcNextPoint.radiusOfCurvature*arcNextPoint.radiusOfCurvature - centerPerpendicularSignedDistance*centerPerpendicularSignedDistance);

            boolean lineSegmentIntersectsArc1 = true;
            boolean lineSegmentIntersectsArc2 = true;
            boolean arc1IntersectsRay = true;
            boolean arc2IntersectsRay = true;

            double intercept1Position = centerParallelDistance - interceptParallelDistance;
            double cappedIntercept1Position;
            if (intercept1Position <= 0-maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsCircle1 = false;
                arc1IntersectsRay = false;
                cappedIntercept1Position = 0;
            } else if (intercept1Position >= pointToPointDistance+maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle1 = false;
                lineSegmentIntersectsCircle2 = false;
                lineSegmentFullyInsideCircle = false;
                cappedIntercept1Position = pointToPointDistance;
            } else {
                lineSegmentFullyInsideCircle = false;
                cappedIntercept1Position = intercept1Position;
            }

            double intercept2Position = centerParallelDistance + interceptParallelDistance;
            double cappedIntercept2Position;
            if (intercept2Position <= 0-maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle1 = false;
                lineSegmentIntersectsCircle2 = false;
                lineSegmentFullyInsideCircle = false;
                arc1IntersectsRay = false;
                arc2IntersectsRay = false;
                cappedIntercept2Position = 0;
            } else if (intercept2Position >= pointToPointDistance+maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle2 = false;
                cappedIntercept2Position = pointToPointDistance;
            } else {
                lineSegmentFullyInsideCircle = false;
                cappedIntercept2Position = intercept2Position;
            }

            double cosLineAngle = lineSegmentNextPoint.pointToPointX / pointToPointDistance;
            double sinLineAngle = lineSegmentNextPoint.pointToPointY / pointToPointDistance;

            double intercept1AngleFromArcCenter = 0;
            double intercept1AngleFromArcCenterRelativeToArcStart = 0;

            double cappedIntercept1X = 0;
            double cappedIntercept1Y = 0;
            if (lineSegmentIntersectsArc1 || arc1IntersectsRay) {
                double intercept1X = lineSegmentLastPointX + cosLineAngle * intercept1Position;
                double intercept1Y = lineSegmentLastPointY + sinLineAngle * intercept1Position;

                intercept1AngleFromArcCenter = Math.atan2(intercept1Y - arcNextPoint.arcCenterY, intercept1X - arcNextPoint.arcCenterX);
                if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                    intercept1AngleFromArcCenterRelativeToArcStart = (intercept1AngleFromArcCenter - arcNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10*Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                    if (intercept1AngleFromArcCenterRelativeToArcStart >= arcNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc1 = false;
                        arc1IntersectsRay = false;
                    }
                } else {
                    intercept1AngleFromArcCenterRelativeToArcStart = (intercept1AngleFromArcCenter - arcNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10*Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                    if (intercept1AngleFromArcCenterRelativeToArcStart <= arcNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc1 = false;
                        arc1IntersectsRay = false;
                    }
                }
                if (lineSegmentIntersectsArc1) {
                    cappedIntercept1X = intercept1X;
                    cappedIntercept1Y = intercept1Y;
                } else if (arc1IntersectsRay) {
                    cappedIntercept1X = lineSegmentLastPointX + cosLineAngle * cappedIntercept1Position;
                    cappedIntercept1Y = lineSegmentLastPointY + sinLineAngle * cappedIntercept1Position;
                }
            }

            double intercept2AngleFromArcCenter = 0;
            double intercept2AngleFromArcCenterRelativeToArcStart = 0;

            double cappedIntercept2X = 0;
            double cappedIntercept2Y = 0;
            if (lineSegmentIntersectsArc2 || arc2IntersectsRay) {
                double intercept2X = lineSegmentLastPointX + cosLineAngle * intercept2Position;
                double intercept2Y = lineSegmentLastPointY + sinLineAngle * intercept2Position;

                intercept2AngleFromArcCenter = Math.atan2(intercept2Y - arcNextPoint.arcCenterY, intercept2X - arcNextPoint.arcCenterX);
                if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                    intercept2AngleFromArcCenterRelativeToArcStart = (intercept2AngleFromArcCenter - arcNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10*Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                    if (intercept2AngleFromArcCenterRelativeToArcStart >= arcNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc2 = false;
                        arc2IntersectsRay = false;
                    }
                } else {
                    intercept2AngleFromArcCenterRelativeToArcStart = (intercept2AngleFromArcCenter - arcNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10*Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                    if (intercept2AngleFromArcCenterRelativeToArcStart <= arcNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc2 = false;
                        arc2IntersectsRay = false;
                    }
                }
                if (lineSegmentIntersectsArc2) {
                    cappedIntercept2X = intercept2X;
                    cappedIntercept2Y = intercept2Y;
                } else if (arc2IntersectsRay) {
                    cappedIntercept2X = lineSegmentLastPointX + cosLineAngle * cappedIntercept2Position;
                    cappedIntercept2Y = lineSegmentLastPointY + sinLineAngle * cappedIntercept2Position;
                }
            }

            if (lineSegmentIntersectsCircle1) {
                if (centerPerpendicularSignedDistance > 0) {
                    if ((intercept1AngleFromArcCenter - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                } else {
                    if ((-(intercept1AngleFromArcCenter - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                }
            }
            if (lineSegmentIntersectsCircle2) {
                if (centerPerpendicularSignedDistance > 0) {
                    if ((nextPointAngleFromArcCenter - intercept2AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - intercept2AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                } else {
                    if ((-(nextPointAngleFromArcCenter - intercept2AngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - intercept2AngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                }
            }

            int windingFactor;
            if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                windingFactor = 1;
            } else {
                windingFactor = -1;
            }

            if (arc1IntersectsRay) {
                overlap.addLineSegmentToOverlap(lineSegmentLastPointX, lineSegmentLastPointY, cappedIntercept1X, cappedIntercept1Y, -windingFactor, lineSegmentIsFirstShape, lineSegmentNextPoint, lineSegmentIntersectsArc1);
            }
            if (arc2IntersectsRay) {
                overlap.addLineSegmentToOverlap(lineSegmentLastPointX, lineSegmentLastPointY, cappedIntercept2X, cappedIntercept2Y, windingFactor, lineSegmentIsFirstShape, lineSegmentNextPoint, lineSegmentIntersectsArc2);
            }
            if (lineSegmentIntersectsArc1) {
                overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, cappedIntercept1X, cappedIntercept1Y, intercept1AngleFromArcCenterRelativeToArcStart, windingFactor, !lineSegmentIsFirstShape, arcNextPoint, true);
            }
            if (lineSegmentIntersectsArc2) {
                overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, cappedIntercept2X, cappedIntercept2Y, intercept2AngleFromArcCenterRelativeToArcStart, -windingFactor, !lineSegmentIsFirstShape, arcNextPoint, true);
            }
        }

        if (!lineSegmentIntersectsCircle1 && !lineSegmentIntersectsCircle2 && !lineSegmentFullyInsideCircle) {
            if (centerPerpendicularSignedDistance > 0) {
                if ((nextPointAngleFromArcCenter - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    lineSegmentIntersectsArcRay = true;
                }
            } else {
                if ((-(nextPointAngleFromArcCenter - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                    lineSegmentIntersectsArcRay = true;
                }
            }
        }
        if (lineSegmentIntersectsArcRay) {
            int rayWindingFactor = (centerPerpendicularSignedDistance > 0) ? 1 : -1;
            overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, arcNextPoint.x, arcNextPoint.y, arcNextPoint.nonCachePoint.arcAngleChange, rayWindingFactor, !lineSegmentIsFirstShape, arcNextPoint, false);
        }
    }

    static void addPotentialArcIntersectionWithArcToOverlap(OverlapHandler overlap, PolyarcgonPointCache lastPoint, PolyarcgonArcedPointCache nextPoint, PolyarcgonPointCache otherLastPoint, PolyarcgonArcedPointCache otherNextPoint, double maxRoundingErrorForNearCollisions) {
        double lastPointX = lastPoint.getX();
        double lastPointY = lastPoint.getY();
        double otherLastPointX = otherLastPoint.getX();
        double otherLastPointY = otherLastPoint.getY();

        double distanceSq = (otherNextPoint.arcCenterX-nextPoint.arcCenterX)*(otherNextPoint.arcCenterX-nextPoint.arcCenterX) + (otherNextPoint.arcCenterY-nextPoint.arcCenterY)*(otherNextPoint.arcCenterY-nextPoint.arcCenterY);
        double distance = Math.sqrt(distanceSq);

        double angleOfArcStartFromOtherArcCenter = Math.atan2(lastPointY-otherNextPoint.arcCenterY, lastPointX-otherNextPoint.arcCenterX);
        double angleOfArcEndFromOtherArcCenter = Math.atan2(nextPoint.y-otherNextPoint.arcCenterY, nextPoint.x-otherNextPoint.arcCenterX);

        double angleOfOtherArcStartFromArcCenter = Math.atan2(otherLastPointY-nextPoint.arcCenterY, otherLastPointX-nextPoint.arcCenterX);
        double angleOfOtherArcEndFromArcCenter = Math.atan2(otherNextPoint.y-nextPoint.arcCenterY, otherNextPoint.x-nextPoint.arcCenterX);

        int windingFactor = (nextPoint.nonCachePoint.arcAngleChange > 0) ? 1 : -1;
        int otherWindingFactor = (otherNextPoint.nonCachePoint.arcAngleChange > 0) ? 1 : -1;

        boolean arcIntersectsOtherCircle1 = true;
        boolean arcIntersectsOtherCircle2 = true;
        boolean otherArcIntersectsCircle1 = true;
        boolean otherArcIntersectsCircle2 = true;
        boolean circleContainsOtherArcStart = true;
        boolean otherCircleContainsArcStart = true;
        boolean circleContainsOtherArcCenter = true;
        boolean otherCircleContainsArcCenter = true;
        int numberOfTimesArcIntersectsOtherRayClockwise = 0; //really is counterclockwise from the y is up point of view
        int numberOfTimesOtherArcIntersectsRayClockwise = 0;

        if (maxRoundingErrorForNearCollisions > 0) {
            if (distance >= nextPoint.radiusOfCurvature+otherNextPoint.radiusOfCurvature && distance-maxRoundingErrorForNearCollisions < nextPoint.radiusOfCurvature+otherNextPoint.radiusOfCurvature) {
                distance -= maxRoundingErrorForNearCollisions;
                distanceSq = distance*distance;
            } else if ((nextPoint.radiusOfCurvature >= distance+otherNextPoint.radiusOfCurvature && nextPoint.radiusOfCurvature < distance+maxRoundingErrorForNearCollisions+otherNextPoint.radiusOfCurvature)
                    || (otherNextPoint.radiusOfCurvature >= distance+nextPoint.radiusOfCurvature) && (otherNextPoint.radiusOfCurvature < distance+maxRoundingErrorForNearCollisions+nextPoint.radiusOfCurvature)) {
                distance += maxRoundingErrorForNearCollisions;
                distanceSq = distance*distance;
            }
        }

        if (distance >= nextPoint.radiusOfCurvature+otherNextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            circleContainsOtherArcStart = false;
            otherCircleContainsArcStart = false;
            circleContainsOtherArcCenter = false;
            otherCircleContainsArcCenter = false;
        } else if (nextPoint.radiusOfCurvature >= distance+otherNextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            otherCircleContainsArcStart = false;
            otherCircleContainsArcCenter = false;
        } else if (otherNextPoint.radiusOfCurvature >= distance+nextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            circleContainsOtherArcStart = false;
            circleContainsOtherArcCenter = false;
        } else {
            circleContainsOtherArcCenter = nextPoint.radiusOfCurvature > distance;
            otherCircleContainsArcCenter = otherNextPoint.radiusOfCurvature > distance;

            double radiusSq = nextPoint.radiusOfCurvature*nextPoint.radiusOfCurvature;
            double otherRadiusSq = otherNextPoint.radiusOfCurvature*otherNextPoint.radiusOfCurvature;

            //let the point P refer to one of the two points where the two circles intersect
            //let point A be the center of this circle, and point B be the center of the other circle

            //the angle PAB
            double angleAtArcCenterBetweenOtherArcCenterAndIntersections = Math.acos((radiusSq-otherRadiusSq+distanceSq)/(2.0*nextPoint.radiusOfCurvature*distance));

            //the angle PBA
            double angleAtOtherArcCenterBetweenArcCenterAndIntersections = Math.acos((otherRadiusSq-radiusSq+distanceSq)/(2.0*otherNextPoint.radiusOfCurvature*distance));

            //the angle from this circle to the other circle
            double angleFromArcCenterToOtherArcCenter = Math.atan2(otherNextPoint.arcCenterY-nextPoint.arcCenterY, otherNextPoint.arcCenterX-nextPoint.arcCenterX);
            double angleFromOtherArcCenterToArcCenter = angleFromArcCenterToOtherArcCenter + Math.PI;

            double intersection1AngleFromArcCenter = angleFromArcCenterToOtherArcCenter - angleAtArcCenterBetweenOtherArcCenterAndIntersections;
            double intersection2AngleFromArcCenter = angleFromArcCenterToOtherArcCenter + angleAtArcCenterBetweenOtherArcCenterAndIntersections;

            double intersection1AngleFromOtherArcCenter = angleFromOtherArcCenterToArcCenter + angleAtOtherArcCenterBetweenArcCenterAndIntersections;
            double intersection2AngleFromOtherArcCenter = angleFromOtherArcCenterToArcCenter - angleAtOtherArcCenterBetweenArcCenterAndIntersections;

            double intersection1X = nextPoint.arcCenterX + nextPoint.radiusOfCurvature*Math.cos(intersection1AngleFromArcCenter);
            double intersection1Y = nextPoint.arcCenterY + nextPoint.radiusOfCurvature*Math.sin(intersection1AngleFromArcCenter);

            double intersection2X = nextPoint.arcCenterX + nextPoint.radiusOfCurvature*Math.cos(intersection2AngleFromArcCenter);
            double intersection2Y = nextPoint.arcCenterY + nextPoint.radiusOfCurvature*Math.sin(intersection2AngleFromArcCenter);

            double angleBetweenIntercept1AndArcStartFromArcCenter;
            double angleBetweenIntercept2AndArcStartFromArcCenter;
            if (nextPoint.nonCachePoint.arcAngleChange > 0) {
                angleBetweenIntercept1AndArcStartFromArcCenter = (intersection1AngleFromArcCenter - nextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndArcStartFromArcCenter >= nextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle1 = false;
                }
                angleBetweenIntercept2AndArcStartFromArcCenter = (intersection2AngleFromArcCenter - nextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndArcStartFromArcCenter >= nextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle2 = false;
                }
            } else {
                angleBetweenIntercept1AndArcStartFromArcCenter = (intersection1AngleFromArcCenter - nextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndArcStartFromArcCenter <= nextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle1 = false;
                }
                angleBetweenIntercept2AndArcStartFromArcCenter = (intersection2AngleFromArcCenter - nextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndArcStartFromArcCenter <= nextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle2 = false;
                }
            }
            if (angleBetweenIntercept2AndArcStartFromArcCenter >= angleBetweenIntercept1AndArcStartFromArcCenter) {
                otherCircleContainsArcStart = false; //because the arc starts outside the other circle
            }
            double angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter;
            double angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter;
            if (otherNextPoint.nonCachePoint.arcAngleChange > 0) {
                angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter = (intersection1AngleFromOtherArcCenter - otherNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter >= otherNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle1 = false;
                }
                angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter = (intersection2AngleFromOtherArcCenter - otherNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2*Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter >= otherNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle2 = false;
                }
            } else {
                angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter = (intersection1AngleFromOtherArcCenter - otherNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter <= otherNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle1 = false;
                }
                angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter = (intersection2AngleFromOtherArcCenter - otherNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2*Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter <= otherNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle2 = false;
                }
            }
            if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter >= angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter) {
                circleContainsOtherArcStart = false; //because the other arc starts outside the circle
            }
            if (arcIntersectsOtherCircle1 && arcIntersectsOtherCircle2 && otherCircleContainsArcStart) {
                if ((intersection1AngleFromOtherArcCenter - intersection2AngleFromOtherArcCenter + 10*Math.PI) % (2*Math.PI) > (otherNextPoint.endAngle - intersection2AngleFromOtherArcCenter + 10*Math.PI) % (2*Math.PI)) {
                    numberOfTimesArcIntersectsOtherRayClockwise += windingFactor;
                }
            } else {
                double angleBetweenArcStartAndArcCenterFromOtherArcCenter = ((angleOfArcStartFromOtherArcCenter - angleFromOtherArcCenterToArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                double angleBetweenArcEndAndArcCenterFromOtherArcCenter = ((angleOfArcEndFromOtherArcCenter - angleFromOtherArcCenterToArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                if (arcIntersectsOtherCircle1) {
                    numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(otherNextPoint.endAngle, intersection1AngleFromOtherArcCenter, angleAtOtherArcCenterBetweenArcCenterAndIntersections, angleBetweenArcStartAndArcCenterFromOtherArcCenter, angleBetweenArcEndAndArcCenterFromOtherArcCenter, windingFactor, 1);
                }
                if (arcIntersectsOtherCircle2) {
                    numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(otherNextPoint.endAngle, intersection2AngleFromOtherArcCenter, angleAtOtherArcCenterBetweenArcCenterAndIntersections, angleBetweenArcStartAndArcCenterFromOtherArcCenter, angleBetweenArcEndAndArcCenterFromOtherArcCenter, windingFactor, -1);
                }
            }

            if (otherArcIntersectsCircle1 && otherArcIntersectsCircle2 && circleContainsOtherArcStart) {
                if ((intersection2AngleFromArcCenter - intersection1AngleFromArcCenter + 10*Math.PI) % (2*Math.PI) > (nextPoint.endAngle - intersection1AngleFromArcCenter + 10*Math.PI) % (2*Math.PI)) {
                    numberOfTimesOtherArcIntersectsRayClockwise += otherWindingFactor;
                }
            } else {
                double angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter = ((angleOfOtherArcStartFromArcCenter - angleFromArcCenterToOtherArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                double angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter = ((angleOfOtherArcEndFromArcCenter - angleFromArcCenterToOtherArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                if (otherArcIntersectsCircle1) {
                    numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(nextPoint.endAngle, intersection1AngleFromArcCenter, angleAtArcCenterBetweenOtherArcCenterAndIntersections, angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter, angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter, otherWindingFactor, -1);
                }
                if (otherArcIntersectsCircle2) {
                    numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(nextPoint.endAngle, intersection2AngleFromArcCenter, angleAtArcCenterBetweenOtherArcCenterAndIntersections, angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter, angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter, otherWindingFactor, 1);
                }
            }
            if (arcIntersectsOtherCircle1 && otherArcIntersectsCircle1) {
                overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, intersection1X, intersection1Y, angleBetweenIntercept1AndArcStartFromArcCenter, -windingFactor*otherWindingFactor, true, nextPoint, true);
                overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, intersection1X, intersection1Y, angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter, windingFactor*otherWindingFactor, false, otherNextPoint, true);
            }
            if (arcIntersectsOtherCircle2 && otherArcIntersectsCircle2) {
                overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, intersection2X, intersection2Y, angleBetweenIntercept2AndArcStartFromArcCenter, windingFactor*otherWindingFactor, true, nextPoint, true);
                overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, intersection2X, intersection2Y, angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter, -windingFactor*otherWindingFactor, false, otherNextPoint, true);
            }
        }
        if (!otherArcIntersectsCircle1 && !otherArcIntersectsCircle2 && !circleContainsOtherArcStart) {
            numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(nextPoint.endAngle, otherNextPoint.nonCachePoint.arcAngleChange, angleOfOtherArcStartFromArcCenter, angleOfOtherArcEndFromArcCenter, otherCircleContainsArcCenter);
        }
        if (!arcIntersectsOtherCircle1 && !arcIntersectsOtherCircle2 && !otherCircleContainsArcStart) {
            numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(otherNextPoint.endAngle, nextPoint.nonCachePoint.arcAngleChange, angleOfArcStartFromOtherArcCenter, angleOfArcEndFromOtherArcCenter, circleContainsOtherArcCenter);
        }
        // Note it is logically impossible for these to be different than -1, 0, or 1
        if (numberOfTimesOtherArcIntersectsRayClockwise != 0) {
            overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, nextPoint.x, nextPoint.y, nextPoint.nonCachePoint.arcAngleChange, numberOfTimesOtherArcIntersectsRayClockwise, true, nextPoint, false);
        }
        if (numberOfTimesArcIntersectsOtherRayClockwise != 0) {
            overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, otherNextPoint.x, otherNextPoint.y, otherNextPoint.nonCachePoint.arcAngleChange, numberOfTimesArcIntersectsOtherRayClockwise, false, otherNextPoint, false);
        }
    }
    private static int getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(double arcBEndAngle, double arcAAngleChange, double angleOfArcAStartFromArcBCenter, double angleOfArcAEndFromArcBCenter, boolean circleAContainsArcBCenter) {
        if (circleAContainsArcBCenter) { //note cannot replace this with circleContainsOtherArcStart
            //it can only move clockwise or counterclockwise depending on whether windingFactor is positive
            if (arcAAngleChange > 0) {
                if ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI) > (arcBEndAngle - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    return 1;
                }
            } else {
                if ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI) < (arcBEndAngle - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                    return -1;
                }
            }
        } else {
            //it can move either clockwise or counterclockwise regardless of winding factor, but cannot rotate more than pi in either direction

            double angleBetweenArcStartAndArcEndFromOtherArcCenter = ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
            if (angleBetweenArcStartAndArcEndFromOtherArcCenter > 0) {
                if (angleBetweenArcStartAndArcEndFromOtherArcCenter > (arcBEndAngle - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    return 1;
                }
            } else {
                if (angleBetweenArcStartAndArcEndFromOtherArcCenter < (arcBEndAngle - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                    return -1;
                }
            }
        }
        return 0;
    }

    private static int getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(double arcBEndAngle, double intersectionCAngleFromArcBCenter, double angleAtArcBCenterBetweenArcACenterAndIntersections, double angleBetweenArcAStartAndArcACenterFromArcBCenter, double angleBetweenArcAEndAndArcACenterFromArcBCenter, int arcAWindingFactor, int signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter) {
        // To calculate the angle between intercept 1 and the arc start/end (from the point of view of the other center),
        // we can't just look at the two angles because we don't know which way the arc turns from one angle to the other.
        // Instead we have to break the angle into two pieces: one between intercept C and the center (which we know the direction of),
        // and one between the center and the arc start/end (which is less than pi).
//        wrong: double angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = (angleOfArcAStartOrEndFromArcBCenter - intersectionCAngleFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)
        double angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter;
        if (arcAWindingFactor*signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter > 0) {
            angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = angleBetweenArcAStartAndArcACenterFromArcBCenter + angleAtArcBCenterBetweenArcACenterAndIntersections*(-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter);
        } else {
            angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = angleBetweenArcAEndAndArcACenterFromArcBCenter + angleAtArcBCenterBetweenArcACenterAndIntersections*(-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter);
        }
        if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter > 0) {
            if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter > (arcBEndAngle - intersectionCAngleFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                return (-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter) * arcAWindingFactor;
            }
        }
        if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter < 0) {
            if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter < (arcBEndAngle - intersectionCAngleFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                return signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter * arcAWindingFactor;
            }
        }
        return 0;
    }

    static interface PolyarcgonPointCache {

        double getX();
        double getY();
        PolyarcgonPoint getNonCachePoint();
        void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation);
        void updateLineOrArcCache(PolyarcgonPointCache lastPoint);
    }
    static class PolyarcgonStraightPointCache implements PolyarcgonPointCache {
        double x;
        double y;
        public double pointToPointX;
        public double pointToPointY;
        final PolyarcgonPoint nonCachePoint;
        PolyarcgonStraightPointCache(PolyarcgonPoint nonCachePoint) {
            this.nonCachePoint = nonCachePoint;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public PolyarcgonPoint getNonCachePoint() {
            return nonCachePoint;
        }

        @Override
        public void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation) {
            x = nonCachePoint.x*cosRotation - nonCachePoint.y*sinRotation + xTranslation;
            y = nonCachePoint.y*cosRotation + nonCachePoint.x*sinRotation + yTranslation;
        }

        @Override
        public void updateLineOrArcCache(PolyarcgonPointCache lastPoint) {
            pointToPointX = x-lastPoint.getX();
            pointToPointY = y-lastPoint.getY();
        }
    }
    static class PolyarcgonArcedPointCache implements PolyarcgonPointCache {
        double x;
        double y;
        double radiusOfCurvature = 0;
        private double signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = 0;
        double arcCenterX;
        double arcCenterY;
        double startAngle;
        double endAngle;
        final PolyarcgonPoint nonCachePoint;

        PolyarcgonArcedPointCache(PolyarcgonPoint nonCachePoint) {
            this.nonCachePoint = nonCachePoint;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public PolyarcgonPoint getNonCachePoint() {
            return nonCachePoint;
        }

        @Override
        public void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation) {
            x = nonCachePoint.x*cosRotation - nonCachePoint.y*sinRotation + xTranslation;
            y = nonCachePoint.y*cosRotation + nonCachePoint.x*sinRotation + yTranslation;
        }
        @Override
        public void updateLineOrArcCache(PolyarcgonPointCache lastPoint) {
            double lastPointX = lastPoint.getX();
            double lastPointY = lastPoint.getY();
            if (radiusOfCurvature == 0 || signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance == 0) {
                double arcPointToPointX = x - lastPointX;
                double arcPointToPointY = y - lastPointY;
                double arcPointToPointDistance = Math.sqrt(arcPointToPointX * arcPointToPointX + arcPointToPointY * arcPointToPointY);
                double arcSignedRadiusOfCurvature = arcPointToPointDistance / (2.0 * Math.sin(nonCachePoint.arcAngleChange / 2.0));
                double arcSignedDistanceBetweenArcCenterAndStraightEdge = arcSignedRadiusOfCurvature * Math.cos(nonCachePoint.arcAngleChange / 2.0);
                signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = arcSignedDistanceBetweenArcCenterAndStraightEdge/arcPointToPointDistance;
                radiusOfCurvature = Math.abs(arcSignedRadiusOfCurvature);
            }
            arcCenterX = (lastPointX + x)/2.0 + (-(y- lastPointY))*signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
            arcCenterY = (lastPointY + y)/2.0 + (x- lastPointX)*signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
            startAngle = Math.atan2(lastPointY - arcCenterY, lastPointX - arcCenterX);
            endAngle = startAngle + nonCachePoint.arcAngleChange;
        }
    }
    static double[] getArcCenterAndSignedRadius(double lastPointX, double lastPointY, double nextPointX, double nextPointY, double arcAngleChange) {
        double arcPointToPointX = nextPointX - lastPointX;
        double arcPointToPointY = nextPointY - lastPointY;
        double arcPointToPointDistance = Math.sqrt(arcPointToPointX * arcPointToPointX + arcPointToPointY * arcPointToPointY);
        double arcSignedRadiusOfCurvature = arcPointToPointDistance / (2.0 * Math.sin(arcAngleChange / 2.0));
        double arcSignedDistanceBetweenArcCenterAndStraightEdge = arcSignedRadiusOfCurvature * Math.cos(arcAngleChange / 2.0);
        double signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = arcSignedDistanceBetweenArcCenterAndStraightEdge/arcPointToPointDistance;

        double[] arcCenterAndSignedRadius = new double[3];
        arcCenterAndSignedRadius[0] = (lastPointX + nextPointX)/2.0 + (-(nextPointY- lastPointY))*signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
        arcCenterAndSignedRadius[1] = (lastPointY + nextPointY)/2.0 + (nextPointX- lastPointX)*signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
        arcCenterAndSignedRadius[2] = arcSignedRadiusOfCurvature;
        return arcCenterAndSignedRadius;
    }
}
class GameCompositeShape implements GameShape {
    private ArrayList<GameShape> allShapes;
    private HashSet<GameShape> negativeShapes;

    double x;
    double y;
    double mass;
    double area;
    double momentOfInertia;
    double boundingRadius;
    double rotationRadians;

    private double xWhenLastUpdatedShapes = 0;
    private double yWhenLastUpdatedShapes = 0;
    private double rotationWhenLastUpdatedShapes;
    private ArrayList<DoublePoint> allShapesTemplatePositions;

    public GameCompositeShape(ArrayList<GameShape> shapes) {
        this(shapes, new HashSet<GameShape>());
    }

    public GameCompositeShape(ArrayList<GameShape> allShapes, HashSet<GameShape> negativeShapes) {
        this.allShapes = allShapes;
        this.negativeShapes = negativeShapes;
        for (GameShape shape : allShapes) {
            if (negativeShapes.contains(shape)) {
                mass -= shape.getMass();
                x -= shape.getX()*shape.getMass();
                y -= shape.getY()*shape.getMass();
                area -= shape.getArea();
            } else {
                mass += shape.getMass();
                x += shape.getX()*shape.getMass();
                y += shape.getY()*shape.getMass();
                area += shape.getArea();
            }
        }
        x /= mass;
        y /= mass;


        rotationRadians = 0;

        xWhenLastUpdatedShapes = x;
        yWhenLastUpdatedShapes = y;
        rotationWhenLastUpdatedShapes = 0;


        allShapesTemplatePositions = new ArrayList<>();
        boundingRadius = 0;
        momentOfInertia = 0;

        for (GameShape shape : allShapes) {
            DoublePoint shapeRelativePos = new DoublePoint(shape.getX()-x, shape.getY()-y);
            allShapesTemplatePositions.add(shapeRelativePos);
            double distanceSq = shapeRelativePos.x*shapeRelativePos.x + shapeRelativePos.y*shapeRelativePos.y;
            double radius = Math.sqrt(distanceSq) + shape.getBoundingRadius();
            if (radius > boundingRadius) {
                boundingRadius = radius;
            }
            if (negativeShapes.contains(shape)) {
                momentOfInertia -= shape.getMomentOfInertia() + shape.getMass() * distanceSq;
            } else {
                momentOfInertia += shape.getMomentOfInertia() + shape.getMass() * distanceSq;
            }
        }

//        Log.i("me", String.format("mass: %.2f area: %.2f moment of inertia: %.2f x: %.2f y: %.2f bounding radius: %.2f", mass, area, momentOfInertia, x, y, boundingRadius));
    }

    public void collision(GameShape otherShape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapHandler overlapHandler) {
        if (Math.sqrt((otherShape.getX()-x)*(otherShape.getX()-x) + (otherShape.getY()-y)*(otherShape.getY()-y)) < boundingRadius + otherShape.getBoundingRadius()) {
            for (GameShape shape : getAllShapes()) {
                // note if the other shape is also a composite shape, this will end up calling the same function
                // from the other shape, colliding against part of this shape
                shape.collision(otherShape, isThisMovable, isOtherShapeMovable, thisIsFirstShape, overlapHandler);
            }
        }
    }


    @Override
    public void draw(Canvas canvas) {
//        for (GameShape shape : getAllShapes()) {
//            shape.draw(canvas); //ahh screw negative shapes for now
//        }
        drawWithBorder(canvas);
    }
    public void drawWithBorder(Canvas canvas) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            double blurRadius = 20;
//            Bitmap imageToDraw = Bitmap.createBitmap((int) (2*boundingRadius+1), (int) (2*boundingRadius+1), Bitmap.Config.ARGB_8888, true);
//            Canvas imageCanvas = new Canvas(imageToDraw);
//            imageCanvas.translate((int) (boundingRadius-x), (int) (boundingRadius-y));
            Paint paint = new Paint();
            paint.setColor(Color.rgb(0, 255, 255));
            paint.setStyle(Paint.Style.FILL);
            MaskFilter filter = paint.getMaskFilter();
            paint.setMaskFilter(new BlurMaskFilter((float) blurRadius, BlurMaskFilter.Blur.OUTER));
            for (GameShape shape : getAllShapes()) {
//                if (shape instanceof GameConvexPolygon) {
//
//                    Path path = new Path();
//                    DoublePoint[] corners = ((GameConvexPolygon) shape).getCorners();
//                    path.moveTo((float) corners[corners.length-1].x, (float) corners[corners.length-1].y); // used for first point
//                    for (int i=0; i<corners.length; i++) {
//                        path.lineTo((float) corners[i].x, (float) corners[i].y);
//                    }
//                    canvas.drawPath(path, paint);
//                } else {
////                    shape.draw(imageCanvas);
//                    shape.draw(canvas);
//                }
                shape.draw(canvas);
            }
            paint.setMaskFilter(filter);
            paint.setColor(Color.rgb(0, 128, 128));
            for (GameShape shape : getAllShapes()) {
//                if (shape instanceof GameConvexPolygon) {
//                    Path path = new Path();
//                    DoublePoint[] corners = ((GameConvexPolygon) shape).getCorners();
//                    path.moveTo((float) corners[corners.length-1].x, (float) corners[corners.length-1].y); // used for first point
//                    for (int i=0; i<corners.length; i++) {
//                        path.lineTo((float) corners[i].x, (float) corners[i].y);
//                    }
//                    canvas.drawPath(path, paint);
//                } else {
//                    shape.draw(canvas);
//                }
                shape.draw(canvas);

            }
//            Paint paint = new Paint();
//            canvas.drawBitmap(imageToDraw, (int) (x-boundingRadius), (int) (y-boundingRadius), paint);
        } else {
            for (GameShape shape : getAllShapes()) {
                shape.draw(canvas); //ahh screw negative shapes for now
            }
        }
    }

    @Override
    public void receiveForce(ForceAndTorque collision) {
//        if (this == collision.shape1) {
            //because inertia-less, don't have velocity or angular speed, just add acceleration to position instead of velocity and likewise for angular acceleration

            x += collision.forceActingOnShapeX / mass;
            y += collision.forceActingOnShapeY / mass;

            rotationRadians += collision.torqueActingOnShape / momentOfInertia;
//        }
    }
//    @Override
//    public void receiveForce(double forceX, double forceY, double torque) {
//        x += forceX / mass;
//        y += forceY / mass;
//        rotationRadians += torque / momentOfInertia;
//    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public double getArea() {
        return area;
    }

    @Override
    public double getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getMomentOfInertia() {
        return momentOfInertia;
    }

    @Override
    public void setPos(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setRotation(double rotationRadians) {
        this.rotationRadians = rotationRadians;
    }

    public ArrayList<GameShape> getAllShapes() {
        if (xWhenLastUpdatedShapes != x || yWhenLastUpdatedShapes != y || rotationWhenLastUpdatedShapes != rotationRadians) {
            double cos = Math.cos(rotationRadians);
            double sin = Math.sin(rotationRadians);
            for (int i=0; i<allShapes.size(); i++) {
                GameShape shape = allShapes.get(i);
                DoublePoint templatePoint = allShapesTemplatePositions.get(i);
                shape.setPos(x + templatePoint.x*cos + templatePoint.y*(-sin), y + templatePoint.x*sin + templatePoint.y*cos);
                shape.setRotation(rotationRadians);
            }
            xWhenLastUpdatedShapes = x;
            yWhenLastUpdatedShapes = y;
            rotationWhenLastUpdatedShapes = rotationRadians;
        }

        return allShapes;
    }

    public HashSet<GameShape> getNegativeShapes() {
        getAllShapes();
        return negativeShapes;
    }
}

/**
 * Very simple class representing an (x, y)
 * Similar to Android's Point or PointF classes, except double
 */
class DoublePoint {
    double x;
    double y;
    public DoublePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }
    public DoublePoint(DoublePoint src) {
        this.x = src.x;
        this.y = src.y;
    }

    @Override
    public boolean equals(Object o) {
        // Generated by Android Studio
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoublePoint that = (DoublePoint) o;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0;
    }

    @Override
    public int hashCode() {
        // Generated by Android Studio
        return Objects.hash(x, y);
    }
}
class ForceAndTorque {
    GameShape shape;
    double forceActingOnShapeX;
    double forceActingOnShapeY;
    double torqueActingOnShape;
    public ForceAndTorque(double x, double y, double forceActingOnShapeX, double forceActingOnShapeY, GameShape shape) {

        this.shape = shape;
        this.forceActingOnShapeX = forceActingOnShapeX;
        this.forceActingOnShapeY = forceActingOnShapeY;

        if (shape != null) {
            torqueActingOnShape = (x- shape.getX()) * forceActingOnShapeY - (y- shape.getY()) * forceActingOnShapeX;
        }
    }
    public ForceAndTorque(OverlapGradientForceCalculator overlap, boolean onFirstShape) {
        if (onFirstShape) {
            shape = overlap.firstShape;
        } else {
            shape = overlap.otherShape;

        }
        addForceAndTorque(overlap);
    }

    public void addForceAndTorque(OverlapGradientForceCalculator other) {
        // divide by overlap perimeter not force otherwise torque becomes near infinite if force zero
        if (other.overlapPerimeter != 0) {
            double depth = other.overlapArea/other.overlapPerimeter;
            int factor = 0;
            if (shape != null) {
                if (shape == other.firstShape) {
                    factor = 1;
                    torqueActingOnShape += other.overlapGradientTorqueOnFirstShape * depth;
                } else if (shape == other.otherShape) {
                    factor = -1;
                    torqueActingOnShape += other.overlapGradientTorqueOnOtherShape * depth;
                }
            }
            forceActingOnShapeX += factor * other.overlapGradientForceX * depth;
            forceActingOnShapeY += factor * other.overlapGradientForceY * depth;
        }
    }
    public void addForceAndTorque(ForceAndTorque other) {
        torqueActingOnShape += other.torqueActingOnShape;
        forceActingOnShapeX += other.forceActingOnShapeX;
        forceActingOnShapeY += other.forceActingOnShapeY;
    }


    public void multiplyIntensity(double factor) {
        forceActingOnShapeX *= factor;
        forceActingOnShapeY *= factor;
        torqueActingOnShape *= factor;
    }

    public boolean isAlmostZero() {
        double area = shape.getArea();
        return (forceActingOnShapeX * forceActingOnShapeX + forceActingOnShapeY * forceActingOnShapeY) / area + (torqueActingOnShape * torqueActingOnShape) / area / area < 0.000001 * 0.000001;
    }

    public OverlapGradientForceCalculator asOverlapGradientForceCalculator() {
        OverlapGradientForceCalculator force = new OverlapGradientForceCalculator(shape, null);
        force.overlapGradientForceX = forceActingOnShapeX;
        force.overlapGradientForceY = forceActingOnShapeY;
        force.overlapGradientTorqueOnFirstShape = torqueActingOnShape;
        // invert the formula for converting to force and torque
        force.overlapArea = 1;
        force.overlapPerimeter = 1;
        return force;
    }
}
class GameForceField {
    /**
     * For the strength attribute of pushAwayForceField() etc.
     */
    public static final double PREFERRED_STRENGTH = 0.01;

    GameShape affectedArea;
    BiFunction<GameShape, OverlapAreaIntegralCalculator, ForceAndTorque> forceFunction;
    public GameForceField(GameShape affectedArea, BiFunction<GameShape, OverlapAreaIntegralCalculator, ForceAndTorque> forceFunction) {
        this.affectedArea = affectedArea;
        this.forceFunction = forceFunction;
    }
    public ForceAndTorque apply(GameShape shape) {
        if (affectedArea != null) {
            OverlapAreaIntegralCalculator calculator = new OverlapAreaIntegralCalculator(shape, affectedArea);
            shape.collision(affectedArea, true, false, true, calculator);
            if (!calculator.isAlmostZero()) {
                return forceFunction.apply(shape, calculator);
            }
        }
        return new ForceAndTorque(0, 0, 0, 0, shape);
    }
    public static GameForceField pushAwayForceField(GameShape affectedArea, double centerX, double centerY, double strength) {
        return new GameForceField(affectedArea, (shape, overlapAreaIntegralCalculator) -> {
            double xDisplacement = overlapAreaIntegralCalculator.overlapXAreaIntegral / overlapAreaIntegralCalculator.overlapArea - centerX;
            double yDisplacement = overlapAreaIntegralCalculator.overlapYAreaIntegral / overlapAreaIntegralCalculator.overlapArea - centerY;
            double resultingForce = strength * overlapAreaIntegralCalculator.overlapArea / shape.getArea();
            double distance = Math.sqrt(xDisplacement*xDisplacement + yDisplacement*yDisplacement);
            if (distance != 0) { // distance might equal zero if the force field is inside
                return new ForceAndTorque(centerX, centerY, resultingForce * xDisplacement / distance, resultingForce * yDisplacement / distance, shape);
            } else {
                return new ForceAndTorque(0, 0, 0, 0, shape);
            }
        });
    }
    public static GameForceField simplePushAwayForceField(GameShape affectedArea, double strength) {
        GameForceField newForceField = new GameForceField(affectedArea, null);
        //we have to have the force function reference the force field so have to make it after
        newForceField.forceFunction = (shape, overlapAreaIntegralCalculator) -> {
            double xDisplacement = overlapAreaIntegralCalculator.overlapXAreaIntegral / overlapAreaIntegralCalculator.overlapArea - newForceField.affectedArea.getX();
            double yDisplacement = overlapAreaIntegralCalculator.overlapYAreaIntegral / overlapAreaIntegralCalculator.overlapArea - newForceField.affectedArea.getY();
            double resultingForce = strength * overlapAreaIntegralCalculator.overlapArea;
            double distance = Math.sqrt(xDisplacement * xDisplacement + yDisplacement * yDisplacement);
            if (distance != 0) { // distance might equal zero if the force field is inside
                return new ForceAndTorque(newForceField.affectedArea.getX(), newForceField.affectedArea.getY(), resultingForce * xDisplacement / distance, resultingForce * yDisplacement / distance, shape);
            } else {
                return new ForceAndTorque(0, 0, 0, 0, shape);
            }
        };
        return newForceField;
    }
}