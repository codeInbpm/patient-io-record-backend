package com.patient.io.service;

import com.patient.io.dto.ClaimDutyBedsDTO;
import com.patient.io.dto.NurseAssignBedsDTO;
import com.patient.io.dto.NurseSaveDTO;
import com.patient.io.vo.NurseAssignmentVO;
import com.patient.io.vo.NurseManageVO;

import java.util.List;

public interface NurseService {

    /**
     * 护士长查询护士名册（含负责床位/病患统计）
     */
    List<NurseManageVO> listManageNurses();

    /**
     * 查询在职护士选项列表（供交班选择接班人，所有角色可查）
     */
    List<NurseManageVO> listNurseOptions(Long wardId);

    /**
     * 保存护士（新增或编辑）
     */
    NurseManageVO saveNurse(NurseSaveDTO dto);

    /**
     * 切换护士在职/停用状态
     */
    void toggleNurseStatus(Long nurseId);

    /**
     * 护士长协助护士解绑微信账号
     */
    void unbindNurseWechat(Long nurseId);

    /**
     * 查询指定病房下的床位分配情况（包含该护士选中状态）
     */
    NurseAssignmentVO getNurseWardAssignments(Long nurseId, Long wardId);

    /**
     * 为护士批量分配负责床位及病患
     */
    void assignBeds(NurseAssignBedsDTO dto);

    /**
     * 当前登录护士查询自己当班的床位管辖情况
     */
    NurseAssignmentVO getMyDutyBedAssignments(Long wardId);

    /**
     * 护士当班自主认领/变更管辖床位（勾选接班不覆盖他人，取消勾选解除自身管辖）
     */
    void claimMyDutyBeds(ClaimDutyBedsDTO dto);
}
