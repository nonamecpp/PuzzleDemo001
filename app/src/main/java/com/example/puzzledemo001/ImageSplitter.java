package com.example.puzzledemo001;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class ImageSplitter {

    /**
     * A simplified and robust method to split an image.
     * It creates a single, perfectly-sized bitmap and cuts from it, avoiding complex scaling and recycling.
     */
    public static List<PuzzlePiece> splitImage(Context context, int drawableId, int difficulty) {
        ArrayList<PuzzlePiece> pieces = new ArrayList<>();

        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return pieces; // Return empty if drawable not found
        }

        // Define a base size. Using a fixed large size is safer than relying on intrinsic dimensions for shapes.
        int baseSize = 600; 

        // Calculate the final board size to be perfectly divisible.
        int boardWidth = (baseSize / difficulty) * difficulty;
        int boardHeight = (baseSize / difficulty) * difficulty;

        // This check is a safeguard, though with a base size of 600 it's unlikely to be triggered.
        if (boardWidth == 0 || boardHeight == 0) {
            return pieces; 
        }

        // 1. Create the final bitmap ONCE, with the perfect dimensions.
        Bitmap boardBitmap = Bitmap.createBitmap(boardWidth, boardHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(boardBitmap);

        // 2. Draw the source drawable onto the canvas, scaling it to fit the board dimensions.
        drawable.setBounds(0, 0, boardWidth, boardHeight);
        drawable.draw(canvas);

        // 3. Now, cut from this clean, perfectly-sized bitmap. All calculations will be exact.
        int pieceWidth = boardWidth / difficulty;
        int pieceHeight = boardHeight / difficulty;

        int pieceNumber = 0; // The missing variable declaration that caused the compile error.

        for (int row = 0; row < difficulty; row++) {
            for (int col = 0; col < difficulty; col++) {
                int x = col * pieceWidth;
                int y = row * pieceHeight;

                Bitmap pieceBitmap = Bitmap.createBitmap(boardBitmap, x, y, pieceWidth, pieceHeight);
                PuzzlePiece piece = new PuzzlePiece(pieceBitmap, pieceNumber);
                pieces.add(piece);
                pieceNumber++;
            }
        }

        // Let the Garbage Collector manage all bitmaps. No manual .recycle() calls.
        return pieces;
    }
}
