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

        // ============== 在这里粘贴代码 ==============
        Button backToMenuButton = findViewById(R.id.backToMenuButton);
        backToMenuButton.setOnClickListener(v -> {
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
        moveHistory = new Stack<>();
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
                    if(selectedPieceExactA!=null)
                        darkenPieceBackground(selectedPieceExactA);
                    selectedPieceExactA = new PieceExact(sourceIndex, PositionType.PieceInBoard);
                    // 使用 "board_piece" 标签来标识这次拖拽源自棋盘
                    ClipData dragData = ClipData.newPlainText("Piece", "PieceInBoard");
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
    public void onPieceClick(View view, int PositionID) {
        int position = getAdapterPiecePosition(PositionID);
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
        PuzzlePiece occupant = puzzlePiecesDone[PositionID]; // 查找当前格子上是否已有碎片

        if (selectedPieceExactA != null) {
            selectedPieceExactB = new PieceExact(PositionID, PositionType.PieceInBoard);

            if(!selectedPieceExactB.equal(selectedPieceExactA))
                PerformComplexMove(new PieceMovement(selectedPieceExactA, selectedPieceExactB));
            if(selectedPieceExactA.getType()!=PositionType.PieceInWait)
                darkenPieceBackground(selectedPieceExactA);
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
        pieceAdapter.removePiece(puzzlePieces.indexOf(piece));
    }
    private void DeletePieceBoard(int position){
        UpdatePieceBoard(position, null);
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
            DeletePieceBoard(origin.getPostionID());
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
            System.console().printf("Move " + movement.Origin.getPostionID() + " to " + movement.Target.getPostionID() + "\n");
            System.console().printf("     " + movement.Origin.getType() + " to " + movement.Target.getType() + "\n");
        }
        System.console().printf("undo complete\n");
        ClearHighLight();
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

    @Override
    public void onPieceLongClick(View view, int PositionID) {
        // Prevent long click from list if board is full
        int position = getAdapterPiecePosition(PositionID);
        if(puzzlePieces.isEmpty()) return;

        if(selectedPieceExactA!=null){
            darkenPieceBackground(selectedPieceExactA);
        }
        PuzzlePiece piece = pieceAdapter.getPiece(position);
        selectedPieceExactA = new PieceExact(piece.getOriginalIndex(), PositionType.PieceInWait);

        ClipData data = ClipData.newPlainText("Piece","PieceInWait");

        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(data, myShadow, view, 0);

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
    public void ClearHighLight(){
        for (int i = 0; i < puzzleBoard.getChildCount(); i++) {
            View cell = puzzleBoard.getChildAt(i);
            if (((ImageView) cell).getDrawable() == null) {
                cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            } else {
                cell.setBackground(null);
            }
        }
        for(int i = 0;i<piecesRecyclerView.getChildCount();i++){
            piecesRecyclerView.getChildAt(i).setVisibility(View.VISIBLE);
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
            if(puzzlePiecesDone[i] == null || !puzzlePiecesDone[i].isCorrect()){
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