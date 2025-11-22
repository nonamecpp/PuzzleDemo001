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

    // --- End Variables for Animated Solve ---

    /**
     * 负责驱动UI计时器更新的周期性任务。
     */
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // 关键：计算从游戏开始到当前流逝的总毫秒数。
            long millis = System.currentTimeMillis() - startTime;

            // 核心：将总毫秒数转换为“分钟:秒”的格式。
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            // 更新：将格式化后的时间字符串设置到UI的TextView上。
            if (timerText != null) {
                timerText.setText(String.format(Locale.getDefault(), "计时: %02d:%02d", minutes, seconds));
            }

            // 循环：安排此任务在1秒后再次执行，形成定时器效果。
            timerHandler.postDelayed(this, 1000);
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_play);

        // 关键：初始化“返回主菜单”按钮的点击事件。
        Button backToMenuButton = findViewById(R.id.backToMenuButton);
        backToMenuButton.setOnClickListener(v -> {
            // 创建一个意图 (Intent) 来启动您的主菜单 Activity
            // MainActivity.class 是您项目的主入口，这里是正确的
            android.content.Intent intent = new android.content.Intent(GamePlayActivity.this, MainActivity.class);

            // 核心：添加Flag以清空任务栈，防止按返回键时回到已结束的游戏。
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            // 启动主菜单 Activity
            startActivity(intent);

            // 结束当前的游戏 Activity
            finish();
        });

        // 步骤1：初始化界面中的所有视图控件 (Toolbar, GridLayout等)。
        initializeViews();

        // 步骤2：设置Toolbar作为应用的ActionBar，并启用返回箭头。
        setSupportActionBar(gameToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // 步骤3：从Intent中获取游戏难度和图片URI。
        difficulty = getIntent().getIntExtra("difficulty", 3);
        imageUriString = getIntent().getStringExtra("imageUri");

        // 步骤4：根据难度初始化棋盘数组和UI布局。
        puzzlePiecesDone = new PuzzlePiece[difficulty * difficulty];
        setupPuzzleBoard();
        piecesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 步骤5：准备图片、分割碎片、打乱并启动游戏计时器。
        startGame();
    }


    /**
     * 集中初始化Activity中所有的UI视图控件。
     *核心作用：通过findViewById将XML布局中的视图与Java代码中的变量关联起来。
     */
    private void initializeViews() {
        // 获取核心交互及信息展示的UI组件
        gameToolbar = findViewById(R.id.gameToolbar); // 顶部工具栏
        puzzleBoard = findViewById(R.id.puzzleBoard); // 拼图棋盘
        piecesRecyclerView = findViewById(R.id.piecesRecyclerView); // 底部待选碎片列表
        timerText = findViewById(R.id.timerText); // 实时计时器文本
        movesText = findViewById(R.id.movesText); // 步数统计文本

        // 获取游戏完成时显示的UI组件
        completionLayout = findViewById(R.id.completionLayout); // 游戏完成后的覆盖层
        totalTimeText = findViewById(R.id.totalTimeText); // 最终用时文本
    }


    /**
     * 初始化拼图棋盘 (GridLayout)。
     * 核心职责：动态创建棋盘网格，并为每个格子设置点击和拖拽监听器。
     */
    private void setupPuzzleBoard() {
        // 步骤1：根据难度设置GridLayout的行数和列数。
        puzzleBoard.setColumnCount(difficulty);
        puzzleBoard.setRowCount(difficulty);
        moveHistory = new Stack<>(); // 同时清空撤销历史

        // 步骤2：循环创建每一个格子并添加到棋盘中。
        int totalCells = difficulty * difficulty;
        for (int i = 0; i < totalCells; i++) {
            ImageView cell = new ImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();

            // 核心：通过设置权重(weight=1f)，让所有格子均分GridLayout的空间。
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(i % difficulty, 1, 1f);
            params.rowSpec = GridLayout.spec(i / difficulty, 1, 1f);
            params.setMargins(2, 2, 2, 2); // 设置格子间的间距
            cell.setLayoutParams(params);

            // 步骤3：为每个格子设置基础UI和监听器。
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray)); // 默认背景
            cell.setTag(i); // 关键：将格子的索引(0 to N-1)存入Tag，便于后续识别。
            cell.setOnDragListener(this); // 注册拖放事件监听器。
            cell.setOnClickListener(this::handleCellClick); // 注册单击事件监听器。

            // 核心：为棋盘上的格子添加长按监听，以启动“从棋盘拖拽”的操作。
            cell.setOnLongClickListener(v -> {
                int sourceIndex = (int) v.getTag();
                PuzzlePiece sourcePiece = puzzlePiecesDone[sourceIndex];

                // 只有当格子里确实有碎片时，才允许发起拖拽。
                if (sourcePiece != null) {
                    if (selectedPieceExactA != null)
                        darkenPieceBackground(selectedPieceExactA);
                    selectedPieceExactA = new PieceExact(sourceIndex, PositionType.PieceInBoard);

                    // 创建拖拽数据，开始系统的拖拽流程。
                    ClipData dragData = ClipData.newPlainText("Piece", "PieceInBoard");
                    View.DragShadowBuilder myShadow = new View.DragShadowBuilder(v);
                    v.startDragAndDrop(dragData, myShadow, v, 0);
                    return true; // 返回true表示事件已被消费。
                }
                return false; // 如果长按的是空格子，则不响应。
            });

            // 步骤4：将创建好的格子视图添加到棋盘，并初始化数据模型。
            puzzleBoard.addView(cell);
            puzzlePiecesDone[i] = null; // 确保棋盘数据在开始时为空。
        }
    }

    /**
     * 游戏开始的核心逻辑。
     * 负责加载并分割图片、打乱碎片、重置数据并启动计时器。
     */
    private void startGame() {
        // 步骤1：根据传入的URI，尝试加载并分割用户选择的图片。
        Uri imageUri = Uri.parse(imageUriString);
        puzzlePieces = ImageSplitter.splitImage(this, imageUri, difficulty);

        // 核心：健壮性检查。如果用户图片加载失败，则回退到使用内置的默认图片。
        if (puzzlePieces == null || puzzlePieces.isEmpty()) {
            Toast.makeText(this, "图片加载失败，使用默认图片", Toast.LENGTH_LONG).show();
            puzzlePieces = ImageSplitter.splitImage(this, R.drawable.puzzle_default, difficulty);
            // 如果默认图片也加载失败，则终止游戏。
            if (puzzlePieces == null) {
                Toast.makeText(this, "发生致命错误，无法加载游戏", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        // 步骤2：打乱拼图碎片的顺序，增加游戏趣味性。
        Collections.shuffle(puzzlePieces);

        // 步骤3：初始化或清空游戏状态。
        moveHistory.clear(); // 清空撤销历史记录。
        ClearRecyclerHighLight(); // 清除可能存在的UI高亮。

        // 步骤4：设置底部待选碎片列表的适配器。
        pieceAdapter = new PuzzlePieceAdapter(puzzlePieces);
        pieceAdapter.setClickListener(this); // 关键：将Activity自身作为点击监听器。
        piecesRecyclerView.setAdapter(pieceAdapter);

        // 步骤5：重置游戏统计数据（步数和计时器）。
        movesCount = 0;
        if (movesText != null) {
            movesText.setText("步数: 0");
        }
        startTime = System.currentTimeMillis(); // 记录游戏开始的精确时间戳。
        timerHandler.postDelayed(timerRunnable, 0); // 立即启动计时器。

        Toast.makeText(this, "游戏开始!", Toast.LENGTH_SHORT).show();
    }

    private void darkenPieceBackground(PieceExact pieceExact){
        View view = getExactpieceView(pieceExact);
        view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
    }
    /**
     * 将一个精确位置对象(PieceExact)“翻译”成它在具体视图中的位置索引。
     * @param pieceExact 包含位置和类型的精确位置对象。
     * @return 如果在棋盘上，返回棋盘索引；如果在待选区，返回它在Adapter中的索引。
     */
    private int getExactpiecePosition(PieceExact pieceExact) {
        // 安全检查：如果对象为空，则无位置。
        if (pieceExact == null) return -1;

        // 核心：如果碎片在“待选区”(RecyclerView)，需通过其ID查询在Adapter中的实时位置。
        if (pieceExact.getType() == PositionType.PieceInWait) {
            return getAdapterPiecePosition(pieceExact.getPostionID());
        }

        // 如果碎片在“棋盘”上(GridLayout)，其位置ID就是它的棋盘索引，直接返回。
        return pieceExact.getPostionID();
    }

    /**
     * 将一个精确位置对象(PieceExact)“翻译”成它对应的具体UI视图(View)。
     * @param pieceExact 包含位置和类型的精确位置对象。
     * @return 如果在棋盘上，返回对应的ImageView；如果在待选区，返回其ViewHolder的根视图。
     */
    private View getExactpieceView(PieceExact pieceExact) {
        // 安全检查：如果对象为空，则无对应视图。
        if (pieceExact == null) return null;
        View view = null;

        // 核心分支1：如果碎片在“棋盘”上(GridLayout)，通过Tag查找其视图。
        if (pieceExact.getType() == PositionType.PieceInBoard) {
            view = getBoardViewByTag(pieceExact.getPostionID());
        }

        // 核心分支2：如果碎片在“待选区”(RecyclerView)，通过其在Adapter中的位置获取视图。
        if (pieceExact.getType() == PositionType.PieceInWait) {
            view = piecesRecyclerView.getChildAt(getAdapterPiecePosition(pieceExact.getPostionID()));
        }

        return view;
    }

    /**
     * 响应用户在底部“待选碎片列表”(RecyclerView)中的点击事件。
     * @param view 被点击的碎片视图。
     * @param PositionID 被点击碎片的原始ID（注意：不是它在列表中的实时位置）。
     */
    @Override
    public void onPieceClick(View view, int PositionID) {
        // 步骤1：获取被点击的碎片对象。
        int position = getAdapterPiecePosition(PositionID);
        PuzzlePiece piece = pieceAdapter.getPiece(position);

        // 核心：处理UI高亮。如果之前已选中了某个碎片，先调用方法将其高亮效果取消。
        if(selectedPieceExactA != null){
            darkenPieceBackground(selectedPieceExactA);
        }

        // 关键：将当前点击的碎片标记为“已选中”，记录其来源是“待选区”。
        selectedPieceExactA = new PieceExact(piece.getOriginalIndex(), PositionType.PieceInWait);

        // UI反馈：将被点击的碎片视图背景设置为绿色，明确告知用户它已被选中。
        view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
    }


    /**
     * 响应用户在上方“拼图棋盘”(GridLayout)中格子的点击事件。
     * @param v 被点击的棋盘格子视图 (ImageView)。
     */
    private void handleCellClick(View v) {
        ImageView targetCell = (ImageView) v;
        int PositionID = (int) targetCell.getTag();
        PuzzlePiece occupant = puzzlePiecesDone[PositionID]; // 查找当前格子上是否已有碎片

        // --- 核心分支 1: 如果用户手上已经选中了一个碎片 (selectedPieceExactA != null) ---
        if (selectedPieceExactA != null) {
            // 记录用户想要放置的目标位置。
            selectedPieceExactB = new PieceExact(PositionID, PositionType.PieceInBoard);

            // 如果目标位置和起始位置不同，则执行复杂的移动逻辑（包括可能的交换）。
            if(!selectedPieceExactB.equal(selectedPieceExactA))
                PerformComplexMove(new PieceMovement(selectedPieceExactA, selectedPieceExactB));

            // 清理：清除上一个选中项的UI高亮效果。
            if(selectedPieceExactA.getType()!=PositionType.PieceInWait)
                darkenPieceBackground(selectedPieceExactA);

            // 清理：重置两个选择变量，完成本次操作。
            selectedPieceExactA = selectedPieceExactB = null;

        } else {
            // --- 核心分支 2: 手上没有选择任何碎片 (直接点击棋盘) ---

            // 如果点击的是一个空格子，则不执行任何操作。
            if(occupant == null)return;

            // 关键：将当前点击的、带有碎片的格子标记为“已选中”。
            selectedPieceExactA = new PieceExact(PositionID, PositionType.PieceInBoard);

            // UI反馈：将被点击的格子背景设置为绿色，明确告知用户它已被选中。
            v.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        }
    }

    private void incrementMoves() {
        movesCount++;
        if (movesText != null) {
            movesText.setText("步数: " + movesCount);
        }
    }

    private void decrementMoves() {
        movesCount--;
        if (movesText != null) {
            movesText.setText("步数: " + movesCount);
        }
    }

    private void UpdatePieceBoard(int PositionID, PuzzlePiece piece) {
        // 1. 把 View 强转为 ImageView，这样才能设置图片
        ImageView targetCell = (ImageView) getBoardViewByTag(PositionID);
        if(targetCell == null)return;
        if (piece == null) {
            // 如果传进来的 piece 是 null，说明要把这个格子清空
            targetCell.setImageDrawable(null); // 清除图片
            targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray)); // 恢复灰色背景
        } else {
            // 如果有 piece，显示它的图片
            targetCell.setScaleType(ImageView.ScaleType.FIT_XY);
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
        UpdatePieceBoard(position, piece);
        puzzlePiecesDone[position]=piece;
    }
    private void DeletePieceWait(PuzzlePiece piece){
        pieceAdapter.removePiece(getAdapterPiecePosition(piece.getOriginalIndex()));
    }
    private void DeletePieceBoard(int position){
        UpdatePieceBoard(position, null);
        puzzlePiecesDone[position]=null;
    }

    /**
     * 执行一个“原子级”的移动操作，仅处理数据和UI的直接迁移。
     * @param movement 包含起始和目标位置的简单移动指令。
     * 注意：此方法不处理交换逻辑，调用前需确保目标位置是空的。
     */
    private void PerformMove(PieceMovement movement){
        // warning : ensure target board position is empty
        // suggest : make one MOVE into 2 to 3 basic movement
        PieceExact origin = movement.Origin;
        PieceExact target = movement.Target;

        // 场景1：从“待选区”移动到“棋盘”。
        if(origin.getType() == PositionType.PieceInWait && target.getType() == PositionType.PieceInBoard){
            PuzzlePiece piece = pieceAdapter.getPiece(getAdapterPiecePosition(origin.getPostionID()));
            piece.setCurrentIndex(target.getPostionID());
            DeletePieceWait(piece); // 从底部列表数据和UI中移除。
            PushPieceBoard(piece, target.getPostionID()); // 添加到棋盘数据和UI中。
        }
        // 场景2：在“棋盘”内部移动（从一个格子到另一个空格子）。
        else if(origin.getType() == PositionType.PieceInBoard && target.getType() == PositionType.PieceInBoard){
            PuzzlePiece piece = puzzlePiecesDone[origin.getPostionID()];
            piece.setCurrentIndex(target.getPostionID());
            DeletePieceBoard(origin.getPostionID()); // 从原格子数据和UI中移除。
            PushPieceBoard(piece, target.getPostionID()); // 添加到新格子数据和UI中。
        }
        // 场景3：从“棋盘”移回到“待选区”。
        else if(origin.getType() == PositionType.PieceInBoard && target.getType() == PositionType.PieceInWait){
            PuzzlePiece piece = puzzlePiecesDone[origin.getPostionID()];
            piece.setCurrentIndex(-1); // -1 通常表示不在棋盘上。
            DeletePieceBoard(origin.getPostionID()); // 从棋盘数据和UI中移除。
            PushPieceWait(piece); // 添加回底部列表数据和UI中。
        }
    }

    private void UndoLastMove(PieceMovement movement){
        movement.setUndo();
        PerformMove(movement);
    }
    /**
     * 执行一个“复杂”移动，这是所有游戏内移动操作的统一入口。
     * 核心职责：处理目标位置有无碎片的两种情况（移动或交换），记录历史，并更新游戏状态。
     * @param move 玩家意图执行的移动（例如，从待选区到棋盘）。
     */
    private void PerformComplexMove(PieceMovement move) {
        // 步骤1：为本次复杂操作创建一个记录对象，用于撤销。
        ComplexMovement generalmove = new ComplexMovement();
        PieceMovement movement;

        // 核心：检查目标棋盘位置是否已被其他碎片占据。
        if(move.Target.getType() == PositionType.PieceInBoard
                && puzzlePiecesDone[move.Target.getPostionID()]!=null){
            // 如果被占据，则先将“占位”的碎片移回到待选区，为新碎片腾出位置。
            movement = new PieceMovement(
                    move.Target, // 起始点是即将被占用的棋盘格子
                    new PieceExact(
                            puzzlePiecesDone[move.Target.getPostionID()].getOriginalIndex(),
                            PositionType.PieceInWait)); // 目标点是待选区
            PerformMove(movement); // 执行这个“腾位置”的原子操作。
            generalmove.addMove(movement); // 将“腾位置”的步骤记录下来，用于撤销。
        }

        // 步骤2：执行玩家最初请求的移动（此时目标位置保证是空的）。
        PerformMove(move);
        generalmove.addMove(move); // 将玩家的主要移动步骤也记录下来。

        // 步骤3：将本次所有相关的原子操作作为一个整体，压入撤销栈。
        moveHistory.push(generalmove);

        // 步骤4：更新游戏状态。
        incrementMoves(); // 步数增加。
        ClearRecyclerHighLight(); // 清除所有UI高亮。
        checkCompletion(); // 检查游戏是否完成。
    }


    private void undoComplexMove(){
        if(moveHistory.isEmpty())return;
        ComplexMovement move = moveHistory.pop();
        for(PieceMovement movement = move.end();movement!=null;movement = move.previous()) {
            UndoLastMove(movement);
//            System.console().printf("Move " + movement.Origin.getPostionID() + " to " + movement.Target.getPostionID() + "\n");
//            System.console().printf("     " + movement.Origin.getType() + " to " + movement.Target.getType() + "\n");
        }
//        System.console().printf("undo complete\n");
//        ClearHighLight();
        decrementMoves();
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

    /**
     * 响应用户在底部“待选碎片列表”中的长按事件，用于启动拖拽操作。
     * @param view 被长按的碎片视图。
     * @param PositionID 被长按碎片的原始ID。
     */
    @Override
    public void onPieceLongClick(View view, int PositionID) {
        // 规则：如果棋盘已满（即待选区为空），则不允许从待选区发起拖拽。
        int position = getAdapterPiecePosition(PositionID);
        if(puzzlePieces.isEmpty()) return;

        // 步骤1：处理UI高亮。如果之前有其他选中的碎片，先取消其高亮。
        if(selectedPieceExactA!=null){
            darkenPieceBackground(selectedPieceExactA);
        }
        // 步骤2：记录被拖拽的碎片信息，标记其来源是“待选区”。
        PuzzlePiece piece = pieceAdapter.getPiece(position);
        selectedPieceExactA = new PieceExact(piece.getOriginalIndex(), PositionType.PieceInWait);

        // 步骤3：创建拖拽所需的数据。
        ClipData data = ClipData.newPlainText("Piece","PieceInWait");

        // 步骤4：创建拖拽阴影，并启动系统的拖拽流程。
        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(data, myShadow, view, 0);

        // 关键：拖拽开始后，立即隐藏原始视图，营造出“拿起”的效果。
        view.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        // 将 v 转换为目标单元格，这在大多数事件中都是安全的
        ImageView targetCell = (ImageView) v;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // ACTION_DRAG_STARTED 中无法获取 ClipData 内容 (returns null)，只能检查 MIME Type
                if (event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    return true;
                }
                return false;
            case DragEvent.ACTION_DRAG_ENTERED:
                // 【新增】进入时高亮
                targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                // 【新增】离开时取消高亮，恢复原样
                // 注意：只有当格子上没有图片时才恢复灰色背景
                if (targetCell.getDrawable() == null) {
                    targetCell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                } else {
                    targetCell.setBackground(null); // 如果有图，则背景应为透明
                }
                return true;
            case DragEvent.ACTION_DROP:
                int PositionID = (int) targetCell.getTag();
                selectedPieceExactB = new PieceExact(PositionID, PositionType.PieceInBoard);
                if(!selectedPieceExactB.equal(selectedPieceExactA))
                    PerformComplexMove(new PieceMovement(selectedPieceExactA, selectedPieceExactB));
                selectedPieceExactA = selectedPieceExactB = null;
                // 【重要】在放下后也清除一下高亮，因为 ACTION_DRAG_EXITED 可能不被触发
                ClearHighLight();
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                View draggedView = (View) event.getLocalState();
                if (!event.getResult()) {
                    if (draggedView != null) {
                        draggedView.setVisibility(View.VISIBLE);
                    }
                }
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                // 通常忽略
                return true;
            default:
                return false;
        }
    }
    /**
     * 清除整个棋盘 (GridLayout) 上的所有高亮效果。* 核心职责：遍历所有棋盘格子，将它们的背景恢复到默认状态。
     */
    public void ClearHighLight(){
        // 遍历棋盘上的每一个格子视图 (ImageView)。
        for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
            View cell = puzzleBoard.getChildAt(i);
            // 如果格子里没有图片（即为空格子），则恢复其深灰色背景。
            if (((ImageView) cell).getDrawable() == null) {
                cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            } else {
                // 如果格子里已经有图片，则将背景设置为 null（透明），以避免遮挡图片。
                cell.setBackground(null);
            }
        }
    }

    public void ClearRecyclerHighLight(){
        for(int i = 0;i<piecesRecyclerView.getChildCount();i++){
            piecesRecyclerView.getChildAt(i).setVisibility(View.VISIBLE);
        }
        for(int i = 0;i<piecesRecyclerView.getChildCount();i++) {
            View cell = piecesRecyclerView.getChildAt(i);
            cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));
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
    /**
     * 获取一个“提示”移动，即找到一个当前可以放置到正确位置的碎片。
     * @return 一个 PieceMovement 对象，描述了从哪里移动到哪里的正确步骤；如果没有可行的提示，则返回 null。
     */
    public PieceMovement getTip() {
        // 步骤1：找出所有“有价值”的目标位置。
        // “有价值”的位置定义为：该位置本身未放置正确，或其邻居尚未放置正确。
        // 这是一种优化，避免在已经完成的区域寻找提示。
        List<Integer>pobChoice = new ArrayList<>();
        for(int i =0;i<difficulty*difficulty;i++){
            if(puzzlePiecesDone[i] == null || !puzzlePiecesDone[i].isCorrect()){
                // 此处复杂的 for 循环和 if 判断逻辑，是为了确定当前格子 i 是否值得作为寻找目标。
                // 如果一个格子的四周有空格子，或者有尚未完成的邻居，它就被认为是有价值的。
                for(int tmp = 0;tmp<4;tmp++){
                    int nx = i/difficulty + dx[tmp];
                    int ny = i%difficulty + dy[tmp];
                    if(nx<0 || ny<0 || nx>=difficulty || ny >=difficulty
                            || (puzzlePiecesDone[nx*difficulty+ny] != null && puzzlePiecesDone[nx*difficulty+ny].isCorrect())){
                        pobChoice.add(i);
                        break; // 只要满足一个条件，就加入列表并跳出内层循环
                    }
                }
            }
        }
        // 如果没有找到任何有价值的目标（通常意味着游戏已完成），则返回null。
        if (pobChoice.isEmpty()) return null;

        // 步骤2：从所有有价值的目标中，随机选择一个作为本次提示的目标。
        int optPosition = pobChoice.get(random.nextInt(pobChoice.size()));

        // 步骤3：寻找这个目标碎片现在位于何处。

        // 情况A：在“待选区”里寻找这个碎片。
        for(int i = 0; i < puzzlePieces.size(); i++){
            PuzzlePiece piece = puzzlePieces.get(i);
            if(piece.getOriginalIndex() == optPosition){
                // 找到了！构造一个从“待选区”到其正确“棋盘”位置的移动。
                return new PieceMovement(new PieceExact(optPosition, PositionType.PieceInWait), new PieceExact(optPosition,PositionType.PieceInBoard));
            }
        }

        // 情况B：如果待选区没找到，就在“棋盘”的其他错误位置上寻找它。
        for(int i = 0;i< difficulty*difficulty;i++){
            if(puzzlePiecesDone[i]!=null&&puzzlePiecesDone[i].getOriginalIndex() == optPosition){
                // 找到了！构造一个从棋盘上的“错误位置”到“正确位置”的移动。
                return new PieceMovement(new PieceExact(i,PositionType.PieceInBoard), new PieceExact(optPosition,PositionType.PieceInBoard));
            }
        }
        // 如果逻辑上都找不到（几乎不可能发生），则返回null。
        return null;
    }


    /**
     * 自动完成的核心方法。
     * 它会获取一个提示，执行移动，然后通过Handler安排下一次移动，直到拼图完成。
     */
    private void autoComplete() {
        // 如果游戏已经完成，或者没有待移动的碎片了，就停止
        if (puzzlePieces.isEmpty()) {
            Toast.makeText(this, "自动完成结束！", Toast.LENGTH_SHORT).show();
            // 确保游戏结束逻辑被触发
            if (completionLayout.getVisibility() != View.VISIBLE) {
                checkCompletion();
            }
            return;
        }

        // 1. 获取下一步的最佳移动策略
        PieceMovement nextMove = getTip();

        if (nextMove == null) {
            // 如果 getTip() 返回 null，说明可能已经完成或陷入僵局
            Toast.makeText(this, "没有可执行的移动了。", Toast.LENGTH_SHORT).show();
            checkCompletion();
            return;
        }

        // 2. 立即执行这一步移动
        PerformComplexMove(nextMove);

        // 3. 安排下一次自动移动
        // 使用 Handler.postDelayed 来创建一个短暂的延迟（例如100毫秒），
        // 这样用户就能看到一步步完成的动画效果。
        new Handler(Looper.getMainLooper()).postDelayed(this::autoComplete, 100); // 100毫秒延迟
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
            undoComplexMove();
            return true;
        } else if (itemId == R.id.action_hint) {
            // 调用 getTip() 获取提示
            selectedPieceExactA = selectedPieceExactB = null;
            PieceMovement tip = getTip();
            if (tip == null) {
                // 如果没有可用的提示（可能拼图已完成或出现意外情况）
                Toast.makeText(this, "没有可用的提示或拼图已完成", Toast.LENGTH_SHORT).show();
            } else {
                // 如果获取到了提示，就执行提示效果
                showHint(tip);
            }
            return true;
        } else if (itemId == R.id.action_solve) { // <-- 新增的“自动完成”逻辑分支
            // 弹出确认对话框，防止用户误触
            new AlertDialog.Builder(this)
                    .setTitle("自动完成")
                    .setMessage("您确定要自动完成拼图吗？这将计入总用时。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        // 用户确认后，开始自动完成
                        Toast.makeText(GamePlayActivity.this, "开始自动完成...", Toast.LENGTH_SHORT).show();
                        autoComplete(); // 调用我们之前添加的autoComplete方法
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
    /**
     * 根据 getTip() 返回的结果，在界面上高亮显示提示
     * @param tip 包含提示信息的对象
     */
    private void showHint(PieceMovement tip) {
        if (tip == null) return; // 安全检查
        PieceExact origin = tip.Origin;
        PieceExact target = tip.Target;
        int sourceIndex = getExactpiecePosition(origin);
        int targetIndex = getExactpiecePosition(target);
        if (origin.getType() == PositionType.PieceInWait) {
            // Plan A 的提示：源于【待选列表】-> 目标是【棋盘】

            piecesRecyclerView.smoothScrollToPosition(sourceIndex);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                RecyclerView.ViewHolder holder = piecesRecyclerView.findViewHolderForAdapterPosition(sourceIndex);
                View pieceView = (holder != null) ? holder.itemView : null;
                View boardCell = puzzleBoard.getChildAt(targetIndex);

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