package com.lefoxxy.encumbrance.weight;

public record WeightSnapshot(double currentWeight, double maxWeightBeforePenalty, double penaltyRatio) {
    public boolean isEncumbered() {
        return penaltyRatio > 0.0D;
    }
}
