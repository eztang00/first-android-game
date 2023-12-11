package com.github.eztang00.firstandroidgame;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Stores information about progress made in the game, using only serializable objects.
 */
public class SerializableGameProgress implements Serializable {
    public final HashMap<Integer, Integer> levelHighScores;

    public SerializableGameProgress() {
        levelHighScores = new HashMap<>();
    }

    public SerializableGameProgress(GameProgress gameProgress) {
        this();
        levelHighScores.putAll(gameProgress.levelHighScores);
    }
}
