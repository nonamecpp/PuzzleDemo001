package com.example.puzzledemo001;

import java.util.ArrayList;
import java.util.List;

public class ComplexMovement {
    private List<PieceMovement> record;
    private int iterator;
    public ComplexMovement(){
        record = new ArrayList<>();
    }

    public void addMove(PieceMovement movement){
        record.add(movement);
    }
    public PieceMovement begin(){
        iterator = 0;
        return record.get(iterator);
    }
    public PieceMovement end(){
        iterator = record.size()-1;
        return record.get(iterator);
    }
    public PieceMovement next(){
        if(iterator < record.size()-1){
            iterator++;
            return record.get(iterator);
        }
        return null;
    }
    public PieceMovement previous(){
        if(iterator > 0){
            iterator--;
            return record.get(iterator);
        }
        return null;
    }
    public boolean hasNext(){
        return iterator < record.size()-1;
    }
    public boolean hasPrevious(){
        return iterator > 0;
    }

}
