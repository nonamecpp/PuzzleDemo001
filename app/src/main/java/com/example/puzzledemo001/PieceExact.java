package com.example.puzzledemo001;

public class PieceExact{
    private int PostionID;
    private PositionType type;

    public PieceExact(int PostionID, PositionType type){
        this.PostionID = PostionID;
        this.type = type;
    }
    public boolean equal(PieceExact piece){
        if(piece == null)return false;
        return piece.PostionID == this.PostionID && piece.type == this.type;
    }
    public int getPostionID(){
        return PostionID;
    }
    public PositionType getType(){
        return type;
    }

}