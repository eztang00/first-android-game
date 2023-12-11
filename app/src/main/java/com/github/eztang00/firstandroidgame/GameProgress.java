package com.github.eztang00.firstandroidgame;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.databinding.ObservableArrayMap;
import androidx.databinding.ObservableMap;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.github.eztang00.firstandroidgame.ui.game.RippleGolfGame;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GameProgress {
    public final ObservableArrayMap<Integer, Integer> levelHighScores;
    private final MutableLiveData<GameProgress> observerHandler = new MutableLiveData<>(this);
    public GameProgress() {
        levelHighScores = new ObservableArrayMap<>();
        // can't use lambda because ObservableMap.OnMapChangedCallback not interface
        levelHighScores.addOnMapChangedCallback(new ObservableMap.OnMapChangedCallback<ObservableMap<Integer, Integer>, Integer, Integer>() {
            @Override
            public void onMapChanged(ObservableMap<Integer, Integer> sender, Integer key) {
                notifyChange();
            }
        });
    }
    public GameProgress(SerializableGameProgress serializableGameProgress) {
        this();
        levelHighScores.putAll(serializableGameProgress.levelHighScores);
    }

    public void become(GameProgress target) {
        levelHighScores.putAll((SimpleArrayMap<Integer, Integer>) target.levelHighScores);
    }
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<GameProgress> observer) {
        observerHandler.observe(owner, observer);
    }
    public void notifyChange() {
        observerHandler.postValue(this);
    }

    public int getScore() {
        int score = 0;
        for (Map.Entry<Integer, Integer> levelAndHighScore : levelHighScores.entrySet()) {
            score += levelAndHighScore.getValue() - RippleGolfGame.getPar(levelAndHighScore.getKey());
        }
        return score;
    }
}
class SerializableGameProgress implements Serializable {
    public final HashMap<Integer, Integer> levelHighScores;
    public SerializableGameProgress() {
        levelHighScores = new HashMap<>();
    }
    public SerializableGameProgress(GameProgress gameProgress) {
        this();
        levelHighScores.putAll(gameProgress.levelHighScores);
    }
}
