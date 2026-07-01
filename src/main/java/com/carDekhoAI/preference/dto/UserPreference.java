package com.carDekhoAI.preference.dto;

public record UserPreference(
        Long budget,
        String fuelType,
        String bodyType,
        String transmission,
        String drivingPattern,
        Integer familySize,
        String priority,
        String brandPreference,
        Integer groundClearance,
        Integer bootSpace
) {

    public boolean isComplete() {
        return budget != null
                && fuelType != null
                && bodyType != null
                && transmission != null
                && drivingPattern != null
                && familySize != null
                && priority != null;
    }
}
