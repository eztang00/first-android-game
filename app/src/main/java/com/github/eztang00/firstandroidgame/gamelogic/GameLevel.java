package com.github.eztang00.firstandroidgame.gamelogic;

import android.content.Context;

public interface GameLevel<T extends Game> {

    void initiateLevel(Context context, T game, int level);
}
