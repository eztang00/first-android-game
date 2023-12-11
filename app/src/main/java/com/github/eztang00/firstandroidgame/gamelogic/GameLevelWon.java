package com.github.eztang00.firstandroidgame.gamelogic;

import android.content.Context;
import android.graphics.Color;

import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGame;
import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGameLevel;

/**
 * The final level of the game with fireworks
 */
public class GameLevelWon implements RippleGolfGameLevel {
    static GameLevelWon staticInstance = new GameLevelWon();

    public static GameLevelWon getInstance() {
        return staticInstance;
    }

    private GameLevelWon() {
    }

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
