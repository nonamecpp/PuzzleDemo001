package com.example.puzzledemo001;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class GamePlayActivity extends AppCompatActivity implements PuzzlePieceAdapter.OnPieceClickListener, View.OnDragListener {

    private int difficulty;
    private GridLayout puzzleBoard;
    private RecyclerView piecesRecyclerView;
    private Toolbar gameToolbar;
    private LinearLayout completionLayout;
    private TextView totalTimeText;

    private PuzzlePieceAdapter pieceAdapter;
    private List<PuzzlePiece> puzzlePieces;
    private int correctPiecesCount = 0;

    private long startTime;
    private Handler timerHandler = new Handler(Looper.getMainLooper());

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "1. 进入关卡", Toast.LENGTH_SHORT).show();
        setContentView(R.layout.activity_game_play);

        Toast.makeText(this, "2. 视图已加载", Toast.LENGTH_SHORT).show();
        initializeViews();

        Toast.makeText(this, "3. 视图已初始化", Toast.LENGTH_SHORT).show();
        setSupportActionBar(gameToolbar);

        difficulty = getIntent().getIntExtra("difficulty", 3);

        Toast.makeText(this, "4. 正在设置拼图板", Toast.LENGTH_SHORT).show();
        setupPuzzleBoard();

        Toast.makeText(this, "5. 正在设置拼图块列表", Toast.LENGTH_SHORT).show();
        piecesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        Toast.makeText(this, "6. 游戏即将开始", Toast.LENGTH_SHORT).show();
        startGame();
        
        Toast.makeText(this, "7. 关卡加载完成", Toast.LENGTH_SHORT).show();
    }

    private void initializeViews() {
        gameToolbar = findViewById(R.id.gameToolbar);
        puzzleBoard = findViewById(R.id.puzzleBoard);
        piecesRecyclerView = findViewById(R.id.piecesRecyclerView);
        completionLayout = findViewById(R.id.completionLayout);
        totalTimeText = findViewById(R.id.totalTimeText);
    }

    private void setupPuzzleBoard() {
        puzzleBoard.setColumnCount(difficulty);
        puzzleBoard.setRowCount(difficulty);

        int totalCells = difficulty * difficulty;
        for (int i = 0; i < totalCells; i++) {
            ImageView cell = new ImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(i % difficulty, 1, 1f);
            params.rowSpec = GridLayout.spec(i / difficulty, 1, 1f);
            params.setMargins(2, 2, 2, 2);
            cell.setLayoutParams(params);
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            cell.setTag(i); // Tag the cell with its correct index
            cell.setOnDragListener(this);
            puzzleBoard.addView(cell);
        }
    }

    private void startGame() {
        puzzlePieces = ImageSplitter.splitImage(this, R.drawable.default_puzzle_image, difficulty);

        pieceAdapter = new PuzzlePieceAdapter(puzzlePieces);
        pieceAdapter.setClickListener(this);
        piecesRecyclerView.setAdapter(pieceAdapter);

        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);

        Toast.makeText(this, "游戏开始!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPieceLongClick(View view, int position) {
        PuzzlePiece piece = pieceAdapter.getPiece(position);
        ClipData.Item item = new ClipData.Item(Integer.toString(position));
        ClipData dragData = new ClipData("puzzle_piece", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);

        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(dragData, myShadow, view, 0);

        view.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        ImageView targetCell = (ImageView) v;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

            case DragEvent.ACTION_DRAG_ENTERED:
                targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                return true;

            case DragEvent.ACTION_DROP:
                ClipData.Item item = event.getClipData().getItemAt(0);
                int position = Integer.parseInt(item.getText().toString());
                PuzzlePiece draggedPiece = pieceAdapter.getPiece(position);

                int targetIndex = (int) targetCell.getTag();

                if (draggedPiece.getOriginalIndex() == targetIndex) {
                    targetCell.setImageBitmap(draggedPiece.getPieceBitmap());
                    targetCell.setBackground(null);
                    targetCell.setOnDragListener(null);

                    pieceAdapter.removePiece(position);

                    correctPiecesCount++;
                    if (correctPiecesCount == difficulty * difficulty) {
                        endGame();
                    }

                    return true;
                } else {
                    Toast.makeText(this, "位置不对哦!", Toast.LENGTH_SHORT).show();
                    return false;
                }

            case DragEvent.ACTION_DRAG_ENDED:
                targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                View draggedView = (View) event.getLocalState();
                if (!event.getResult()) {
                    draggedView.setVisibility(View.VISIBLE);
                }
                return true;

            default:
                return false;
        }
    }

    private void endGame() {
        timerHandler.removeCallbacks(timerRunnable);
        long millis = System.currentTimeMillis() - startTime;
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;

        totalTimeText.setText(String.format(Locale.getDefault(), "总用时: %02d:%02d", minutes, seconds));
        completionLayout.setVisibility(View.VISIBLE);
        Toast.makeText(this, "恭喜你，完成了拼图！", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_undo) {
            Toast.makeText(this, "撤销操作待实现", Toast.LENGTH_SHORT).show();
            return true;
        } else if (itemId == R.id.action_solve) {
            Toast.makeText(this, "自动完成待实现", Toast.LENGTH_SHORT).show();
            return true;
        } else if (itemId == R.id.action_hint) {
            Toast.makeText(this, "提示功能待实现", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }
}
