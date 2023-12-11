package com.github.eztang00.firstandroidgame.ui.home;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Space;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.eztang00.firstandroidgame.GameProgress;
import com.github.eztang00.firstandroidgame.R;
import com.github.eztang00.firstandroidgame.databinding.FragmentHomeBinding;
import com.github.eztang00.firstandroidgame.databinding.LevelPreviewWithTitleAndInfoBinding;
import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGame;
import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGameLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Currently just shows a list of levels and lets the user click them
 */
public class HomeFragment extends Fragment {

private FragmentHomeBinding binding;
private HashMap<Integer, LevelPreviewWithTitleAndInfoBinding> levelPreviews; //hash map so index starts at 1 instead of 0

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        ViewModelProvider viewModelProvider = new ViewModelProvider(requireActivity());
        HomeViewModel homeViewModel = viewModelProvider.get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        GameProgress progress = homeViewModel.getProgress();

        levelPreviews = new HashMap<>();

        int firstLevelPreviewOfRowId = 0;
        int cornerSquareId = 0;
        for (int level = 1; RippleGolfGameLevel.existsGameLevel(level); level++) {
            int position = level-1;

            // cannot use inflate method with parent parameter
            // since that somehow makes it hard to change the dimensions or constraints
            // despite using constraintSet.clear and setLayoutParams
            @NonNull LevelPreviewWithTitleAndInfoBinding levelPreview = LevelPreviewWithTitleAndInfoBinding.inflate(getLayoutInflater());
            levelPreviews.put(level, levelPreview);

            int levelPreviewId = View.generateViewId();
            levelPreview.getRoot().setId(levelPreviewId);

            int guidelineLeftId;
            int guidelineRightId;

            if (position % 3 == 0) {
                cornerSquareId = addCornerSquare(binding.constraintLayoutLevelsGrid, position==0, firstLevelPreviewOfRowId);
                firstLevelPreviewOfRowId = levelPreviewId;

                guidelineLeftId = R.id.guideline_column_1_left;
                guidelineRightId = R.id.guideline_column_1_right;
            } else if (position % 3 == 1) {
                guidelineLeftId = R.id.guideline_column_2_left;
                guidelineRightId = R.id.guideline_column_2_right;
            } else {
                guidelineLeftId = R.id.guideline_column_3_left;
                guidelineRightId = R.id.guideline_column_3_right;
            }

            levelPreview.getRoot().setLayoutParams(new ConstraintLayout.LayoutParams(
                    0, ConstraintLayout.LayoutParams.WRAP_CONTENT));

            binding.constraintLayoutLevelsGrid.addView(levelPreview.getRoot());

            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(binding.constraintLayoutLevelsGrid);

            constraintSet.connect(levelPreviewId, ConstraintSet.TOP, cornerSquareId, ConstraintSet.BOTTOM, 0);
            constraintSet.connect(levelPreviewId, ConstraintSet.START, guidelineLeftId, ConstraintSet.START, 0);
            constraintSet.connect(levelPreviewId, ConstraintSet.END, guidelineRightId, ConstraintSet.START, 0);

            constraintSet.applyTo(binding.constraintLayoutLevelsGrid);


            levelPreview.previewLevelPicture.setGameLevel(level);
            levelPreview.text1.setText(getString(R.string.level_preview_title, level));

            Integer strokes = progress.levelHighScores.getOrDefault(level, Integer.MAX_VALUE);
            if (strokes != null && strokes != Integer.MAX_VALUE) {
                setLevelPreviewAsWon(levelPreview, strokes);
            }
            levelPreview.text3.setText(getString(R.string.par_text, RippleGolfGame.getPar(level)));

            int levelToSelect = level;
            levelPreview.getRoot().setOnClickListener(view -> homeViewModel.selectItem(levelToSelect));
        }

        progress.observe(getViewLifecycleOwner(), gameProgress -> {
            for (Map.Entry<Integer, LevelPreviewWithTitleAndInfoBinding> levelPreviewEntry : levelPreviews.entrySet()) {
                Integer strokes = gameProgress.levelHighScores.getOrDefault(levelPreviewEntry.getKey(), Integer.MAX_VALUE);
                if (strokes != null && strokes != Integer.MAX_VALUE) {
                    setLevelPreviewAsWon(levelPreviewEntry.getValue(), strokes);
                } else {
                    setLevelPreviewAsLost(levelPreviewEntry.getValue());
                }
            }
        });

        return root;
    }
    private void setLevelPreviewAsWon(LevelPreviewWithTitleAndInfoBinding levelPreview, int strokes) {
        int par = RippleGolfGame.getPar(levelPreview.previewLevelPicture.game.level);
        int perfectPar = RippleGolfGame.getPerfectPar(levelPreview.previewLevelPicture.game.level);
        if (strokes == 1) {
            levelPreview.text2.setText(getString(R.string.complete_level_text_perfect_1_stroke));
        } else if (strokes <= perfectPar) {
            levelPreview.text2.setText(getString(R.string.complete_level_text_perfect, strokes));
        } else {
            levelPreview.text2.setText(getString(R.string.complete_level_text, strokes));
        }
        levelPreview.text2.setTextColor(Color.GREEN);
//        if (strokes <= perfectPar) {
//            levelPreview.text3.setTextColor(Color.GREEN);
//            levelPreview.text2.setTypeface(null, Typeface.BOLD);
//            levelPreview.text3.setTypeface(null, Typeface.BOLD);
//        } else
        if (strokes <= par) {
            levelPreview.text3.setTextColor(Color.GREEN);
            levelPreview.text2.setTypeface(null, Typeface.BOLD);
            levelPreview.text3.setTypeface(null, Typeface.BOLD);
        } else {
            levelPreview.text3.setTextColor(Color.GRAY);
            levelPreview.text2.setTypeface(null, Typeface.NORMAL);
            levelPreview.text3.setTypeface(null, Typeface.NORMAL);
        }
    }
    private void setLevelPreviewAsLost(LevelPreviewWithTitleAndInfoBinding levelPreview) {
        levelPreview.text2.setText(getString(R.string.incomplete_level_text));
        levelPreview.text2.setTextColor(Color.GRAY);
        levelPreview.text3.setTextColor(Color.GRAY);
    }
    private int addCornerSquare(ConstraintLayout constraintLayoutLevelsGrid, boolean firstCorner, int firstLevelPreviewOfRowId) {
        Space cornerSquare = new Space(getContext());
        int cornerSquareId = View.generateViewId();
        cornerSquare.setId(cornerSquareId);
        cornerSquare.setLayoutParams(new ConstraintLayout.LayoutParams(0, 0));
        constraintLayoutLevelsGrid.addView(cornerSquare);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayoutLevelsGrid);
        constraintSet.connect(cornerSquareId, ConstraintSet.START, constraintLayoutLevelsGrid.getId(), ConstraintSet.START);
        constraintSet.connect(cornerSquareId, ConstraintSet.END, R.id.guideline_column_1_left, ConstraintSet.START);
        if (firstCorner) {
            constraintSet.connect(cornerSquareId, ConstraintSet.TOP, constraintLayoutLevelsGrid.getId(), ConstraintSet.TOP);
        } else {
            constraintSet.connect(cornerSquareId, ConstraintSet.TOP, firstLevelPreviewOfRowId, ConstraintSet.BOTTOM);
        }
        constraintSet.setDimensionRatio(cornerSquareId, "H,1:1");
        constraintSet.applyTo(constraintLayoutLevelsGrid);
        return cornerSquareId;
    }

@Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }
}