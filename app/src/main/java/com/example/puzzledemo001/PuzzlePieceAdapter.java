package com.example.puzzledemo001;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class PuzzlePieceAdapter extends RecyclerView.Adapter<PuzzlePieceAdapter.PieceViewHolder> {

    // 保存所有拼图块的数据列表
    private List<PuzzlePiece> pieces;

    // 定义点击 / 长按回调接口，供外部处理交互
    public interface OnPieceClickListener {
        void onPieceClick(View view, int position);
        void onPieceLongClick(View view, int position);
    }
    // 外部传入的监听器实例
    private OnPieceClickListener clickListener;

    // 设置监听器，供 Activity/Fragment 调用
    public void setClickListener(OnPieceClickListener clickListener) {
        this.clickListener = clickListener;
    }

    // 通过构造方法传入拼图块列表
    public PuzzlePieceAdapter(List<PuzzlePiece> pieces) {
        this.pieces = pieces;
    }

    @NonNull
    @Override
    public PieceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载单个拼图块的布局 item_puzzle_piece
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_puzzle_piece, parent, false);
        return new PieceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PieceViewHolder holder, int position) {
        // 绑定当前位置的拼图块数据
        PuzzlePiece piece = pieces.get(position);
        holder.pieceImageView.setImageBitmap(piece.getPieceBitmap());
        // 设置背景颜色，避免透明区域看不清
        holder.pieceImageView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.background_light));

        // 使用原始索引作为 tag，方便拖拽时识别是哪一块
        holder.itemView.setTag(piece.getOriginalIndex());

        // 点击事件：把原始索引回调给外部
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPieceClick(v, piece.getOriginalIndex());
            }
        });

        // 长按事件：通常用于开始拖拽
        holder.itemView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPieceLongClick(v, piece.getOriginalIndex());
            }
            return true;
        });
    }

    // 根据当前位置获取对应的拼图块对象
    public PuzzlePiece getPiece(int position) {
        if (position >= 0 && position < pieces.size()) {
            return pieces.get(position);
        }
        return null;
    }

    // 从列表中删除一块拼图并刷新界面
    public void removePiece(int position) {
        if (position >= 0 && position < pieces.size()) {
            pieces.remove(position);
            notifyItemRemoved(position);                 // 通知该位置被删除
            notifyItemRangeChanged(position, pieces.size()); // 更新后续项的位置
        }
    }

    // 向列表末尾添加一块新的拼图并刷新界面
    public void addPiece(PuzzlePiece piece){
        if(piece != null){
            pieces.add(piece);
            notifyItemInserted(pieces.size() - 1);      // 通知新插入的位置
//            notifyItemRangeChanged(pieces.size()-1, pieces.size());
        }
    }

    @Override
    public int getItemCount() {
        return pieces.size();
    }

    // ViewHolder：持有单个 item 里的视图引用
    public static class PieceViewHolder extends RecyclerView.ViewHolder {
        ImageView pieceImageView;

        public PieceViewHolder(@NonNull View itemView) {
            super(itemView);
            // 绑定布局中用于显示拼图块图片的 ImageView
            pieceImageView = itemView.findViewById(R.id.pieceImageView);
        }
    }
}

