package com.patient.io.controller;

import com.patient.io.common.Result;
import com.patient.io.dto.NurseAssignDTO;
import com.patient.io.dto.PatientCreateDTO;
import com.patient.io.service.PatientService;
import com.patient.io.vo.PatientVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/patients")
public class PatientController {

    @Autowired
    private PatientService patientService;

    /**
     * 护士长：创建病患档案
     */
    @PostMapping("/create")
    public Result<PatientVO> createPatient(@RequestBody @Validated PatientCreateDTO createDTO) {
        return Result.success(patientService.createPatient(createDTO));
    }

    /**
     * 护士长/管理员：编辑病患档案
     */
    @PutMapping("/update")
    public Result<PatientVO> updatePatient(@RequestBody @Validated com.patient.io.dto.PatientUpdateDTO updateDTO) {
        return Result.success(patientService.updatePatient(updateDTO));
    }

    /**
     * 护士长：为病人/床位分配专属责任护士
     */
    @PostMapping("/assign-nurse")
    public Result<Void> assignNurseToPatient(@RequestBody @Validated NurseAssignDTO assignDTO) {
        patientService.assignNurseToPatient(assignDTO);
        return Result.success();
    }

    /**
     * 获取病患列表（支持多维度查询：不做查询限制，护士可查看所有患者，亦可通过 myOnly=true 仅看我负责的）
     */
    @GetMapping("/list")
    public Result<List<PatientVO>> listPatients(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false, defaultValue = "false") Boolean myOnly) {
        return Result.success(patientService.listPatientsForCurrentRole(wardId, myOnly));
    }

    @GetMapping("/{id}")
    public Result<PatientVO> getPatientDetail(@PathVariable Long id) {
        return Result.success(patientService.getPatientDetail(id));
    }

    @GetMapping("/my")
    public Result<PatientVO> getMyBoundPatient() {
        return Result.success(patientService.getMyBoundPatient());
    }

    /**
     * 护士长：办理病人出院（释放床位）
     */
    /**
     * 护士长：办理病人出院（释放床位）
     */
    @PostMapping("/{id}/discharge")
    public Result<Void> dischargePatient(@PathVariable Long id) {
        patientService.dischargePatient(id);
        return Result.success();
    }

    /**
     * 护士长/管理员：解绑患者微信
     */
    @PostMapping("/{id}/unbind-wechat")
    public Result<Void> unbindWechat(@PathVariable Long id) {
        patientService.unbindPatientWechat(id);
        return Result.success();
    }
}
