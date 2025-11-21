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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;

public class GamePlayActivity extends AppCompatActivity implements PuzzlePieceAdapter.OnPieceClickListener, View.OnDragListener {

    private Random random = new Random();
    private int[] dx = {0, 0, 1, -1};
    private int[] dy = {1, -1, 0, 0};
    private int difficulty;
    private String imageUriString;

    private GridLayout puzzleBoard;
    private RecyclerView piecesRecyclerView;
    private Toolbar gameToolbar;
    private LinearLayout completionLayout;
    private TextView totalTimeText;
    private TextView timerText; // For live timer
    private TextView movesText; // For move count

    private PuzzlePieceAdapter pieceAdapter;
    private List<PuzzlePiece> puzzlePieces;
    private PuzzlePiece[] puzzlePiecesDone;
    private int correctPiecesCount = 0;
    private int movesCount = 0; // To count user moves

    // Variables for click-to-place functionality
    private PieceExact selectedPieceExactA = null;
    private PieceExact selectedPieceExactB = null;
    private long startTime;
    private Handler timerHandler = new Handler(Looper.getMainLooper());

    // --- Variables for Undo ---
    private Stack<ComplexMovement> moveHistory = new Stack<>();

    // --- Variables for Animated Solve ---
    private Menu optionsMenu;
    private Handler solveHandler = new Handler(Looper.getMainLooper());
    private List<PuzzlePiece> solvedPiecesForAnimation;
    private int solveAnimationIndex = 0;
    // --- End Variables for Animated Solve ---

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            if (timerText != null) {
                timerText.setText(String.format(Locale.getDefault(), "计时: %02d:%02d", minutes, seconds));
            }
            timerHandler.postDelayed(this, 1000);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_play);

        initializeViews();
        setSupportActionBar(gameToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        difficulty = getIntent().getIntExtra("difficulty", 3);
        imageUriString = getIntent().getStringExtra("imageUri");
        puzzlePiecesDone = new PuzzlePiece[difficulty*difficulty];
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
        timerText = findViewById(R.id.timerText); // Initialize timer text view
        movesText = findViewById(R.id.movesText); // Initialize moves text view
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
            cell.setTag(i);
            cell.setOnDragListener(this);
            cell.setOnClickListener(this::handleCellClick);

            // Add long click listener for swapping pieces on the board
            // Add long click listener for swapping/moving pieces on the board
            cell.setOnLongClickListener(v -> {
                int sourceIndex = (int) v.getTag();
                // 查找当前长按的格子上是否真的有碎片
                PuzzlePiece sourcePiece = findPieceByCurrentIndex(sourceIndex);

                // 新的逻辑：只要这个格子里确实有碎片，就允许启动拖拽
                if (sourcePiece != null) {
                    ClipData.Item item = new ClipData.Item(Integer.toString(sourceIndex));
                    // 使用 "board_piece" 标签来标识这次拖拽源自棋盘
                    ClipData dragData = new ClipData("board_piece", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
                    View.DragShadowBuilder myShadow = new View.DragShadowBuilder(v);
                    v.startDragAndDrop(dragData, myShadow, v, 0);
                    return true;
                }

                // 如果长按的是一个空格子，则不允许拖拽
                return false;
            });


            puzzleBoard.addView(cell);
            puzzlePiecesDone[i] = null;
        }
    }

    private void startGame() {
        if (imageUriString != null) {
            puzzlePieces = ImageSplitter.splitImage(this, Uri.parse(imageUriString), difficulty);
        } else {
            puzzlePieces = ImageSplitter.splitImage(this, R.drawable.puzzle_default, difficulty);
        }

        if (puzzlePieces == null || puzzlePieces.isEmpty()) {
            Toast.makeText(this, "图片加载失败，使用默认图片", Toast.LENGTH_LONG).show();
            puzzlePieces = ImageSplitter.splitImage(this, R.drawable.puzzle_default, difficulty);
            if (puzzlePieces == null) {
                Toast.makeText(this, "发生致命错误，无法加载游戏", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        Collections.shuffle(puzzlePieces);
        moveHistory.clear();

        pieceAdapter = new PuzzlePieceAdapter(puzzlePieces);
        pieceAdapter.setClickListener(this);
        piecesRecyclerView.setAdapter(pieceAdapter);

        // Reset stats and start timer
        movesCount = 0;
        if (movesText != null) {
            movesText.setText("步数: 0");
        }
        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);

        Toast.makeText(this, "游戏开始!", Toast.LENGTH_SHORT).show();
    }
    private void darkenPieceBackground(PieceExact pieceExact){
        View view = getExactpieceView(pieceExact);
        view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
    }
    private int getExactpiecePosition(PieceExact pieceExact){
        if(pieceExact == null)return -1;
        if(pieceExact.getType() == PositionType.PieceInWait){
            return getAdapterPiecePosition(pieceExact.getPostionID());
        }
        return pieceExact.getPostionID();
    }
    private View getExactpieceView(PieceExact  pieceExact){
        if(pieceExact == null)return null;
        View view = null;
        if(pieceExact.getType() == PositionType.PieceInBoard){
            view = getBoardViewByTag(pieceExact.getPostionID());
        }
        if(pieceExact.getType() == PositionType.PieceInWait){
            view = piecesRecyclerView.getChildAt(getAdapterPiecePosition(pieceExact.getPostionID()));
        }
        return view;
    }
    @Override
    public void onPieceClick(View view, int position) {

        PuzzlePiece piece = pieceAdapter.getPiece(position);
        if(selectedPieceExactA != null){
            View v = getBoardViewByTag(getExactpiecePosition(selectedPieceExactA));
            v.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        selectedPieceExactA = new PieceExact(piece.getOriginalIndex(), PositionType.PieceInWait);
        view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));

    }

    private void handleCellClick(View v) {
        ImageView targetCell = (ImageView) v;
        int PositionID = (int) targetCell.getTag();
        PuzzlePiece occupant = findPieceByCurrentIndex(PositionID); // 查找当前格子上是否已有碎片

        if (selectedPieceExactA != null) {
            selectedPieceExactB = new PieceExact(PositionID, PositionType.PieceInBoard);
            darkenPieceBackground(selectedPieceExactA);

            if(!selectedPieceExactB.equal(selectedPieceExactA))
                PerformComplexMove(new PieceMovement(selectedPieceExactA, selectedPieceExactB));
            selectedPieceExactA = selectedPieceExactB = null;

        } else {
            // --- 逻辑分支 2: 手上没有选择任何碎片 (直接点击棋盘) ---
            if(occupant == null)return;
            selectedPieceExactA = new PieceExact(PositionID, PositionType.PieceInBoard);
            v.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        }
    }
    private void incrementMoves() {
        movesCount++;
        if (movesText != null) {
            movesText.setText("步数: " + movesCount);
        }
    }

    private void UpdatePieceBoard(View v, PuzzlePiece piece) {
        // 1. 把 View 强转为 ImageView，这样才能设置图片
        ImageView targetCell = (ImageView) v;

        if (piece == null) {
            // 如果传进来的 piece 是 null，说明要把这个格子清空
            targetCell.setImageDrawable(null); // 清除图片
            targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray)); // 恢复灰色背景
        } else {
            // 如果有 piece，显示它的图片
            targetCell.setImageBitmap(piece.getPieceBitmap());
            targetCell.setBackground(null); // 清除背景色
        }
    }

    private View getBoardViewByTag(int index) {
        return puzzleBoard.findViewWithTag(index);
    }

    private void PushPieceWait(PuzzlePiece piece){
        pieceAdapter.addPiece(piece);
    }
    private void PushPieceBoard(PuzzlePiece piece,int position){
        UpdatePieceBoard(getBoardViewByTag(position), piece);
        puzzlePiecesDone[position]=piece;
    }
    private void DeletePieceWait(PuzzlePiece piece){
        pieceAdapter.removePiece(puzzlePieces.indexOf(piece));
    }
    private void DeletePieceBoard(int position){
        UpdatePieceBoard(getBoardViewByTag(position), null);
        puzzlePiecesDone[position]=null;
    }
    private void PerformMove(PieceMovement movement){
        // warning : ensure target board position is empty
        // suggest : make one MOVE into 2 to 3 basic movement
        PieceExact origin = movement.Origin;
        PieceExact target = movement.Target;
        if(origin.getType() == PositionType.PieceInWait && target.getType() == PositionType.PieceInBoard){
            PuzzlePiece piece = puzzlePieces.get(getAdapterPiecePosition(origin.getPostionID()));
            piece.setCurrentIndex(target.getPostionID());
            DeletePieceWait(piece);
            PushPieceBoard(piece, target.getPostionID());
        }
        else if(origin.getType() == PositionType.PieceInBoard && target.getType() == PositionType.PieceInBoard){
            PuzzlePiece piece = puzzlePiecesDone[origin.getPostionID()];
            piece.setCurrentIndex(target.getPostionID());
            DeletePieceBoard(target.getPostionID());
            PushPieceBoard(piece, target.getPostionID());
        }
        else if(origin.getType() == PositionType.PieceInBoard && target.getType() == PositionType.PieceInWait){
            PuzzlePiece piece = puzzlePiecesDone[origin.getPostionID()];
            piece.setCurrentIndex(-1);
            DeletePieceBoard(origin.getPostionID());
            PushPieceWait(piece);
        }
    }
    private void UndoLastMove(PieceMovement movement){
        movement.setUndo();
        PerformMove(movement);
    }
    private void PerformComplexMove(PieceMovement move) {
        ComplexMovement generalmove = new ComplexMovement();
        PieceMovement movement;
        if(move.Target.getType() == PositionType.PieceInBoard
                && puzzlePiecesDone[move.Target.getPostionID()]!=null){
            movement = new PieceMovement(
                            move.Target,
                            new PieceExact(
                                puzzlePiecesDone[move.Target.getPostionID()].getOriginalIndex(),
                                PositionType.PieceInWait));
            PerformMove(movement);
            generalmove.addMove(movement);
        }
        PerformMove(move);
        generalmove.addMove(move);
        moveHistory.push(generalmove);
        incrementMoves();
        checkCompletion();
    }
    private void undoComplexMove(){
        if(moveHistory.isEmpty())return;
        ComplexMovement move = moveHistory.pop();
        for(PieceMovement movement = move.begin();movement!=null;movement = move.next()) {
            UndoLastMove(movement);
        }
    }
    private void checkCompletion() {
        if (!puzzlePieces.isEmpty()) return; // Don't check until all pieces are on the board

        boolean allCorrect = true;
        for (int i = 0; i < difficulty; i++) {
            for (int j = 0; j < difficulty; j++) {
                if (!puzzlePiecesDone[i*difficulty+j].isCorrect()) {
                    allCorrect = false;
                    break;
                }
            }
            if (!allCorrect) break;
        }

        if (allCorrect) {
            endGame();
        }
    }

    @Override
    public void onPieceLongClick(View view, int position) {
        // Prevent long click from list if board is full
        if(puzzlePieces.isEmpty()) return;

        if(selectedPieceExactA!=null){
            darkenPieceBackground(selectedPieceExactA);
        }
        PuzzlePiece piece = pieceAdapter.getPiece(position);
        selectedPieceExactA = new PieceExact(piece.getOriginalIndex(), PositionType.PieceInWait);

        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(null, myShadow, view, 0);

        view.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        // 将 v 转换为目标单元格，这在大多数事件中都是安全的
        ImageView targetCell = (ImageView) v;

        switch (event.getAction()) {
            case DragEvent.ACTION_DROP:
                int PositionID = (int) targetCell.getTag();
                selectedPieceExactB = new PieceExact(PositionID, PositionType.PieceInBoard);
                if(!selectedPieceExactB.equal(selectedPieceExactA))
                    PerformComplexMove(new PieceMovement(selectedPieceExactA, selectedPieceExactB));
                selectedPieceExactA = selectedPieceExactB = null;
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                View draggedView = (View) event.getLocalState();
                if (!event.getResult()) {
                    if (draggedView != null) {
                        draggedView.setVisibility(View.VISIBLE);
                    }
                }
                return true;

            default:
                return false;
        }
    }
    public void ClearHighLight(){
        for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
            View cell = puzzleBoard.getChildAt(i);
            if (((ImageView) cell).getDrawable() == null) {
                cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            } else {
                cell.setBackground(null);
            }
        }
    }


    private int getAdapterPiecePosition(int originalIndex) {
        for (int i=0; i < puzzlePieces.size(); i++) {
            if (puzzlePieces.get(i).getOriginalIndex() == originalIndex) {
                return i;
            }
        }
        return -1;
    }
    // TODO : wait for adaptor fix
    public PieceMovement getTip() {
        List<Integer>pobChoice = new ArrayList<>();
        for(int i =0;i<difficulty*difficulty;i++){
            if(!puzzlePiecesDone[i].isCorrect() || puzzlePiecesDone[i] == null){
                for(int tmp = 0;tmp<4;tmp++){
                    int nx = i/difficulty + dx[tmp];
                    int ny = i%difficulty + dy[tmp];
                    if(nx<0 || ny<0 || nx>=difficulty || ny >=difficulty
                            || (puzzlePiecesDone[nx*difficulty+ny] != null && puzzlePiecesDone[nx*difficulty+ny].isCorrect())){
                        pobChoice.add(i);
                    }
                }
            }
        }
        if (pobChoice.isEmpty()) return null;

        int optPosition = pobChoice.get(random.nextInt(pobChoice.size()));
        for(int i = 0; i < puzzlePieces.size(); i++){
            PuzzlePiece piece = puzzlePieces.get(i);
            if(piece.getOriginalIndex() == optPosition){
                return new PieceMovement(PositionType.PieceInWait, optPosition, PositionType.PieceInBoard, optPosition);
            }
        }
        for(int i = 0;i< difficulty*difficulty;i++){
            if(puzzlePiecesDone[i]!=null&&puzzlePiecesDone[i].getOriginalIndex() == optPosition){
                return new PieceMovement(PositionType.PieceInBoard, i,PositionType.PieceInBoard, optPosition);
            }
        }
        return null;
    }

    private void solvePuzzleAnimated() {
        // 1. Stop timer and disable all user interactions
        timerHandler.removeCallbacks(timerRunnable);
        disableAllInteractions();

        // 2. Generate a fresh, ordered list of the correct pieces
        if (imageUriString != null) {
            solvedPiecesForAnimation = ImageSplitter.splitImage(this, Uri.parse(imageUriString), difficulty);
        } else {
            solvedPiecesForAnimation = ImageSplitter.splitImage(this, R.drawable.puzzle_default, difficulty);
        }

        if (solvedPiecesForAnimation == null) {
            Toast.makeText(this, "无法完成拼图，图片加载失败", Toast.LENGTH_SHORT).show();
            enableAllInteractions(); // Re-enable interactions if solving fails
            return;
        }

        // 3. Clear current game state
        puzzlePieces.clear();
        puzzlePiecesDone.clear();
        if (pieceAdapter != null) {
            pieceAdapter.notifyDataSetChanged();
        }
        piecesRecyclerView.setVisibility(View.GONE);

        for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
            ImageView cell = (ImageView) puzzleBoard.getChildAt(i);
            cell.setImageDrawable(null);
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }

        // 4. Start the animation sequence
        solveAnimationIndex = 0;
        solveHandler.post(solveRunnable);
    }

    private final Runnable solveRunnable = new Runnable() {
        @Override
        public void run() {
            if (solvedPiecesForAnimation != null && solveAnimationIndex < solvedPiecesForAnimation.size()) {
                PuzzlePiece piece = solvedPiecesForAnimation.get(solveAnimationIndex);
                int correctIndex = piece.getOriginalIndex();
                ImageView targetCell = (ImageView) puzzleBoard.getChildAt(correctIndex);

                if (targetCell != null) {
                    targetCell.setImageBitmap(piece.getPieceBitmap());
                    targetCell.setBackground(null);

                    // Create and start animation programmatically
                    AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                    fadeIn.setInterpolator(new AccelerateInterpolator());
                    fadeIn.setDuration(300); // A bit faster for a snappier feel
                    fadeIn.setFillAfter(true);
                    targetCell.startAnimation(fadeIn);

                    puzzlePiecesDoneIndex[correctIndex / difficulty][correctIndex % difficulty] = correctIndex;
                    puzzlePiecesDone.add(piece);
                }

                solveAnimationIndex++;
                solveHandler.postDelayed(this, 150); // Delay for next piece
            } else {
                // All pieces are placed, wait for animations to finish, then end game.
                new Handler(Looper.getMainLooper()).postDelayed(GamePlayActivity.this::checkCompletion, 500);
            }
        }
    };

    private void disableAllInteractions() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_solve).setEnabled(false);
            optionsMenu.findItem(R.id.action_hint).setEnabled(false);
            optionsMenu.findItem(R.id.action_undo).setEnabled(false);
            optionsMenu.findItem(R.id.action_view_original).setEnabled(false);
        }

        piecesRecyclerView.setEnabled(false);
        if (pieceAdapter != null) {
            pieceAdapter.setClickListener(null);
        }

        for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
            View cell = puzzleBoard.getChildAt(i);
            cell.setOnClickListener(null);
            cell.setOnLongClickListener(null);
            cell.setOnDragListener(null);
        }
    }

    private void enableAllInteractions() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_solve).setEnabled(true);
            optionsMenu.findItem(R.id.action_hint).setEnabled(true);
            optionsMenu.findItem(R.id.action_undo).setEnabled(true);
            optionsMenu.findItem(R.id.action_view_original).setEnabled(true);
        }
        // This part is for robustness, in case you want to allow restarting the game
        // without leaving the activity. For now, it's mainly for the error path.
    }

    private void endGame() {
        timerHandler.removeCallbacks(timerRunnable);
        long millis = System.currentTimeMillis() - startTime;
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;

        totalTimeText.setText(String.format(Locale.getDefault(), "总用时: %02d:%02d", minutes, seconds));
        completionLayout.setVisibility(View.VISIBLE);
        puzzleBoard.setVisibility(View.GONE);
        piecesRecyclerView.setVisibility(View.GONE);
        Toast.makeText(this, "恭喜你，完成了拼图！", Toast.LENGTH_LONG).show();
    }
    // Done
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_menu, menu);
        this.optionsMenu = menu; // Store the menu instance
        return true;
    }
    // TODO: lack of action
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_view_original) {
            showOriginalImage();
            return true;
        } else if (itemId == R.id.action_undo) {
            undoComplexMove();
            return true;
        } else if (itemId == R.id.action_solve) {
            solvePuzzleAnimated();
            return true;
        } else if (itemId == R.id.action_hint) {
            Toast.makeText(this, "提示功能待实现", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
    // Done
    private void showOriginalImage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ImageView imageView = new ImageView(this);

        if (imageUriString != null) {
            imageView.setImageURI(Uri.parse(imageUriString));
        } else {
            imageView.setImageResource(R.drawable.puzzle_default);
        }

        builder.setView(imageView);
        AlertDialog dialog = builder.create();
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    //Done
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
        solveHandler.removeCallbacks(solveRunnable); // Stop animation if activity is paused
    }
    // ?
    private PuzzlePiece findPieceByCurrentIndex(int cellIndex) {
        for (PuzzlePiece piece : puzzlePiecesDone) {
            if (piece.getCurrentIndex() == cellIndex) {
                return piece;
            }
        }
        return null;
    }
}
