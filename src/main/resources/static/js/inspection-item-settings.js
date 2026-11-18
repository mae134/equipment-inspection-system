document.addEventListener("DOMContentLoaded", () => {
  const inspectionItemTypeSelect =
    document.getElementById("inspectionItemType");
  const numericSettings = document.getElementById("numeric-settings");
  const booleanSettings = document.getElementById("boolean-settings");

  function updateSettingsVisibility() {
    const isNumeric = inspectionItemTypeSelect.value === "NUMERIC";
    const isBoolean = inspectionItemTypeSelect.value === "BOOLEAN";

    numericSettings.hidden = !isNumeric;
    numericSettings.disabled = !isNumeric;

    booleanSettings.hidden = !isBoolean;
    booleanSettings.disabled = !isBoolean;
  }

  inspectionItemTypeSelect.addEventListener("change", updateSettingsVisibility);

  // 編集画面では既存の種類に応じて初期表示を切り替える
  updateSettingsVisibility();
});
