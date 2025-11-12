package com.example.puzzledemo001;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;

public class GamePreLaunchActivity extends AppCompatActivity {

    private RadioGroup difficultyRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_pre_launch);

        difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        ImageView previewImageView = findViewById(R.id.previewImageView);
        // TODO: You can set a default puzzle image here if you want
        // previewImageView.setImageResource(R.drawable.default_puzzle_image);

        Button startGameButton = findViewById(R.id.startGameButton);
        startGameButton.setOnClickListener(v -> {
            int selectedDifficulty = getSelectedDifficulty();
            Intent intent = new Intent(GamePreLaunchActivity.this, GamePlayActivity.class);
            intent.putExtra("difficulty", selectedDifficulty);
            // Since image selection is removed, we might pass a default image identifier or nothing
            startActivity(intent);
        });
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
            return 3; // Default to easy
        }
    }
}
