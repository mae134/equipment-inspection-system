
package com.mae134.equipmentinspection.equipment;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EquipmentPageController {

  private final EquipmentService equipmentService;

  public EquipmentPageController(EquipmentService equipmentService) {
    this.equipmentService = equipmentService;
  }

  @GetMapping("/equipment/new")
  public String showCreateForm(Model model) {
    model.addAttribute("equipmentForm", new EquipmentForm());
    return "equipment-create";
  }

  @PostMapping("/equipment")
  public String createEquipment(
      @Valid @ModelAttribute("equipmentForm") EquipmentForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {
      return "equipment-create";
    }

    try {
      equipmentService.save(form.toRequest());
    } catch (DuplicateResourceException e) {
      bindingResult.rejectValue(
          "equipmentCode",
          "duplicate",
          "設備コードは既に登録されています");
      return "equipment-create";
    }

    redirectAttributes.addFlashAttribute(
        "successMessage", "設備を登録しました");

    return "redirect:/dashboard";
  }
}
