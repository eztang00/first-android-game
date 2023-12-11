package com.github.eztang00.firstandroidgame;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.github.eztang00.firstandroidgame.databinding.ActivityMainBinding;
import com.github.eztang00.firstandroidgame.ui.game.RippleGolfGame;
import com.github.eztang00.firstandroidgame.ui.game.GameListener;
import com.github.eztang00.firstandroidgame.ui.game.GameViewModel;
import com.github.eztang00.firstandroidgame.ui.home.HomeViewModel;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private Menu menuInToolbar;
    private int preferredOrientation = Configuration.ORIENTATION_UNDEFINED;
private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

     binding = ActivityMainBinding.inflate(getLayoutInflater());
//     setContentView(new GameView(this));
     setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
//        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_game)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);


        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        HomeViewModel homeViewModel = viewModelProvider.get(HomeViewModel.class);
        GameViewModel gameViewModel = viewModelProvider.get(GameViewModel.class);

        //load this before inflating otherwise the menu is created before loaded progress and won't show progress
        GameProgress progress = homeViewModel.getProgress();
        progress.become(SaveAndLoad.gsonLoad(this));

        RippleGolfGame game = new RippleGolfGame(false);
        gameViewModel.setGame(game);

        game.addGameListener(new GameListener() {
            @Override
            public void onLevelComplete(int completedLevel, int strokesToWinPrevLevel) {
                runOnUiThread(() -> {
                    Integer highScoreSoFar = progress.levelHighScores.getOrDefault(completedLevel, Integer.MAX_VALUE);
                    //weird warning requires checking it's not null
                    if (highScoreSoFar == null || strokesToWinPrevLevel < highScoreSoFar) {
//                if (strokesToWinPrevLevel < Objects.requireNonNullElse(progress.levelHighScores.get(prevLevel), Integer.MAX_VALUE)) {
                        progress.levelHighScores.put(completedLevel, strokesToWinPrevLevel);
                        SaveAndLoad.gsonSave(progress, MainActivity.this);
                    }
                    if (menuInToolbar != null) {
                        menuInToolbar.findItem(R.id.strokes_and_par_info).setTitle(left(getString(R.string.strokes_and_par_info, progress.getScore(), game.strokes, RippleGolfGame.getPar(game.level))));
                    }

                });
            }

            @Override
            public void onStrokesAndParChange(int strokes, int par) {
                //https://www.tutorialkart.com/kotlin-android/original-thread-created-view-hierarchy-can-touch-views/#gsc.tab=0
                runOnUiThread(() -> {
                    if (menuInToolbar != null) {
                        menuInToolbar.findItem(R.id.strokes_and_par_info).setTitle(left(getString(R.string.strokes_and_par_info, progress.getScore(), strokes, par)));
                    }
                });
            }
        } );

        homeViewModel.getSelectedItem().observe(this, gameLevel -> {
            View gameNavigationButton = findViewById(R.id.nav_game);
            if (gameNavigationButton != null) { //need to check otherwise somehow null pointer error when rotating screen
                gameNavigationButton.performClick();
            }
//                navController.navigate(R.id.nav_game);

            //initiate level if it's a different level
            //if it's the same level still initiate again if
            //no progress is made (strokes == 0) otherwise
            //looks weird
            if (game.level != gameLevel || game.strokes == 0) {
                game.initiateLevel(this, gameLevel, false);
            }
        });

        gameViewModel.getIsGameRunning().observe(this, isGameRunning -> {
            if (menuInToolbar != null) {
                //https://stackoverflow.com/questions/12894802/how-to-remove-menuitems-from-menu-programmatically
                if (isGameRunning) {
                    menuInToolbar.findItem(R.id.action_restart).setVisible(true);
                    MenuItem strokesAndParInfo = menuInToolbar.findItem(R.id.strokes_and_par_info);
                    strokesAndParInfo.setTitle(left(getString(R.string.strokes_and_par_info, progress.getScore(), game.strokes, RippleGolfGame.getPar(game.level))));
                } else {
                    menuInToolbar.findItem(R.id.action_restart).setVisible(false);
                    menuInToolbar.findItem(R.id.strokes_and_par_info).setTitle(getString(R.string.score_info, progress.getScore()));
                }
                //see https://stackoverflow.com/questions/3611457/android-temporarily-disable-orientation-changes-in-an-activity/3611554#3611554
                if (isGameRunning) {
//                defaultRequestedOrientation = getRequestedOrientation();
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

                    preferredOrientation = getResources().getConfiguration().orientation;

                } else {
//                setRequestedOrientation(defaultRequestedOrientation);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    //somehow have to set it to sensor instead of what it was before locking,
                    //otherwise it stays locked

                    preferredOrientation = Configuration.ORIENTATION_UNDEFINED;
                }
            }
        });
//        navController.addOnDestinationChangedListener(new NavController.OnDestinationChangedListener() {
//            @Override
//            public void onDestinationChanged(@NonNull NavController navController, @NonNull NavDestination navDestination, @Nullable Bundle bundle) {
//
//            }
//        });
        this.addOnConfigurationChangedListener(configuration -> {
            if (preferredOrientation != configuration.orientation) {
                if (preferredOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (preferredOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        menuInToolbar = menu;
        //make restart button only visible if game is running
        MenuItem restartButton = menu.findItem(R.id.action_restart);
        MenuItem strokesAndParInfo = menu.findItem(R.id.strokes_and_par_info);


        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        GameViewModel gameViewModel = viewModelProvider.get(GameViewModel.class);
        boolean isGameRunning = Boolean.TRUE.equals(gameViewModel.getIsGameRunning().getValue());

        restartButton.setVisible(isGameRunning);
        RippleGolfGame game = gameViewModel.getGame().getValue();

        if (isGameRunning) {
            if (game != null) {
                strokesAndParInfo.setTitle(left(getString(R.string.strokes_and_par_info, viewModelProvider.get(HomeViewModel.class).getProgress().getScore(), game.strokes, RippleGolfGame.getPar(game.level))));
            }
        } else {
            strokesAndParInfo.setTitle(getString(R.string.score_info, viewModelProvider.get(HomeViewModel.class).getProgress().getScore()));
        }

        restartButton.setOnMenuItemClickListener(menuItem -> {
            if (game != null) {
                game.initiateLevel(this, game.level, true);
            }
            return true; // returning true consumes click
        });
        return true;
    }
    private static SpannableString left(String s) {
        //https://stackoverflow.com/questions/19344297/align-center-menu-item-text-in-android
        SpannableString spannableString = new SpannableString(s);
        spannableString.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), 0, s.length(), 0);
        return spannableString;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}