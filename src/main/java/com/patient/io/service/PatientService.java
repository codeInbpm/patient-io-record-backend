package com.patient.io.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.patient.io.dto.NurseAssignDTO;
import com.patient.io.dto.PatientCreateDTO;
import com.patient.io.dto.PatientUpdateDTO;
import com.patient.io.entity.Patient;
import com.patient.io.vo.PatientVO;

import java.util.List;

public interface PatientService extends IService<Patient> {
    PatientVO createPatient(PatientCreateDTO createDTO);
    PatientVO updatePatient(PatientUpdateDTO updateDTO);
    void assignNurseToPatient(NurseAssignDTO assignDTO);
    List<PatientVO> listPatientsForCurrentRole(Long wardId);
    List<PatientVO> listPatientsForCurrentRole(Long wardId, Boolean myOnly);
    PatientVO getPatientDetail(Long patientId);
    PatientVO getMyBoundPatient();
    void dischargePatient(Long patientId);
    void unbindPatientWechat(Long patientId);
}
