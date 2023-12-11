package com.github.eztang00.firstandroidgame.ui.game;

public interface GameListener {

    void onLevelComplete(int completedLevel, int strokesToWinPrevLevel);
    void onStrokesAndParChange(int strokes, int par);
}
