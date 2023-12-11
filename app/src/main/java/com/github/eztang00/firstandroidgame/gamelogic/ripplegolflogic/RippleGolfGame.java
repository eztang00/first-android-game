package com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;

import com.github.eztang00.firstandroidgame.R;
import com.github.eztang00.firstandroidgame.gamelogic.Game;
import com.github.eztang00.firstandroidgame.gamelogic.GameFadeableText;
import com.github.eztang00.firstandroidgame.gamelogic.GameListener;
import com.github.eztang00.firstandroidgame.gamelogic.gameobstacles.GameMaze;
import com.github.eztang00.firstandroidgame.gamelogic.GameShapeAdditionalAttributesForDrawingEtc;
import com.github.eztang00.firstandroidgame.gamelogic.GameShapeDrawer;
import com.github.eztang00.firstandroidgame.gamelogic.gameobstacles.GameWormhole;
import com.github.eztang00.firstandroidgame.gamephysics.GameForceField;
import com.github.eztang00.firstandroidgame.gamephysics.GamePhysicsEngine;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgon;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgonBuilder;
import com.github.eztang00.firstandroidgame.gamephysics.GameShape;

import java.util.ArrayList;

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

