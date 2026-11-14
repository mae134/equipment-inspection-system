
package com.mae134.equipmentinspection.equipment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class EquipmentForm {

  @NotBlank(message = "設備コードは必須です")
  @Size(max = 20, message = "設備コードは20文字以内で入力してください")
  private String equipmentCode;

  @NotBlank(message = "設備名は必須です")
  @Size(max = 100, message = "設備名は100文字以内で入力してください")
  private String name;

  @Size(max = 100, message = "メーカーは100文字以内で入力してください")
  private String manufacturer;

  @Size(max = 100, message = "型番は100文字以内で入力してください")
  private String model;

  @Size(max = 100, message = "設置場所は100文字以内で入力してください")
  private String location;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate installedDate;

  @Min(value = 1, message = "点検周期は1日以上で入力してください")
  private Integer inspectionCycleDays;

  @Size(max = 500, message = "備考は500文字以内で入力してください")
  private String description;

  public EquipmentRequest toRequest() {
    return new EquipmentRequest(
        equipmentCode,
        name,
        manufacturer,
        model,
        location,
        installedDate,
        inspectionCycleDays,
        description,
        true);
  }
}
