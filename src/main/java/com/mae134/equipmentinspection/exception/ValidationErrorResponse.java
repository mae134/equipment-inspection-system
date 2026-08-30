package com.mae134.equipmentinspection.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "入力値検証エラーレスポンス")
public record ValidationErrorResponse(
    @Schema(description = "HTTPステータスコード", example = "400") int status,
    @Schema(description = "エラーメッセージ", example = "Validation failed") String message,
    @Schema(description = "フィールドごとのエラー内容") Map<String, String> errors) {}
