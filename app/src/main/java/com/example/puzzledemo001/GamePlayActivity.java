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
    private List<PuzzlePiece> puzzlePiecesDone;
    private int[][] puzzlePiecesDoneIndex;
    private int correctPiecesCount = 0;
    private int movesCount = 0; // To count user moves

    // Variables for click-to-place functionality
    private PuzzlePiece selectedPiece = null;
    private int selectedPiecePosition = -1;
    private View selectedPieceView = null;
    private PuzzlePiece boardSelectedPiece = null;
    private ImageView boardSelectedCellView = null;

    private long startTime;
    private Handler timerHandler = new Handler(Looper.getMainLooper());

    // --- Variables for Undo --- 
    private Stack<Move> moveHistory = new Stack<>();

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

    // --- Undo Move Classes --- //
    private abstract class Move {
        void undo() {
            movesCount--;
            if (movesText != null) {
                movesText.setText("步数: " + movesCount);
            }
        }
    }

    private class PlaceMove extends Move {
        final PuzzlePiece piece;
        final int targetIndex;

        PlaceMove(PuzzlePiece piece, int targetIndex) {
            this.piece = piece;
            this.targetIndex = targetIndex;
        }

        @Override
        void undo() {
            super.undo();
            undoPiece(piece, targetIndex);
        }
    }

    private class ReplaceMove extends Move {
        final PuzzlePiece newPiece;
        final PuzzlePiece originalPiece;
        final int targetIndex;

        ReplaceMove(PuzzlePiece newPiece, PuzzlePiece originalPiece, int targetIndex) {
            this.newPiece = newPiece;
            this.originalPiece = originalPiece;
            this.targetIndex = targetIndex;
        }

        @Override
        void undo() {
            super.undo();
            // Move the new piece from the board back to the adapter
            undoPiece(newPiece, targetIndex);

            // Find and remove the original piece from the adapter
            int originalPieceAdapterPosition = getAdapterPiecePosition(originalPiece.getOriginalIndex());
            if (originalPieceAdapterPosition != -1) {
                pieceAdapter.removePiece(originalPieceAdapterPosition);

                // Place it back on the board
                ImageView cell = (ImageView) puzzleBoard.getChildAt(targetIndex);
                cell.setImageBitmap(originalPiece.getPieceBitmap());
                cell.setBackground(null);
                MovePiece(originalPiece, targetIndex);
            }
        }
    }

    private class SwapMove extends Move {
        final PuzzlePiece piece1;
        final PuzzlePiece piece2;

        SwapMove(PuzzlePiece piece1, PuzzlePiece piece2) {
            this.piece1 = piece1;
            this.piece2 = piece2;
        }

        @Override
        void undo() {
            super.undo();
            // The current indices are the swapped ones, so we pass them to swap back.
            performSwap(piece1, piece1.getCurrentIndex(), piece2, piece2.getCurrentIndex());
        }
    }
    // --- End Undo Move Classes --- //

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
        puzzlePiecesDoneIndex = new int[difficulty][difficulty];
        puzzlePiecesDone = new ArrayList<>();
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
            puzzlePiecesDoneIndex[i/difficulty][i%difficulty] = -1;
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

    private void incrementMoves() {
        movesCount++;
        if (movesText != null) {
            movesText.setText("步数: " + movesCount);
        }
    }

    private void undoLastMove() {
        if (moveHistory.isEmpty()) {
            Toast.makeText(this, "没有可以撤销的操作了", Toast.LENGTH_SHORT).show();
            return;
        }

        Move lastMove = moveHistory.pop();
        lastMove.undo();

        // After undo, the selected piece state should be cleared
        if (selectedPieceView != null) {
            selectedPieceView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        }
        selectedPiece = null;
        selectedPiecePosition = -1;
        selectedPieceView = null;

        checkCompletion(); // Check if the puzzle is now solved or unsolved
    }

    @Override
    public void onPieceClick(View view, int position) {
        if (selectedPiecePosition == position) {
            if(selectedPieceView != null) {
                 selectedPieceView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }
            selectedPiece = null;
            selectedPiecePosition = -1;
            selectedPieceView = null;
        } else {
            if (selectedPieceView != null) {
                selectedPieceView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }

            selectedPiece = pieceAdapter.getPiece(position);
            selectedPiecePosition = position;
            selectedPieceView = view;
            view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        }
    }

    /**
     * 清除底部待选列表的选择状态
     */
    private void clearBottomSelection() {
        if (selectedPieceView != null) {
            selectedPieceView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        }
        selectedPiece = null;
        selectedPiecePosition = -1;
        selectedPieceView = null;
    }

    /**
     * 清除棋盘上的选择状态
     */
    private void clearBoardSelection() {
        if (boardSelectedCellView != null) {
            // 恢复之前选中格子的背景（如果它上面有图就设为null，没图就设为灰色）
            if (boardSelectedCellView.getDrawable() != null) {
                boardSelectedCellView.setBackground(null);
            } else {
                boardSelectedCellView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            }
        }
        boardSelectedPiece = null;
        boardSelectedCellView = null;
    }


    private void handleCellClick(View v) {
        ImageView targetCell = (ImageView) v;
        int targetIndex = (int) targetCell.getTag();
        PuzzlePiece occupant = findPieceByCurrentIndex(targetIndex); // 查找当前格子上是否已有碎片

        if (selectedPiece != null) {
            // --- 逻辑分支 1: 手上已从底部列表选择了一个碎片 ---

            if (occupant != null) {
                // 目标格子已经有碎片了，执行【替换】逻辑
                incrementMoves();
                moveHistory.push(new ReplaceMove(selectedPiece, occupant, targetIndex));
                undoPiece(occupant, occupant.getCurrentIndex());
            } else {
                // 目标格子是空的，执行【放置】逻辑
                incrementMoves();
                moveHistory.push(new PlaceMove(selectedPiece, targetIndex));
            }

            // 执行放置/替换的公共操作
            targetCell.setImageBitmap(selectedPiece.getPieceBitmap());
            targetCell.setBackground(null);
            MovePiece(selectedPiece, targetIndex);
            pieceAdapter.removePiece(selectedPiecePosition);

            // 清空底部列表的选择状态
            clearBottomSelection();

            if (puzzlePieces.isEmpty()) {
                checkCompletion();
            }

        } else {
            // --- 逻辑分支 2: 手上没有选择任何碎片 (直接点击棋盘) ---

            if (occupant == null) {
                // 点击了一个空格子，手上又没牌，自然是无效操作
                Toast.makeText(this, "请先从下方选择一个拼图块", Toast.LENGTH_SHORT).show();
                return;
            }

            // 点击了一个已有碎片的格子
            if (boardSelectedPiece == null) {
                // A. 板上之前没有选中任何碎片，现在选中它
                boardSelectedPiece = occupant;
                boardSelectedCellView = targetCell;
                // 添加高亮效果以提示用户
                boardSelectedCellView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
                Toast.makeText(this, "已选中，请再点击另一块来交换", Toast.LENGTH_SHORT).show();
            } else {
                // B. 板上之前已经选中了一个碎片，现在执行【交换】
                if (boardSelectedPiece == occupant) {
                    // 点击了同一个碎片两次，视为【取消选择】
                    clearBoardSelection();
                } else {
                    // 点击了不同的碎片，执行【交换】
                    incrementMoves();
                    moveHistory.push(new SwapMove(boardSelectedPiece, occupant));
                    performSwap(boardSelectedPiece, boardSelectedPiece.getCurrentIndex(), occupant, targetIndex);
                    // 交换后清空选择状态
                    clearBoardSelection();
                }
            }
        }
    }


    private void checkCompletion() {
        if (!puzzlePieces.isEmpty()) return; // Don't check until all pieces are on the board

        boolean allCorrect = true;
        for (int i = 0; i < difficulty; i++) {
            for (int j = 0; j < difficulty; j++) {
                if (puzzlePiecesDoneIndex[i][j] != i * difficulty + j) {
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

        PuzzlePiece piece = pieceAdapter.getPiece(position);
        ClipData.Item item = new ClipData.Item(Integer.toString(position));
        ClipData dragData = new ClipData("puzzle_piece", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);

        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(dragData, myShadow, view, 0);

        view.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        // 将 v 转换为目标单元格，这在大多数事件中都是安全的
        ImageView targetCell = (ImageView) v;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                ClipDescription description = event.getClipDescription();
                if (description != null) {
                    return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                }
                return false;

            case DragEvent.ACTION_DRAG_ENTERED:
                targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                if (((ImageView) v).getDrawable() == null) {
                    v.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                }
                return true;

            case DragEvent.ACTION_DROP:
                ClipDescription clipDescription = event.getClipDescription();
                if (clipDescription == null) {
                    return false;
                }
                String clipLabel = clipDescription.getLabel().toString();
                int targetIndex = (int) targetCell.getTag();
                PuzzlePiece targetOccupant = findPieceByCurrentIndex(targetIndex);

                if ("board_piece".equals(clipLabel)) {
                    // --- 板内拖拽逻辑 ---
                    int sourceIndex = Integer.parseInt(event.getClipData().getItemAt(0).getText().toString());
                    if (sourceIndex == targetIndex) return true;

                    PuzzlePiece sourcePiece = findPieceByCurrentIndex(sourceIndex);

                    if (sourcePiece != null) {
                        incrementMoves();
                        if (targetOccupant != null) {
                            // 场景1: 板上碎片拖到【已占用的格子】-> 执行交换
                            moveHistory.push(new SwapMove(sourcePiece, targetOccupant));
                            performSwap(sourcePiece, sourceIndex, targetOccupant, targetIndex);
                        } else {
                            // 场景2: 板上碎片拖到【空格子】-> 执行移动
                            moveHistory.push(new PlaceMove(sourcePiece, targetIndex));

                            // 1. 从模型中移除旧位置的记录
                            puzzlePiecesDone.remove(sourcePiece);
                            puzzlePiecesDoneIndex[sourceIndex / difficulty][sourceIndex % difficulty] = -1;

                            // 2. 清理原格子的视觉效果
                            ImageView sourceCell = puzzleBoard.findViewWithTag(sourceIndex);
                            if (sourceCell != null) {
                                sourceCell.setImageDrawable(null);
                                sourceCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                            }

                            // 3. 在新位置放置碎片 (视觉和数据)
                            targetCell.setImageBitmap(sourcePiece.getPieceBitmap());
                            targetCell.setBackground(null);
                            MovePiece(sourcePiece, targetIndex);
                        }
                    }
                } else { // 假定是从列表拖拽
                    // --- 从列表拖拽到板上的逻辑 ---
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    int position = Integer.parseInt(item.getText().toString());

                    if (position >= 0 && position < pieceAdapter.getItemCount()) {
                        PuzzlePiece draggedPiece = pieceAdapter.getPiece(position);
                        if (draggedPiece != null) {
                            incrementMoves();

                            if (targetOccupant != null) {
                                // 场景3: 列表碎片拖到【已占用的格子】-> 执行"以物换物"
                                moveHistory.push(new ReplaceMove(draggedPiece, targetOccupant, targetIndex));

                                // 从板上数据模型移除被替换者
                                puzzlePiecesDone.remove(targetOccupant);

                                // --- 这里是修正点 ---
                                // 2. 将 targetOccupant 添加回底部列表的数据源
                                puzzlePieces.add(targetOccupant); // 直接操作 Activity 中的 puzzlePieces 列表
                                pieceAdapter.notifyDataSetChanged(); // 通知适配器数据已改变，刷新整个列表
                                // --- 修正结束 ---

                            } else {
                                // 场景4: 列表碎片拖到【空格子】-> 正常放置
                                moveHistory.push(new PlaceMove(draggedPiece, targetIndex));
                            }

                            // 公共操作: 从底部列表移除拖拽的碎片，并在板上放置它
                            pieceAdapter.removePiece(position);

                            targetCell.setImageBitmap(draggedPiece.getPieceBitmap());
                            targetCell.setBackground(null);
                            MovePiece(draggedPiece, targetIndex);

                            if (puzzlePieces.isEmpty()) {
                                checkCompletion();
                            }
                        }
                    }
                }
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                View draggedView = (View) event.getLocalState();
                if (!event.getResult()) {
                    if (draggedView != null) {
                        draggedView.setVisibility(View.VISIBLE);
                    }
                }
                // 清理所有格子的背景高亮
                for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
                    View cell = puzzleBoard.getChildAt(i);
                    if (((ImageView) cell).getDrawable() == null) {
                        cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    } else {
                        cell.setBackground(null);
                    }
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
        if (piece == null) return;
        // Remove from board model
        puzzlePiecesDone.remove(piece);
        puzzlePiecesDoneIndex[originalIndex/difficulty][originalIndex%difficulty] = -1;

        // Visually clear the cell on the board
        ImageView cell = (ImageView) puzzleBoard.getChildAt(originalIndex);
        if (cell != null) {
            cell.setImageBitmap(null);
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }

        // Add back to adapter
        piece.setCurrentIndex(-1);
        pieceAdapter.undoPiece(piece);
    }

    private void performSwap(PuzzlePiece piece1, int index1, PuzzlePiece piece2, int index2) {
        if (piece1 == null || piece2 == null) return;
        ImageView cell1 = (ImageView) puzzleBoard.getChildAt(index1);
        ImageView cell2 = (ImageView) puzzleBoard.getChildAt(index2);
        if (cell1 == null || cell2 == null) return;

        // Swap visuals
        cell1.setImageBitmap(piece2.getPieceBitmap());
        cell2.setImageBitmap(piece1.getPieceBitmap());

        // Swap data in model
        int piece1CurrentIndex = piece1.getCurrentIndex();
        piece1.setCurrentIndex(piece2.getCurrentIndex());
        piece2.setCurrentIndex(piece1CurrentIndex);
        puzzlePiecesDoneIndex[index1 / difficulty][index1 % difficulty] = piece2.getOriginalIndex();
        puzzlePiecesDoneIndex[index2 / difficulty][index2 % difficulty] = piece1.getOriginalIndex();

        checkCompletion();
    }

    private int getAdapterPiecePosition(int originalIndex) {
        for (int i=0; i < puzzlePieces.size(); i++) {
            if (puzzlePieces.get(i).getOriginalIndex() == originalIndex) {
                return i;
            }
        }
        return -1;
    }

    public pieceTip getTip() {
        List<Integer>pobChoice = new ArrayList<>();
        for(int i = 0; i < puzzlePiecesDone.size(); i++){
            PuzzlePiece piece = puzzlePiecesDone.get(i);
            if(!piece.isCorrect()){
                return new pieceTip(false, i, piece.getOriginalIndex());
            }
        }
        for(int i =0, k = 0;i<difficulty;i++){
            for(int j=0;j<difficulty;j++,k++){
                if(puzzlePiecesDoneIndex[i][j] == -1){ // Check for empty cells
                    pobChoice.add(k);
                }
            }
        }
        if (pobChoice.isEmpty()) return null;

        int optPosition = pobChoice.get(random.nextInt(pobChoice.size()));
        for(int i = 0; i < puzzlePieces.size(); i++){
            PuzzlePiece piece = puzzlePieces.get(i);
            if(piece.getOriginalIndex() == optPosition){
                return new pieceTip(true, i, optPosition);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_menu, menu);
        this.optionsMenu = menu; // Store the menu instance
        return true;
    }

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
            undoLastMove();
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
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
        solveHandler.removeCallbacks(solveRunnable); // Stop animation if activity is paused
    }

    private PuzzlePiece findPieceByCurrentIndex(int cellIndex) {
        for (PuzzlePiece piece : puzzlePiecesDone) {
            if (piece.getCurrentIndex() == cellIndex) {
                return piece;
            }
        }
        return null;
    }
}
