package com.example.puzzledemo001;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageSplitter {

    /**
     * Overloaded method to split an image from a URI.
     */
    public static List<PuzzlePiece> splitImage(Context context, Uri imageUri, int difficulty) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }
            if (originalBitmap == null) return new ArrayList<>(); // Return empty if bitmap fails to decode

            return splitBitmap(originalBitmap, difficulty);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // Return empty list on any error
        }
    }

    /**
     * Overloaded method to split an image from a drawable resource.
     */
    public static List<PuzzlePiece> splitImage(Context context, int drawableId, int difficulty) {
        Bitmap originalBitmap = drawableToBitmap(context, drawableId);
        if (originalBitmap == null) {
            return new ArrayList<>();
        }
        return splitBitmap(originalBitmap, difficulty);
    }

    /**
     * The core splitting logic that works with a Bitmap.
     */
    private static List<PuzzlePiece> splitBitmap(Bitmap originalBitmap, int difficulty) {
        ArrayList<PuzzlePiece> pieces = new ArrayList<>();

        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        int divisibleWidth = (originalWidth / difficulty) * difficulty;
        int divisibleHeight = (originalHeight / difficulty) * difficulty;

        if (divisibleWidth == 0 || divisibleHeight == 0) {
            originalBitmap.recycle();
            return pieces;
        }

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, divisibleWidth, divisibleHeight, true);
        if (originalBitmap != scaledBitmap) {
            originalBitmap.recycle(); // Recycle original if it was scaled
        }

        int pieceWidth = divisibleWidth / difficulty;
        int pieceHeight = divisibleHeight / difficulty;

        int pieceNumber = 0;
        for (int row = 0; row < difficulty; row++) {
            for (int col = 0; col < difficulty; col++) {
                int x = col * pieceWidth;
                int y = row * pieceHeight;

                Bitmap pieceBitmap = Bitmap.createBitmap(scaledBitmap, x, y, pieceWidth, pieceHeight);
                PuzzlePiece piece = new PuzzlePiece(pieceBitmap, pieceNumber);
                pieces.add(piece);
                pieceNumber++;
            }
        }

        scaledBitmap.recycle();
        return pieces;
    }


    /**
     * Converts a drawable resource to a Bitmap. (Internal helper)
     */
    private static Bitmap drawableToBitmap(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return null;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) {
            width = 600;
            height = 600;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
