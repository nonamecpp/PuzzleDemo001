package com.example.puzzledemo001;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.BitmapFactory;
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

        // ============== 在这里粘贴代码 ==============
        Button backToMenuButton = findViewById(R.id.backToMenuButton);        backToMenuButton.setOnClickListener(v -> {
            // 创建一个意图 (Intent) 来启动您的主菜单 Activity
            // MainActivity.class 是您项目的主入口，这里是正确的
            android.content.Intent intent = new android.content.Intent(GamePlayActivity.this, MainActivity.class);

            // 添加这个 Flag 是为了清空当前的游戏任务栈，
            // 这样当用户在主菜单按返回键时，不会再回到刚刚结束的游戏界面。
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            // 启动主菜单 Activity
            startActivity(intent);

            // 结束当前的游戏 Activity
            finish();
        });
        // ============================================

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
        Uri imageUri = Uri.parse(imageUriString);
        puzzlePieces = ImageSplitter.splitImage(this, imageUri, difficulty);

        // --- 修正结束 ---

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
            // --- 核心修正：添加延迟 ---
            // 1. 立即停止计时器
            timerHandler.removeCallbacks(timerRunnable);

            // 2. 禁用所有可交互的UI元素，防止用户在延迟期间进行多余操作
            piecesRecyclerView.setEnabled(false); // 禁用底部列表的交互

            // 3. 使用 Handler 延迟执行 endGame()
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 这部分代码将在 2 秒后执行
                    endGame();
                }
            }, 2000); // 这里的 2000 代表延迟 2000 毫秒 (2秒)，您可以按需修改
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
        // --- 增强版逻辑 ---

        // 1. 更新数据模型 (这是您已有的逻辑)
        piece.setCurrentIndex(targetIndex);
        puzzlePiecesDone.add(piece);
        puzzlePiecesDoneIndex[targetIndex/difficulty][targetIndex%difficulty] = piece.getOriginalIndex();

        // 2. 更新视觉视图 (这是新增的逻辑)
        ImageView targetCell = (ImageView) puzzleBoard.getChildAt(targetIndex);
        if (targetCell != null) {
            targetCell.setImageBitmap(piece.getPieceBitmap());
            // 核心修正：确保图片填充满格子，消除缝隙
            targetCell.setScaleType(ImageView.ScaleType.FIT_XY);
            targetCell.setBackground(null); // 清除灰色背景
        }
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

        cell1.setScaleType(ImageView.ScaleType.FIT_XY);
        cell2.setScaleType(ImageView.ScaleType.FIT_XY);
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
        // --- 最终、最可靠的提示逻辑 ---

        // =================================================================================
        // Plan A: 优先提示【底部可见的待选块】应该去哪里
        // =================================================================================
        LinearLayoutManager layoutManager = (LinearLayoutManager) piecesRecyclerView.getLayoutManager();
        if (layoutManager != null) {
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();

            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                List<Integer> visibleAdapterPositions = new ArrayList<>();
                for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
                    if (i < puzzlePieces.size()) {
                        visibleAdapterPositions.add(i);
                    }
                }
                Collections.shuffle(visibleAdapterPositions);

                // --- 阶段1：优先寻找目标为空的提示 ---
                for (int adapterPosition : visibleAdapterPositions) {
                    PuzzlePiece pieceToHint = puzzlePieces.get(adapterPosition);
                    int targetBoardIndex = pieceToHint.getOriginalIndex();

                    // 直接检查视图状态，不依赖数据模型
                    View targetCellView = puzzleBoard.getChildAt(targetBoardIndex);
                    if (targetCellView instanceof ImageView) {
                        ImageView targetCell = (ImageView) targetCellView;
                        if (targetCell.getDrawable() == null) {
                            // 目标格子是空的，这是最完美的提示，立即返回
                            return new pieceTip(true, adapterPosition, targetBoardIndex);
                        }
                    }
                }

                // --- 阶段2：如果找不到空目标，再寻找目标被占用的提示 ---
                for (int adapterPosition : visibleAdapterPositions) {
                    PuzzlePiece pieceToHint = puzzlePieces.get(adapterPosition);
                    int targetBoardIndex = pieceToHint.getOriginalIndex();

                    // 使用数据模型检查占据者是否是【错误】的块
                    PuzzlePiece occupantPiece = findPieceByCurrentIndex(targetBoardIndex);
                    // 只有在【目标位置被占据】且【占据者不是正确的块】时，才提示
                    if (occupantPiece != null && occupantPiece.getOriginalIndex() != targetBoardIndex) {
                        return new pieceTip(true, adapterPosition, targetBoardIndex);
                    }
                }
            }
        }

        // =================================================================================
        // Plan B: 如果Plan A在两个阶段都找不到提示，则在棋盘上寻找错位块 (保持不变)
        // =================================================================================
        List<PuzzlePiece> misplacedPiecesOnBoard = new ArrayList<>();
        for (PuzzlePiece piece : puzzlePiecesDone) {
            if (!piece.isCorrect()) {
                misplacedPiecesOnBoard.add(piece);
            }
        }

        if (!misplacedPiecesOnBoard.isEmpty()) {
            Collections.shuffle(misplacedPiecesOnBoard);
            PuzzlePiece pieceToHint = misplacedPiecesOnBoard.get(0);
            return new pieceTip(false, pieceToHint.getCurrentIndex(), pieceToHint.getOriginalIndex());
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
        } else if (itemId == R.id.action_hint) {
            // 调用 getTip() 获取提示
            pieceTip tip = getTip();
            if (tip == null) {
                // 如果没有可用的提示（可能拼图已完成或出现意外情况）
                Toast.makeText(this, "没有可用的提示或拼图已完成", Toast.LENGTH_SHORT).show();
            } else {
                // 如果获取到了提示，就执行提示效果
                showHint(tip);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 根据 getTip() 返回的结果，在界面上高亮显示提示
     * @param tip 包含提示信息的对象
     */
    /**
     * 根据 getTip() 返回的结果，在界面上高亮显示提示
     * @param tip 包含提示信息的对象
     */
    /**
     * 根据 getTip() 返回的结果，在界面上高亮显示提示
     * @param tip 包含提示信息的对象
     */
    /**
     * 根据 getTip() 返回的结果，在界面上高亮显示提示
     * @param tip 包含提示信息的对象
     */
    private void showHint(pieceTip tip) {
        if (tip == null) return; // 安全检查

        if (tip.getIsUnused()) {
            // Plan A 的提示：源于【待选列表】-> 目标是【棋盘】
            int adapterPosition = tip.getOriginPosition();
            int boardIndex = tip.getTargetPosition();

            piecesRecyclerView.smoothScrollToPosition(adapterPosition);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                RecyclerView.ViewHolder holder = piecesRecyclerView.findViewHolderForAdapterPosition(adapterPosition);
                View pieceView = (holder != null) ? holder.itemView : null;
                View boardCell = puzzleBoard.getChildAt(boardIndex);

                // --- 核心修正：解耦动画调用，确保它们能独立执行 ---
                // 1. 只要找到下方的块，就闪烁它
                if (pieceView != null) {
                    animateHintView(pieceView, false); // 普通闪烁
                }

                // 2. 只要找到棋盘上的目标格，就闪烁它（无论上面是空的还是被占用了）
                if (boardCell != null) {
                    animateHintView(boardCell, true);  // 绿色高亮
                }

                // --- 修正结束 ---

            }, 300);

        }  else {
            // Plan B 的提示：源于【棋盘】-> 目标是【棋盘】
            // 这部分是您已完美实现的功能，我们一字不改，完全保留！
            int sourceIndex = tip.getOriginPosition();
            int targetIndex = tip.getTargetPosition();

            View sourceCell = puzzleBoard.getChildAt(sourceIndex);
            View targetCell = puzzleBoard.getChildAt(targetIndex);

            if (sourceCell != null && targetCell != null) {
                animateHintView(sourceCell, false);
                // 完全保留您原来的调用，确保功能不被破坏
                animateHintView(targetCell, false);
            } else {
                Toast.makeText(GamePlayActivity.this, "提示：请注意棋盘上的拼图块", Toast.LENGTH_SHORT).show();
            }
        }
    }






    /**
     * 对给定的视图执行闪烁动画
     * @param viewToAnimate 需要执行动画的View
     * @param isGreenHint   如果为true，则使用绿色前景闪烁；否则使用透明度闪烁。
     */
    private void animateHintView(View viewToAnimate, boolean isGreenHint) {
        if (viewToAnimate == null) return;

        // 清除可能正在进行的旧动画，防止冲突
        viewToAnimate.clearAnimation();

        if (isGreenHint) {
            // --- 方案A：绿色前景闪烁 (Foreground Animation) ---
            // 这种方式会在图片上层叠加一个半透明的颜色层来闪烁，完美解决遮挡问题。

            // 1. 创建一个半透明的绿色 Drawable 作为前景
            android.graphics.drawable.GradientDrawable foregroundDrawable = new android.graphics.drawable.GradientDrawable();
            foregroundDrawable.setColor(ContextCompat.getColor(this, R.color.hint_green));

            // 2. 将前景设置到视图上
            viewToAnimate.setForeground(foregroundDrawable);

            // 3. 创建一个 ValueAnimator 来改变前景的透明度 (从半透明 -> 完全透明)
            ValueAnimator alphaAnimator = ValueAnimator.ofInt(150, 0); // 150是比较合适的半透明值
            alphaAnimator.setDuration(350);
            alphaAnimator.setRepeatCount(3);
            alphaAnimator.setRepeatMode(ValueAnimator.REVERSE);

            alphaAnimator.addUpdateListener(animation -> {
                int alphaValue = (int) animation.getAnimatedValue();
                // 实时更新前景的透明度
                if (viewToAnimate.getForeground() != null) {
                    viewToAnimate.getForeground().setAlpha(alphaValue);
                }
            });

            alphaAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // 动画结束后，彻底移除前景，恢复视图原状
                    viewToAnimate.setForeground(null);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    // 如果动画被取消，也移除前景
                    viewToAnimate.setForeground(null);
                }
            });

            alphaAnimator.start();

        } else {
            // --- 方案B：普通透明度闪烁 (AlphaAnimation) ---
            // (此部分逻辑正确，保持不变)
            AlphaAnimation blinkAnimation = new AlphaAnimation(1.0f, 0.2f);
            blinkAnimation.setDuration(250);
            blinkAnimation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            blinkAnimation.setRepeatCount(3);
            blinkAnimation.setRepeatMode(Animation.REVERSE);
            viewToAnimate.startAnimation(blinkAnimation);
        }
    }



    private void showOriginalImage() {
        // 检查 imageUriString 是否有效，这主要是为了代码健壮性
        if (imageUriString == null || imageUriString.isEmpty()) {
            Toast.makeText(this, "图片资源丢失", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ImageView imageView = new ImageView(this);

        // --- 您想要保留的、非常好的设置代码 ---
        // 设置一个最小尺寸，防止在某些设备上因为没有尺寸而显示不出来
        imageView.setMinimumWidth(500);
        imageView.setMinimumHeight(500);
        // 设置缩放模式，确保图片能被看见
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        // --- 保留结束 ---

        // --- 核心简化点在这里 ---
        // 不再需要 if-else，无条件地使用接收到的 imageUriString 来设置图片
        imageView.setImageURI(Uri.parse(imageUriString));
        // --- 简化结束 ---

        builder.setView(imageView);
        AlertDialog dialog = builder.create();

        // 添加点击图片关闭对话框的功能，提升用户体验
        imageView.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }



    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
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
