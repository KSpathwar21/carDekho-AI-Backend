package com.carDekhoAI.sql.validator;

public record SqlValidationResult(boolean valid, String reason) {

    public static SqlValidationResult ok() {
        return new SqlValidationResult(true, null);
    }

    public static SqlValidationResult reject(String reason) {
        return new SqlValidationResult(false, reason);
    }
}
