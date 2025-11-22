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
        // 设置主界面布局（游戏准备界面）
        setContentView(R.layout.activity_game_prepare);

        setupViews();   // 初始化按钮等控件
        hideSystemUi(); // 进入沉浸式全屏
    }

    // 查找界面上的按钮并绑定点击事件
    private void setupViews() {
        Button startButton = findViewById(R.id.startButton);       // “开始游戏”按钮
        Button settingsButton = findViewById(R.id.settingsButton); // “设置”按钮
        Button aboutButton = findViewById(R.id.aboutButton);       // “关于”按钮

        // 点击“开始游戏”，跳转到预启动界面（选择图片和难度）
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GamePreLaunchActivity.class);
            startActivity(intent);
        });

        // 点击“设置”，弹出设置对话框
        settingsButton.setOnClickListener(v -> {
            showSettingsDialog();
        });

        // 点击“关于”，弹出关于信息对话框
        aboutButton.setOnClickListener(v -> {
            showAboutDialog();
        });
    }

    // 显示简单的设置对话框（占位实现）
    private void showSettingsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("设置")
                .setMessage("游戏设置功能")
                .setPositiveButton("确定", null)
                .show();
    }

    // 显示“关于”对话框，展示版本和开发信息
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

        // 窗口重新获取焦点时再次隐藏系统栏，保持沉浸式
        if (hasFocus) {
            hideSystemUi();
        }
    }

    // 设置系统 UI 为沉浸式全屏，隐藏状态栏和导航栏
    private void hideSystemUi() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY             // 沉浸式，手势呼出后自动隐藏
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE      // 布局稳定
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // 内容延伸到导航栏下
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN  // 内容延伸到状态栏下
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION    // 隐藏导航栏
                        | View.SYSTEM_UI_FLAG_FULLSCREEN         // 隐藏状态栏
        );
    }
}
