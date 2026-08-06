package com.mae134.equipmentinspection.equipment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EquipmentRequest(
    @NotBlank(message = "設備コードは必須です") @Size(max = 20, message = "設備コードは20文字以内で入力してください")
        String equipmentCode,
    @NotBlank(message = "設備名は必須です") @Size(max = 100, message = "設備名は100文字以内で入力してください") String name,
    @Size(max = 100, message = "メーカーは100文字以内で入力してください") String manufacturer,
    @Size(max = 100, message = "型番は100文字以内で入力してください") String model,
    @Size(max = 100, message = "設置場所は100文字以内で入力してください") String location,
    LocalDate installedDate,
    @Min(value = 1, message = "点検周期は1日以上で入力してください") Integer inspectionCycleDays,
    @Size(max = 500, message = "備考は500文字以内で入力してください") String description,
    Boolean active) {}
