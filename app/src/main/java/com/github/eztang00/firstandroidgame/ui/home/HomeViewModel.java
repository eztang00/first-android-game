package com.github.eztang00.firstandroidgame.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.eztang00.firstandroidgame.GameProgress;

/**
 * Stores a copy of the game progress, plus lets the user select a level
 */
public class HomeViewModel extends ViewModel {

    private final GameProgress progress = new GameProgress();
    private final MutableLiveData<Integer> selectedItem = new MutableLiveData<Integer>();

    public void selectItem(int item) {
        selectedItem.setValue(item);
    }
    public LiveData<Integer> getSelectedItem() {
        return selectedItem;
    }
    public GameProgress getProgress() {
        return progress;
    }
    public HomeViewModel() {
    }
}