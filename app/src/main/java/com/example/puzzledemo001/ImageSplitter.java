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
     * 从 URI 读取图片并按难度切分
     */
    public static List<PuzzlePiece> splitImage(Context context, Uri imageUri, int difficulty) {
        try {
            // 通过 ContentResolver 打开图片输入流
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            // 从输入流解码为 Bitmap
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();   // 及时关闭输入流，避免资源泄露
            }
            // 解码失败时返回空列表
            if (originalBitmap == null) return new ArrayList<>();

            // 使用核心切分逻辑
            return splitBitmap(originalBitmap, difficulty);

        } catch (Exception e) {
            e.printStackTrace();
            // 出现异常时返回空列表，避免崩溃
            return new ArrayList<>();
        }
    }

    /**
     * 从 drawable 资源读取图片并按难度切分
     */
    public static List<PuzzlePiece> splitImage(Context context, int drawableId, int difficulty) {
        // 将 drawable 转为 Bitmap
        Bitmap originalBitmap = drawableToBitmap(context, drawableId);
        if (originalBitmap == null) {
            // 获取失败返回空列表
            return new ArrayList<>();
        }
        return splitBitmap(originalBitmap, difficulty);
    }

    /**
     * 核心切分逻辑：根据难度把 Bitmap 均匀切成 difficulty*difficulty 块
     */
    private static List<PuzzlePiece> splitBitmap(Bitmap originalBitmap, int difficulty) {
        ArrayList<PuzzlePiece> pieces = new ArrayList<>();

        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        // 计算可被 difficulty 整除的宽高，避免最后一块尺寸不整齐
        int divisibleWidth = (originalWidth / difficulty) * difficulty;
        int divisibleHeight = (originalHeight / difficulty) * difficulty;

        // 若图片过小（无法按难度切分），回收并返回空列表
        if (divisibleWidth == 0 || divisibleHeight == 0) {
            originalBitmap.recycle();
            return pieces;
        }

        // 将原图按可整除尺寸缩放
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, divisibleWidth, divisibleHeight, true);
        if (originalBitmap != scaledBitmap) {
            originalBitmap.recycle(); // 若产生了新 Bitmap，则回收旧的
        }

        int pieceWidth = divisibleWidth / difficulty;
        int pieceHeight = divisibleHeight / difficulty;

        int pieceNumber = 0;
        // 双层循环，按行列切成小块
        for (int row = 0; row < difficulty; row++) {
            for (int col = 0; col < difficulty; col++) {
                int x = col * pieceWidth;
                int y = row * pieceHeight;

                // 从缩放后的大图中截取一块
                Bitmap pieceBitmap = Bitmap.createBitmap(scaledBitmap, x, y, pieceWidth, pieceHeight);
                // 创建拼图块对象，并记录其序号
                PuzzlePiece piece = new PuzzlePiece(pieceBitmap, pieceNumber);
                pieces.add(piece);
                pieceNumber++;
            }
        }

        // 所有小块生成后回收大图
        scaledBitmap.recycle();
        return pieces;
    }


    /**
     * 将 drawable 资源转换为 Bitmap（内部工具方法）
     */
    private static Bitmap drawableToBitmap(Context context, int drawableId) {
        // 获取 drawable 对象
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return null;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        // 若原始尺寸无效，则给一个默认尺寸
        if (width <= 0 || height <= 0) {
            width = 600;
            height = 600;
        }

        // 创建空白 Bitmap，并在其上绘制 drawable
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}

