package com.mae134.equipmentinspection.inspectionitem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record EquipmentInspectionItemRequest(
    @NotNull(message = "設備IDは必須です") Long equipmentId,
    @NotBlank(message = "点検項目名は必須です") @Size(max = 100, message = "点検項目名は100文字以内で入力してください")
        String name,
    @NotNull(message = "点検項目種別は必須です") InspectionItemType type,
    @Size(max = 20, message = "単位は20文字以内で入力してください") String unit,
    BigDecimal minValue,
    BigDecimal maxValue,
    Boolean normalBooleanValue,
    @Size(max = 500, message = "説明は500文字以内で入力してください") String description,
    @NotNull(message = "表示順は必須です") Integer displayOrder,
    @NotNull(message = "有効状態は必須です") Boolean active) {}
