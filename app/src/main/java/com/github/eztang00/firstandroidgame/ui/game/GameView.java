package com.github.eztang00.firstandroidgame.ui.game;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

/*
 * lots of code copied from rushd
 * See https://www.androidauthority.com/android-game-java-785331/
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private MainThread thread;
    RippleGolfGame game;
    boolean surfaceExists = false;

    public GameView(Context context) {
        super(context);
        initInConstructor();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initInConstructor();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initInConstructor();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initInConstructor();
    }

    private void initInConstructor() {
        getHolder().addCallback(this);
        setFocusable(true);
        game = new RippleGolfGame(false);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        surfaceExists = true;
        if (game != null) {
            startThread();
        }
    }

    private void startThread() {
        thread = new MainThread(getContext(), getHolder(), this);
        thread.setRunning(true);
        thread.start();
    }

    public void start(RippleGolfGame game) {
        this.game = game;
        if (surfaceExists) {
            startThread();
        }
    }
//    private boolean surfaceExists() {
//        SurfaceHolder surfaceHolder = getHolder();
//        Canvas canvas = null;
//        try {
//            canvas = surfaceHolder.lockCanvas();
//            return canvas != null;
////        } catch (Exception e) {
////            e.printStackTrace();
//        } finally {
//            if (canvas != null) {
//                try {
//                    surfaceHolder.unlockCanvasAndPost(canvas);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
////        return false;
//    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        surfaceExists = false;
        // this code was copied from rushd at https://www.androidauthority.com/android-game-java-785331/
        boolean retry = true;
        while (retry) {
            try {
                thread.setRunning(false);
                thread.join();
                thread.context = null;
                thread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            retry = false;
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldW, int oldH) {
        game.setSize(getContext(), w, h);
//        Log.i("me", "size changed: " + w + " " + h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas != null) {
            super.draw(canvas);
            game.draw(canvas);
        }
    }
}
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
                synchronized(surfaceHolder) {
                    this.gameView.game.update(context);
                    this.gameView.draw(canvas);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                    catch (Exception e) {
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