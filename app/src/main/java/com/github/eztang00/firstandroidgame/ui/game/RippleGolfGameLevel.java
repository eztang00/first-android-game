package com.github.eztang00.firstandroidgame.ui.game;

import android.content.Context;

interface GameLevel <T extends Game> {

    void initiateLevel(Context context, T game, int level);
}
public interface RippleGolfGameLevel extends GameLevel<RippleGolfGame> {

    void initiateLevel(Context context, RippleGolfGame game, int levelNumberToDisplay);

    int getPar();

    int getPerfectPar();

    static boolean existsGameLevel(int level) {
        return !(getGameLevel(level) instanceof GameLevelWon);
    }

    static RippleGolfGameLevel getGameLevel(int level) {
        switch (level) {
            case 1:
                return RippleGolfGameLevel1.getInstance();
            case 2:
                return RippleGolfGameLevel2.getInstance();
            case 3:
                return RippleGolfGameLevel3.getInstance();
            case 4:
                return RippleGolfGameLevel4.getInstance();
            case 5:
                return RippleGolfGameLevel5.getInstance();
            case 6:
                return RippleGolfGameLevel6.getInstance();
            case 7:
                return RippleGolfGameLevel7.getInstance();
            case 8:
                return RippleGolfGameLevel8.getInstance();
            case 9:
                return RippleGolfGameLevel8Generator.getInstance();
            default:
                return GameLevelWon.getInstance();
//                throw new IllegalArgumentException("level doesn't exist: " + level);
        }
    }

}

interface RippleGolfGameLevelWithSpeedFactor extends RippleGolfGameLevel {
    void multiplySpeedFactor(double speedFactor);
}
