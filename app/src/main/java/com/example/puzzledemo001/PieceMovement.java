package com.example.puzzledemo001;

public class PieceMovement {
    PieceExact Origin;
    PieceExact Target;

    public PieceMovement(PieceExact origin, PieceExact target) {
        this.Origin = origin;
        this.Target = target;
    }
    public PieceMovement(PositionType originType, int originPosition, PositionType targetType, int targetPosition) {
        this.Origin = new PieceExact(originPosition, originType);
        this.Target = new PieceExact(targetPosition, targetType);
    }
    public void setUndo(){
        PieceExact tmp = Origin;
        Origin = Target;
        Target = tmp;
    }
}
