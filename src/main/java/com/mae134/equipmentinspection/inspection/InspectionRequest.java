package com.mae134.equipmentinspection.inspection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record InspectionRequest(
    @NotNull(message = "設備IDは必須です") Long equipmentId,
    @NotNull(message = "ユーザーIDは必須です") Long userId,
    @NotNull(message = "点検日時は必須です") LocalDateTime inspectionAt,
    @Size(max = 500, message = "コメントは500文字以内で入力してください") String comment) {}
