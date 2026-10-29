package com.mae134.equipmentinspection.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "APIエラーレスポンス")
public record ApiErrorResponse(
    @Schema(description = "HTTPステータスコード") int status,
    @Schema(description = "エラーメッセージ") String message) {}
