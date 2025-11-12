package com.example.puzzledemo001;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_prepare);

        setupViews();
        hideSystemUi();
    }

    private void setupViews() {
        Button startButton = findViewById(R.id.startButton);
        Button settingsButton = findViewById(R.id.settingsButton);
        Button aboutButton = findViewById(R.id.aboutButton);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GamePreLaunchActivity.class);
            startActivity(intent);
        });

        settingsButton.setOnClickListener(v -> {
            showSettingsDialog();
        });

        aboutButton.setOnClickListener(v -> {
            showAboutDialog();
        });
    }

    private void showSettingsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("设置")
                .setMessage("游戏设置功能")
                .setPositiveButton("确定", null)
                .show();
    }

    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("拼图游戏 v1.0\n\n开发信息：\n使用Android Studio开发\n基于Java语言\nMaterial Design设计")
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}