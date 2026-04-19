package com.snappark.service;
import com.snappark.model.ParkingSlot;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;
import java.util.*;
import java.util.stream.Collectors;
public class SlotRecommendationService {
    private static SlotRecommendationService instance;
    private SlotRecommendationService() {}
    public static SlotRecommendationService getInstance() {
        if (instance == null) instance = new SlotRecommendationService();
        return instance;
    }
    public static class ScoredSlot {
        public final ParkingSlot slot;
        public final int totalScore, typeScore, proximityScore, spreadScore, floorScore;
        public final String reason;
        public ScoredSlot(ParkingSlot slot, int ts, int ps, int ss, int fs) {
            this.slot=slot; this.typeScore=ts; this.proximityScore=ps;
            this.spreadScore=ss; this.floorScore=fs;
            this.totalScore=ts+ps+ss+fs;
            List<String> r=new ArrayList<>();
            if(ts==40) r.add("Perfect type match");
            if(ps>=25) r.add("Near entrance");
            if(ss>=18) r.add("Balanced row");
            if(fs==10) r.add("Ground floor");
            this.reason=r.isEmpty()?"Available slot":String.join(" + ",r);
        }
    }
    public List<ScoredSlot> recommend(List<ParkingSlot> allSlots, VehicleType vt, int fl) {
        List<ParkingSlot> avail=allSlots.stream()
            .filter(s->s.getStatus()==SlotStatus.AVAILABLE)
            .collect(Collectors.toList());
        if(avail.isEmpty()) return Collections.emptyList();
        Map<Integer,Long> rowOcc=new HashMap<>();
        for(ParkingSlot s:allSlots)
            rowOcc.merge(getRow(s.getSlotNumber()),
                s.getStatus()==SlotStatus.OCCUPIED?1L:0L,Long::sum);
        long minOcc=rowOcc.values().stream().mapToLong(Long::longValue).min().orElse(0);
        List<ScoredSlot> scored=new ArrayList<>();
        for(ParkingSlot slot:avail)
            scored.add(new ScoredSlot(slot,
                scoreType(slot,vt),scoreProx(slot),
                scoreSpread(slot,rowOcc,minOcc),scoreFl(slot,fl)));
        scored.sort((a,b)->b.totalScore-a.totalScore);
        return scored.stream().limit(3).collect(Collectors.toList());
    }
    private int scoreType(ParkingSlot slot,VehicleType vt){
        if(slot.getType()==vt) return 40;
        if(vt==VehicleType.CAR&&slot.getType()==VehicleType.SUV) return 20;
        if(vt==VehicleType.SUV&&slot.getType()==VehicleType.CAR) return 15;
        return 0;
    }
    private int scoreProx(ParkingSlot slot){
        int n=getNum(slot.getSlotNumber());
        return n<=3?30:n<=6?22:n<=10?14:6;
    }
    private int scoreSpread(ParkingSlot slot,Map<Integer,Long> rowOcc,long minOcc){
        long o=rowOcc.getOrDefault(getRow(slot.getSlotNumber()),0L);
        return o==minOcc?20:o<=minOcc+1?14:6;
    }
    private int scoreFl(ParkingSlot slot,int fl){return slot.getFloor()==fl?10:4;}
    private int getNum(String s){try{return Integer.parseInt(s.split("-")[1]);}catch(Exception e){return 99;}}
    private int getRow(String s){return getNum(s)/5;}
}
