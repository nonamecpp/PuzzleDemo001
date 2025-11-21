package com.example.puzzledemo001;

public class PieceExact{
    private int PostionID;
    private PositionType type;

    public PieceExact(int PostionID, PositionType type){
        this.PostionID = PostionID;
        this.type = type;
    }
    public int getPostionID(){
        return PostionID;
    }
    public PositionType getType(){
        return type;
    }

}