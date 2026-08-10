package com.mae134.equipmentinspection.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import com.mae134.equipmentinspection.user.User;
import com.mae134.equipmentinspection.user.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

  @Mock private InspectionRepository inspectionRepository;

  @Mock private EquipmentRepository equipmentRepository;

  @Mock private UserRepository userRepository;

  private InspectionService inspectionService;

  @BeforeEach
  void setUp() {
    inspectionService =
        new InspectionService(inspectionRepository, equipmentRepository, userRepository);
  }

  @Test
  void saveShouldReturnCreatedInspection() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    User user = mock(User.class);
    when(user.getId()).thenReturn(1L);

    InspectionRequest request =
        new InspectionRequest(1L, 1L, LocalDateTime.of(2026, 8, 10, 16, 0), "異常なし");

    Inspection savedInspection = new Inspection();
    savedInspection.setId(1L);
    savedInspection.setEquipment(equipment);
    savedInspection.setUser(user);
    savedInspection.setInspectionAt(request.inspectionAt());
    savedInspection.setComment(request.comment());
    savedInspection.setCreatedAt(LocalDateTime.of(2026, 8, 10, 16, 1));
    savedInspection.setUpdatedAt(LocalDateTime.of(2026, 8, 10, 16, 1));

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    when(inspectionRepository.save(any(Inspection.class))).thenReturn(savedInspection);

    InspectionResponse response = inspectionService.save(request);

    assertEquals(1L, response.id());
    assertEquals(1L, response.equipmentId());
    assertEquals(1L, response.userId());
    assertEquals("異常なし", response.comment());

    verify(equipmentRepository).findById(1L);
    verify(userRepository).findById(1L);
    verify(inspectionRepository).save(any(Inspection.class));
  }

  @Test
  void saveShouldThrowExceptionWhenEquipmentDoesNotExist() {
    InspectionRequest request =
        new InspectionRequest(999L, 1L, LocalDateTime.of(2026, 8, 10, 16, 0), "異常なし");

    when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> inspectionService.save(request));

    verify(equipmentRepository).findById(999L);
    verify(userRepository, never()).findById(any());
    verify(inspectionRepository, never()).save(any(Inspection.class));
  }

  @Test
  void saveShouldThrowExceptionWhenUserDoesNotExist() {
    Equipment equipment = mock(Equipment.class);

    InspectionRequest request =
        new InspectionRequest(1L, 999L, LocalDateTime.of(2026, 8, 10, 16, 0), "異常なし");

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    when(userRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> inspectionService.save(request));

    verify(equipmentRepository).findById(1L);
    verify(userRepository).findById(999L);
    verify(inspectionRepository, never()).save(any(Inspection.class));
  }
}
