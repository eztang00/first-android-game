package com.github.eztang00.firstandroidgame.ui.game;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.eztang00.firstandroidgame.databinding.FragmentGameBinding;

public class GameFragment extends Fragment {

private FragmentGameBinding binding;
private MutableLiveData<RippleGolfGame> gameModel;
    private GameViewModel gameViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        gameViewModel =
                new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        binding = FragmentGameBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        gameModel = gameViewModel.getGame();
        Observer<RippleGolfGame> gameCreatedObserver = game -> {
            binding.gameView.start(game);
            gameViewModel.setIsGameRunning(true);
        };
        gameModel.observe(getViewLifecycleOwner(), gameCreatedObserver);
        RippleGolfGame game = gameModel.getValue();
        if (game != null) {
            gameModel.removeObserver(gameCreatedObserver);
            binding.gameView.start(game);
            gameViewModel.setIsGameRunning(true);
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        gameViewModel.setIsGameRunning(false);
//        not needed because of lifecycle owner thing
//        if (gameModel != null && gameCreatedObserver != null) {
//            gameModel.removeObserver(gameCreatedObserver);
//        }
        super.onDestroyView();
        binding = null;
    }
}