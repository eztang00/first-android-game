package com.github.eztang00.firstandroidgame.ui.game;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGame;

/**
 * Mostly just stores a reference to the game instance, in a way
 * compatible with view models
 */
public class GameViewModel extends ViewModel {

    private final MutableLiveData<RippleGolfGame> gameLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGameRunning = new MutableLiveData<>(false);

    public MutableLiveData<RippleGolfGame> getGame() {
        return gameLiveData;
    }
    public void setGame(RippleGolfGame gameLiveData) {
        this.gameLiveData.setValue(gameLiveData);
    }

    public MutableLiveData<Boolean> getIsGameRunning() {
        return isGameRunning;
    }
    public void setIsGameRunning(boolean isGameRunning) {
        this.isGameRunning.setValue(isGameRunning);
    }

    public GameViewModel() {
    }
}