
package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;

@ExtendWith(MockitoExtension.class)
class EquipmentPageControllerTest {

  @Mock
  private EquipmentService equipmentService;

  private EquipmentPageController controller;

  @BeforeEach
  void setUp() {
    controller = new EquipmentPageController(equipmentService);
  }

  @Test
  void showCreateFormShouldDisplayEquipmentForm() {
    Model model = new ConcurrentModel();

    String viewName = controller.showCreateForm(model);

    assertEquals("equipment-create", viewName);
    assertNotNull(model.getAttribute("equipmentForm"));
  }

  @Test
  void createEquipmentShouldRedirectToDashboard() {
    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-003");
    form.setName("テスト設備");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    String viewName = controller.createEquipment(form, bindingResult, redirectAttributes);

    assertEquals("redirect:/dashboard", viewName);
    assertEquals(
        "設備を登録しました",
        redirectAttributes.getFlashAttributes().get("successMessage"));

    verify(equipmentService).save(any(EquipmentRequest.class));
  }

  @Test
  void createEquipmentShouldShowErrorWhenEquipmentCodeIsDuplicated() {
    EquipmentForm form = new EquipmentForm();
    form.setEquipmentCode("EQ-002");
    form.setName("重複テスト設備");

    BindingResult bindingResult = new BeanPropertyBindingResult(form, "equipmentForm");

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

    doThrow(new DuplicateResourceException("設備コードは既に登録されています"))
        .when(equipmentService)
        .save(any(EquipmentRequest.class));

    String viewName = controller.createEquipment(form, bindingResult, redirectAttributes);

    assertEquals("equipment-create", viewName);
    assertTrue(bindingResult.hasFieldErrors("equipmentCode"));

    assertEquals(
        "設備コードは既に登録されています",
        bindingResult.getFieldError("equipmentCode").getDefaultMessage());
  }

}
