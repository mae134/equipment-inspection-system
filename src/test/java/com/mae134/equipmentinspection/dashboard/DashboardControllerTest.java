package com.mae134.equipmentinspection.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

  @Mock private EquipmentService equipmentService;

  private DashboardController dashboardController;

  @BeforeEach
  void setUp() {
    dashboardController = new DashboardController(equipmentService);
  }

  @Test
  void showDashboardShouldDisplayAllEquipmentWhenKeywordIsNull() {
    List<EquipmentResponse> equipmentList = List.of();

    when(equipmentService.search(null)).thenReturn(equipmentList);

    Model model = new ConcurrentModel();

    String viewName = dashboardController.showDashboard(null, model);

    assertEquals("dashboard", viewName);
    assertEquals(equipmentList, model.getAttribute("equipmentList"));
    assertEquals(null, model.getAttribute("keyword"));

    verify(equipmentService).search(null);
  }

  @Test
  void showDashboardShouldDisplaySearchResultsWhenKeywordIsSpecified() {
    List<EquipmentResponse> equipmentList = List.of();

    when(equipmentService.search("ポンプ")).thenReturn(equipmentList);

    Model model = new ConcurrentModel();

    String viewName = dashboardController.showDashboard("ポンプ", model);

    assertEquals("dashboard", viewName);
    assertEquals(equipmentList, model.getAttribute("equipmentList"));
    assertEquals("ポンプ", model.getAttribute("keyword"));

    verify(equipmentService).search("ポンプ");
  }

  @Test
  void showDashboardShouldDisplayNormallyWhenSearchResultIsEmpty() {
    when(equipmentService.search("zzz")).thenReturn(List.of());

    Model model = new ConcurrentModel();

    String viewName = dashboardController.showDashboard("zzz", model);

    assertEquals("dashboard", viewName);
    assertEquals(List.of(), model.getAttribute("equipmentList"));
    assertEquals("zzz", model.getAttribute("keyword"));

    verify(equipmentService).search("zzz");
  }
}
