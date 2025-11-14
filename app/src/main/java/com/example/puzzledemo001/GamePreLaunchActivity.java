package com.example.puzzledemo001;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AnyRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class GamePreLaunchActivity extends AppCompatActivity {

    private RadioGroup difficultyRadioGroup;
    private ImageView previewImageView;

    // 存储最终要传递给下一个页面的图片URI
    private Uri imageToUseUri;

    // 推荐使用新的Activity Result API替代startActivityForResult
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    // 获取用户选择的图片的URI
                    Uri selectedImageUri = result.getData().getData();
                    imageToUseUri = selectedImageUri;
                    // 将图片显示在预览的ImageView中
                    previewImageView.setImageURI(selectedImageUri);
                } else {
                    // 用户可能取消了选择，不做任何事
                    Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_pre_launch);

        // 初始化视图
        difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        previewImageView = findViewById(R.id.previewImageView);
        Button importImageButton = findViewById(R.id.importImageButton);
        Button startGameButton = findViewById(R.id.startGameButton);

        // 1. 设置并显示默认图片
        setupDefaultImage();

        // 2. 为“导入图片”按钮设置点击事件
        importImageButton.setOnClickListener(v -> {
            // 使用新的API启动图片选择器
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        // 3. 为“开始游戏”按钮设置点击事件
        startGameButton.setOnClickListener(v -> {
            // 获取难度
            int selectedDifficulty = getSelectedDifficulty();

            // 创建意图并传递数据
            Intent intent = new Intent(GamePreLaunchActivity.this, GamePlayActivity.class);
            intent.putExtra("difficulty", selectedDifficulty);

            // 统一传递Uri，即使是默认图片
            intent.putExtra("imageUri", imageToUseUri.toString());

            startActivity(intent);
        });
    }

    /**
     * 设置默认图片，并初始化要使用的图片URI
     */
    private void setupDefaultImage() {
        // 将默认的drawable资源ID转换为Uri
        imageToUseUri = getUriFromDrawable(this, R.drawable.default_puzzle_image);
        previewImageView.setImageResource(R.drawable.default_puzzle_image);
    }

    /**
     * 将drawable资源ID转换为Uri的辅助方法
     * @param context 上下文
     * @param drawableId drawable资源的ID
     * @return 对应的Uri
     */
    public static Uri getUriFromDrawable(@NonNull Context context, @AnyRes int drawableId) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.getResources().getResourcePackageName(drawableId) +
                '/' + context.getResources().getResourceTypeName(drawableId) +
                '/' + context.getResources().getResourceEntryName(drawableId));
    }


    private int getSelectedDifficulty() {
        int selectedId = difficultyRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.easyRadioButton) {
            return 3; // 3x3 grid
        } else if (selectedId == R.id.mediumRadioButton) {
            return 4; // 4x4 grid
        } else if (selectedId == R.id.hardRadioButton) {
            return 5; // 5x5 grid
        } else {
            // 默认选中简单或第一个选项
            return 3;
        }
    }

    // onActivityResult方法不再需要，因为我们使用了新的Activity Result API
    // @Override
    // protected void onActivityResult(...) { ... }
}
