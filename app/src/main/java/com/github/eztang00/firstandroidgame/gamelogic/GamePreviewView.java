package com.github.eztang00.firstandroidgame.gamelogic;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.github.eztang00.firstandroidgame.R;
import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGame;

/**
 * A View for showing a snapshot of a game level, without actually
 * running the game like GameView does
 */
public class GamePreviewView extends View {

    public RippleGolfGame game;

    public GamePreviewView(Context context) {
        super(context);
        initInConstructor(context, null);
    }

    public GamePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initInConstructor(context, attrs);
    }

    public GamePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initInConstructor(context, attrs);
    }

    public GamePreviewView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initInConstructor(context, attrs);
    }

    private void initInConstructor(Context context, AttributeSet attrs) {
        setFocusable(true);
        game = new RippleGolfGame(true);
        game.gameState = RippleGolfGame.GameState.PREVIEW_LEVEL;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.GamePreviewView,
                0, 0);

        try {
            game.level = a.getInteger(R.styleable.GamePreviewView_gameLevel, 1);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldW, int oldH) {
        game.setSize(getContext(), w, h);
    }

    public void setGameLevel(int level) {
        game.initiateLevel(getContext(), level, false);
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas != null) {
            super.draw(canvas);
            Log.i("me", "draw function called");
            game.draw(canvas);
        }
    }
}
