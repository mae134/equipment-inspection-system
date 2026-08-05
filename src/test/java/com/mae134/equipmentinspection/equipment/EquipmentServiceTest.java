package com.mae134.equipmentinspection.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.exception.DuplicateResourceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

  @Mock private EquipmentRepository equipmentRepository;

  private EquipmentService equipmentService;

  @BeforeEach
  void setUp() {
    equipmentService = new EquipmentService(equipmentRepository);
  }

  @Test
  void saveShouldReturnCreatedEquipment() {
    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-001",
            "ポンプA",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "定期点検対象設備",
            true);

    Equipment savedEquipment = new Equipment();
    savedEquipment.setId(1L);
    savedEquipment.setEquipmentCode("EQ-001");
    savedEquipment.setName("ポンプA");
    savedEquipment.setManufacturer("ABC");
    savedEquipment.setModel("P-100");
    savedEquipment.setLocation("工場A");
    savedEquipment.setInstalledDate(LocalDate.of(2026, 8, 6));
    savedEquipment.setInspectionCycleDays(30);
    savedEquipment.setDescription("定期点検対象設備");
    savedEquipment.setActive(true);
    savedEquipment.setCreatedAt(LocalDateTime.of(2026, 8, 6, 10, 0));
    savedEquipment.setUpdatedAt(LocalDateTime.of(2026, 8, 6, 10, 0));

    when(equipmentRepository.existsByEquipmentCode("EQ-001")).thenReturn(false);
    when(equipmentRepository.save(any(Equipment.class))).thenReturn(savedEquipment);

    EquipmentResponse response = equipmentService.save(request);

    assertEquals(1L, response.id());
    assertEquals("EQ-001", response.equipmentCode());
    assertEquals("ポンプA", response.name());

    verify(equipmentRepository).existsByEquipmentCode("EQ-001");
    verify(equipmentRepository).save(any(Equipment.class));
  }

  @Test
  void saveShouldThrowExceptionWhenEquipmentCodeAlreadyExists() {
    EquipmentRequest request =
        new EquipmentRequest(
            "EQ-001",
            "ポンプA",
            "ABC",
            "P-100",
            "工場A",
            LocalDate.of(2026, 8, 6),
            30,
            "定期点検対象設備",
            true);

    when(equipmentRepository.existsByEquipmentCode("EQ-001")).thenReturn(true);

    DuplicateResourceException exception =
        assertThrows(DuplicateResourceException.class, () -> equipmentService.save(request));

    assertEquals("設備コードは既に登録されています: EQ-001", exception.getMessage());

    verify(equipmentRepository).existsByEquipmentCode("EQ-001");
    verify(equipmentRepository, never()).save(any(Equipment.class));
  }
}
