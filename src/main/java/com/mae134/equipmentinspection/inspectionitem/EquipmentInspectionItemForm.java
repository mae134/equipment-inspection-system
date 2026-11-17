package com.mae134.equipmentinspection.inspectionitem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EquipmentInspectionItemForm {

  @NotBlank(message = "点検項目名は必須です")
  @Size(max = 100, message = "点検項目名は100文字以内で入力してください")
  private String name;

  @NotNull(message = "点検項目の種類は必須です")
  private InspectionItemType type;

  @Size(max = 20, message = "単位は20文字以内で入力してください")
  private String unit;

  private BigDecimal minValue;

  private BigDecimal maxValue;

  private Boolean normalBooleanValue;

  @Size(max = 500, message = "説明は500文字以内で入力してください")
  private String description;

  @NotNull(message = "表示順は必須です")
  @Min(value = 1, message = "表示順は1以上で入力してください")
  private Integer displayOrder;

  @NotNull(message = "有効状態は必須です")
  private Boolean active = true;

  public EquipmentInspectionItemRequest toRequest(Long equipmentId) {
    return new EquipmentInspectionItemRequest(
        equipmentId,
        name,
        type,
        unit,
        minValue,
        maxValue,
        normalBooleanValue,
        description,
        displayOrder,
        active);
  }
}
