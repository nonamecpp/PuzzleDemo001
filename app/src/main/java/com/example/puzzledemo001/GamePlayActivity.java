package com.example.puzzledemo001;

import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
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

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Locale;

public class GamePlayActivity extends AppCompatActivity implements PuzzlePieceAdapter.OnPieceClickListener, View.OnDragListener {

    private Random random = new Random();
    private int[] dx = {0, 0, 1, -1};
    private int[] dy = {1, -1, 0, 0};
    private int difficulty;
    private String imageUriString; // To receive the image URI from the previous activity

    private GridLayout puzzleBoard;
    private RecyclerView piecesRecyclerView;
    private Toolbar gameToolbar;
    private LinearLayout completionLayout;
    private TextView totalTimeText;

    private PuzzlePieceAdapter pieceAdapter;
    private List<PuzzlePiece> puzzlePieces;
    private List<PuzzlePiece> puzzlePiecesDone;
    private int[][] puzzlePiecesDoneIndex;
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
        setContentView(R.layout.activity_game_play);

        initializeViews();
        setSupportActionBar(gameToolbar);

        // Receive data from Intent
        difficulty = getIntent().getIntExtra("difficulty", 3);
        imageUriString = getIntent().getStringExtra("imageUri");
        puzzlePiecesDoneIndex = new int[difficulty][difficulty];
        setupPuzzleBoard();
        piecesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        startGame();
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
            puzzlePiecesDoneIndex[i/difficulty][i%difficulty] = -1;
        }
    }

    private void startGame() {
        // Decide which image to use
        if (imageUriString != null) {
            puzzlePieces = ImageSplitter.splitImage(this, Uri.parse(imageUriString), difficulty);
        } else {
            puzzlePieces = ImageSplitter.splitImage(this, R.drawable.default_puzzle_image, difficulty);
        }

        // Check if splitting failed (e.g., invalid URI) and fall back to default
        if (puzzlePieces == null || puzzlePieces.isEmpty()) {
            Toast.makeText(this, "图片加载失败，使用默认图片", Toast.LENGTH_LONG).show();
            puzzlePieces = ImageSplitter.splitImage(this, R.drawable.default_puzzle_image, difficulty);
            if (puzzlePieces == null) { // If default also fails, show error and exit
                Toast.makeText(this, "发生致命错误，无法加载游戏", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        // Shuffle the pieces before displaying them
        Collections.shuffle(puzzlePieces);

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

                targetCell.setImageBitmap(draggedPiece.getPieceBitmap());
                targetCell.setBackground(null);
                targetCell.setOnDragListener(null);

                pieceAdapter.removePiece(position);

                if (draggedPiece.getOriginalIndex() == targetIndex)
                    correctPiecesCount++;
                if (correctPiecesCount == difficulty * difficulty) {
                    endGame();
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
    public void MovePiece(PuzzlePiece piece, int targetIndex) {
        piece.setCurrentIndex(targetIndex);
        puzzlePiecesDone.add(piece);
        puzzlePiecesDoneIndex[targetIndex/difficulty][targetIndex%difficulty] = piece.getOriginalIndex();
    }
    public void undoPiece(PuzzlePiece piece, int originalIndex){
        piece.setCurrentIndex(-1);
        puzzlePieces.add(piece);
        puzzlePiecesDoneIndex[originalIndex/difficulty][originalIndex%difficulty] = -1;
    }
    public pieceTip getTip() {
        List<Integer>pobChoice = Collections.emptyList();
        for(int i = 0; i < puzzlePiecesDone.size(); i++){
            PuzzlePiece piece = puzzlePiecesDone.get(i);
            if(!piece.isCorrect()){
                return new pieceTip(false, i, piece.getOriginalIndex());
            }
        }
        for(int i =0, k = 0;i<difficulty;i++){
            for(int j=0;j<difficulty;j++,k++){
                if(puzzlePiecesDoneIndex[i][j]!=k){
                    for(int tmp = 0;tmp<4;tmp++){
                        int nx = dx[tmp]+i;
                        int ny = dy[tmp]+j;
                        if(nx>=0&&nx<difficulty&&ny>=0&&ny<difficulty){
                            if(puzzlePiecesDoneIndex[nx][ny]==k+dx[tmp]+dy[tmp]){
                                pobChoice.add(new Integer(k));
                            }
                        }
                    }
                }
            }
        }
        int optPosition = pobChoice.get(random.nextInt(pobChoice.size()));
        for(int i = 0; i < puzzlePieces.size(); i++){
            PuzzlePiece piece = puzzlePieces.get(i);
            if(piece.getOriginalIndex() == optPosition){
                return new pieceTip(true, i, optPosition);
            }
        }
        return null;
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
