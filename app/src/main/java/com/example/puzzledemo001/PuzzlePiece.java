package com.example.puzzledemo001;

import android.graphics.Bitmap;

public class PuzzlePiece {

    private Bitmap pieceBitmap;
    private int originalIndex;
    private int currentIndex;

    public PuzzlePiece(Bitmap pieceBitmap, int originalIndex) {
        this.pieceBitmap = pieceBitmap;
        this.originalIndex = originalIndex;
        this.currentIndex = -1;
    }

    public Bitmap getPieceBitmap() {
        return pieceBitmap;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    // Method to check if the piece is in its correct final position
    public boolean isCorrect() {
        return currentIndex == originalIndex;
    }
}
