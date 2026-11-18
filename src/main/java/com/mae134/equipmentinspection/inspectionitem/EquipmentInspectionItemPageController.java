package com.mae134.equipmentinspection.inspectionitem;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EquipmentInspectionItemPageController {

  private final EquipmentService equipmentService;
  private final EquipmentInspectionItemService inspectionItemService;

  public EquipmentInspectionItemPageController(
      EquipmentService equipmentService, EquipmentInspectionItemService inspectionItemService) {
    this.equipmentService = equipmentService;
    this.inspectionItemService = inspectionItemService;
  }

  @GetMapping("/equipment/{equipmentId}/inspection-items")
  public String showInspectionItemSettings(@PathVariable Long equipmentId, Model model) {

    populatePageModel(equipmentId, model);
    model.addAttribute("inspectionItemForm", new EquipmentInspectionItemForm());

    return "inspection-item-settings";
  }

  @PostMapping("/equipment/{equipmentId}/inspection-items")
  public String createInspectionItem(
      @PathVariable Long equipmentId,
      @Valid @ModelAttribute("inspectionItemForm") EquipmentInspectionItemForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {

    if (!bindingResult.hasErrors()) {
      try {
        inspectionItemService.save(form.toRequest(equipmentId));
      } catch (DuplicateResourceException e) {
        bindingResult.rejectValue("name", "duplicate", "点検項目名は既に登録されています");
      } catch (IllegalArgumentException e) {
        bindingResult.reject("invalidSettings", e.getMessage());
      }
    }

    if (bindingResult.hasErrors()) {
      populatePageModel(equipmentId, model);
      return "inspection-item-settings";
    }

    redirectAttributes.addFlashAttribute("successMessage", "点検項目を登録しました");

    return "redirect:/equipment/" + equipmentId + "/inspection-items";
  }

  private void populatePageModel(Long equipmentId, Model model) {

    EquipmentResponse equipment =
        equipmentService
            .findById(equipmentId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Equipment not found: " + equipmentId));

    List<EquipmentInspectionItemResponse> inspectionItems =
        inspectionItemService.findByEquipmentId(equipmentId);

    model.addAttribute("equipment", equipment);
    model.addAttribute("inspectionItems", inspectionItems);
  }

  @GetMapping("/equipment/{equipmentId}/inspection-items/{itemId}/edit")
  public String showEditInspectionItem(
      @PathVariable Long equipmentId, @PathVariable Long itemId, Model model) {

    populatePageModel(equipmentId, model);

    EquipmentInspectionItemResponse item = inspectionItemService.findById(itemId);

    // URLの設備IDと点検項目の所属設備IDが一致するか確認
    if (!item.equipmentId().equals(equipmentId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Inspection item not found for equipment: " + equipmentId);
    }

    model.addAttribute("inspectionItemForm", EquipmentInspectionItemForm.fromResponse(item));

    model.addAttribute("editingItemId", itemId);

    return "inspection-item-settings";
  }

  @PostMapping("/equipment/{equipmentId}/inspection-items/{itemId}/edit")
  public String updateInspectionItem(
      @PathVariable Long equipmentId,
      @PathVariable Long itemId,
      @Valid @ModelAttribute("inspectionItemForm") EquipmentInspectionItemForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {

    // 編集対象が指定設備に所属しているか確認
    EquipmentInspectionItemResponse item = inspectionItemService.findById(itemId);

    if (!item.equipmentId().equals(equipmentId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Inspection item not found for equipment: " + equipmentId);
    }

    if (!bindingResult.hasErrors()) {
      try {
        inspectionItemService.update(itemId, form.toRequest(equipmentId));
      } catch (DuplicateResourceException e) {
        bindingResult.rejectValue("name", "duplicate", "点検項目名は既に登録されています");
      } catch (IllegalArgumentException e) {
        bindingResult.reject("invalidSettings", e.getMessage());
      }
    }

    if (bindingResult.hasErrors()) {
      populatePageModel(equipmentId, model);
      model.addAttribute("editingItemId", itemId);
      return "inspection-item-settings";
    }

    redirectAttributes.addFlashAttribute("successMessage", "点検項目を更新しました");

    return "redirect:/equipment/" + equipmentId + "/inspection-items";
  }
}
