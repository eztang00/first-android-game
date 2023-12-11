package com.github.eztang00.firstandroidgame.gamelogic;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * The thread that runs the game
 * <p>
 * lots of code copied from rushd
 * See https://www.androidauthority.com/android-game-java-785331/
 */
class MainThread extends Thread {
    private static final int TARGET_FPS = 60;
    private static final long TARGET_TIME = 1000 / TARGET_FPS;
    //having the thread at least sometimes sleep prevents blocking other threads
    //without needing to fine tune thread priority
    //if it never sleeps it disables menu buttons
    private static final long MIN_WAIT = 100 / TARGET_FPS;
    private final SurfaceHolder surfaceHolder;
    private final GameView gameView;
    public Context context;
    private boolean running;
    public static Canvas canvas;

    public MainThread(Context context, SurfaceHolder surfaceHolder, GameView gameView) {
        super();
        this.surfaceHolder = surfaceHolder;
        this.gameView = gameView;
        this.context = context;
    }

    public void setRunning(boolean isRunning) {
        running = isRunning;
    }

    @Override
    public void run() {
        long startTime;
        long timeMillis;
        long waitTime;
//        long totalTime = 0;
//        int frameCount = 0;

        Log.i("me", "thread starting");
        while (running) {
            startTime = System.currentTimeMillis();
            canvas = null;

            try {
                canvas = this.surfaceHolder.lockCanvas();
                synchronized (surfaceHolder) {
                    this.gameView.game.update(context);
                    this.gameView.draw(canvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            timeMillis = System.currentTimeMillis() - startTime;
            waitTime = Math.max(TARGET_TIME - timeMillis, MIN_WAIT);

            try {
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
//            totalTime += System.currentTimeMillis() - startTime;
//            frameCount++;
//            if (frameCount == TARGET_FPS)        {
//                float averageFPS = 1000 * frameCount / totalTime;
//                frameCount = 0;
//                totalTime = 0;
//                System.out.println(averageFPS + " averageFPS");
//            }
        }

    }
}
