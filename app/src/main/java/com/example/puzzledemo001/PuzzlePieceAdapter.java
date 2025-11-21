package com.example.puzzledemo001;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class PuzzlePieceAdapter extends RecyclerView.Adapter<PuzzlePieceAdapter.PieceViewHolder> {

    private List<PuzzlePiece> pieces;

    // Listener for when an item is clicked or dragged
    public interface OnPieceClickListener {
        void onPieceClick(View view, int position);
        void onPieceLongClick(View view, int position);
    }
    private OnPieceClickListener clickListener;

    public void setClickListener(OnPieceClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public PuzzlePieceAdapter(List<PuzzlePiece> pieces) {
        this.pieces = pieces;
    }

    @NonNull
    @Override
    public PieceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_puzzle_piece, parent, false);
        return new PieceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PieceViewHolder holder, int position) {
        PuzzlePiece piece = pieces.get(position);
        holder.pieceImageView.setImageBitmap(piece.getPieceBitmap());

        // Set the tag to the position, so we can identify the piece during drag
        holder.itemView.setTag(piece.getOriginalIndex());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPieceClick(v, piece.getOriginalIndex());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPieceLongClick(v, piece.getOriginalIndex());
            }
            return true;
        });
    }

    public PuzzlePiece getPiece(int position) {
        if (position >= 0 && position < pieces.size()) {
            return pieces.get(position);
        }
        return null;
    }
    
    public void removePiece(int position) {
        if (position >= 0 && position < pieces.size()) {
            pieces.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, pieces.size());
        }
    }
    public void addPiece(PuzzlePiece piece){
        if(piece != null){
            pieces.add(piece);
            notifyItemInserted(pieces.size() - 1);
//            notifyItemRangeChanged(pieces.size()-1, pieces.size());
        }
    }
    
    @Override
    public int getItemCount() {
        return pieces.size();
    }

    public static class PieceViewHolder extends RecyclerView.ViewHolder {
        ImageView pieceImageView;

        public PieceViewHolder(@NonNull View itemView) {
            super(itemView);
            pieceImageView = itemView.findViewById(R.id.pieceImageView);
        }
    }
}
