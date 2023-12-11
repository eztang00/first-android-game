package com.github.eztang00.firstandroidgame.gamelogic;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ListIterator;

/**
 * Animates the final level of the game with fireworks
 */
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
        projectionDistance = 5 * Math.max(width, height);
        this.fadeDuration = fadeDuration;
        fadeStart = Long.MIN_VALUE;
    }

    public void updateAnimation() {
        if (Math.random() < 1.0 / AVERAGE_DURATION_BETWEEN_FIREWORKS_FRAMES) {
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
        if (now >= fadeStart + fadeDuration) {
            colorsGradient = new int[]{
                    Color.rgb(0, 0, 0),
                    Color.rgb(0, 0, 0x40)};
        } else {
            double fadeProgress = (now - fadeStart) / (double) fadeDuration;
            int fadeToZeroValues = GameShapeDrawer.weightedAverage(0xFF, 0, fadeProgress);
            int fadeToDarkValues = GameShapeDrawer.weightedAverage(0xFF, 0x40, fadeProgress);
            colorsGradient = new int[]{
                    Color.rgb(fadeToZeroValues, fadeToZeroValues, fadeToZeroValues),
                    Color.rgb(fadeToZeroValues, fadeToZeroValues, fadeToDarkValues)};
        }
        float[] stopsGradient = new float[]{0, 1};
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

            x = INITIAL_POSITION_X * width * (2 * Math.random() - 1);
            y = INITIAL_POSITION_Y * width * (2 * Math.random());
            z = 0;
            this.color = Color.rgb(randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION), randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION), randomizeByte(0xFF, FIREWORK_COLOUR_VARIATION));
            velocityX = width * INITIAL_VELOCITY_X * (2 * Math.random() - 1);
            velocityY = width * INITIAL_VELOCITY_Y * (2 * Math.random() - 1);
            velocityZ = height * INITIAL_VELOCITY_Z;
            framesBeforeExploding = GameShapeDrawer.weightedAverage(MIN_FRAMES_BEFORE_EXPLODING, MAX_FRAMES_BEFORE_EXPLODING, Math.random());
            framesBeforeFading = Integer.MAX_VALUE;

            explosionVelocity = shorterDimension * EXPLOSION_VELOCITY;
            gravity = height * GRAVITY;
        }

        public Firework(double x, double y, double z, int color, double gravity, double explosionVelocity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = Color.rgb(randomizeByte(Color.red(color), FIREWORK_FRAGMENT_COLOUR_VARIATION), randomizeByte(Color.green(color), FIREWORK_FRAGMENT_COLOUR_VARIATION), randomizeByte(Color.blue(color), FIREWORK_FRAGMENT_COLOUR_VARIATION));
            this.gravity = gravity;
            this.explosionVelocity = explosionVelocity;
            //yeah I know the space of velocities is a square instead of a circle, who cares
            velocityX = explosionVelocity * (2 * Math.random() - 1);
            velocityY = explosionVelocity * (2 * Math.random() - 1);
            velocityZ = explosionVelocity * (2 * Math.random() - 1);
            framesBeforeExploding = Integer.MAX_VALUE;
            framesBeforeFading = FRAMES_BEFORE_FADING;
        }

        public int randomizeByte(int byteToRandomize, int randomness) {
            byteToRandomize = (int) (byteToRandomize + randomness * (2 * Math.random() - 1));
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
                for (int i = 0; i < NUMBER_OF_FIREWORK_FRAGMENTS; i++) {
                    fireworkListIterator.add(new Firework(x, y, z, color, gravity, explosionVelocity));
                }
            }
        }

        public void draw(Canvas canvas, int projectionDistance) {
            double distanceScaling = ((double) projectionDistance) / (y + projectionDistance);
            double xProjected = canvas.getWidth() / 2 + x * distanceScaling;
            double yProjected = canvas.getHeight() - z * distanceScaling;
            int shorterDimension = Math.min(width, height);
            if (framesBeforeFading < FRAMES_DIMMING_BEFORE_FADING) {
                int transparentColor = Color.argb(0xFF * framesBeforeFading / FRAMES_DIMMING_BEFORE_FADING, Color.red(color), Color.green(color), Color.blue(color));
                GameFadeableText.drawText("Victory", xProjected, yProjected, width, shorterDimension * distanceScaling * TEXT_SIZE, transparentColor, canvas);
            } else {
                GameFadeableText.drawText("Victory", xProjected, yProjected, width, shorterDimension * distanceScaling * TEXT_SIZE, color, canvas);
            }
        }

        public boolean willExplodeInScreen() {
            double xFinal = x + velocityX * framesBeforeExploding;
            double yFinal = y + velocityY * framesBeforeExploding;
            double zFinal = z + velocityZ * framesBeforeExploding - gravity * framesBeforeExploding * (1.0 + framesBeforeExploding) / 2.0;

            double distanceScaling = ((double) projectionDistance) / (yFinal + projectionDistance);
            double xProjected = width / 2.0 + xFinal * distanceScaling;
            double yProjected = height - zFinal * distanceScaling;
            return xProjected >= 0 && xProjected < width && yProjected >= 0 && yProjected <= height;
        }
    }
}
