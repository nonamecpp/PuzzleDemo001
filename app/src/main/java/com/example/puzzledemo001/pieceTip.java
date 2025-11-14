package com.example.puzzledemo001;

public class pieceTip {
    private boolean isUnused;
    private int OriginPosition;
    private int targetPosition;
    public pieceTip(boolean isUnused, int OriginPosition, int targetPosition){
        this.isUnused = isUnused;
        this.OriginPosition = OriginPosition;
        this.targetPosition = targetPosition;
    }
    public boolean getIsUnused(){
        return isUnused;
    }
    public int getOriginPosition(){
        return OriginPosition;
    }
    public int getTargetPosition(){
        return targetPosition;
    }
}
