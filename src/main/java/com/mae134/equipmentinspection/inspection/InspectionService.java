package com.mae134.equipmentinspection.inspection;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.user.User;
import com.mae134.equipmentinspection.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class InspectionService {

  private final InspectionRepository inspectionRepository;
  private final EquipmentRepository equipmentRepository;
  private final UserRepository userRepository;

  public InspectionService(
      InspectionRepository inspectionRepository,
      EquipmentRepository equipmentRepository,
      UserRepository userRepository) {

    this.inspectionRepository = inspectionRepository;
    this.equipmentRepository = equipmentRepository;
    this.userRepository = userRepository;
  }

  private InspectionResponse toResponse(Inspection inspection) {
    return new InspectionResponse(
        inspection.getId(),
        inspection.getEquipment().getId(),
        inspection.getUser().getId(),
        inspection.getInspectionAt(),
        inspection.getComment(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt());
  }

  public InspectionResponse save(InspectionRequest request) {

    Equipment equipment = equipmentRepository.findById(request.equipmentId()).orElseThrow();

    User user = userRepository.findById(request.userId()).orElseThrow();

    Inspection inspection = new Inspection();

    inspection.setEquipment(equipment);
    inspection.setUser(user);
    inspection.setInspectionAt(request.inspectionAt());
    inspection.setComment(request.comment());

    Inspection savedInspection = inspectionRepository.save(inspection);

    return toResponse(savedInspection);
  }

  public List<InspectionResponse> findAll() {
    return inspectionRepository.findAll().stream().map(this::toResponse).toList();
  }

  public Optional<InspectionResponse> findById(Long id) {
    return inspectionRepository.findById(id).map(this::toResponse);
  }

  public InspectionResponse update(Long id, InspectionRequest request) {

    Inspection inspection = inspectionRepository.findById(id).orElseThrow();

    Equipment equipment = equipmentRepository.findById(request.equipmentId()).orElseThrow();

    User user = userRepository.findById(request.userId()).orElseThrow();

    inspection.setEquipment(equipment);
    inspection.setUser(user);
    inspection.setInspectionAt(request.inspectionAt());
    inspection.setComment(request.comment());

    Inspection updatedInspection = inspectionRepository.save(inspection);

    return toResponse(updatedInspection);
  }

  public void delete(Long id) {
    Inspection inspection = inspectionRepository.findById(id).orElseThrow();

    inspectionRepository.delete(inspection);
  }
}
