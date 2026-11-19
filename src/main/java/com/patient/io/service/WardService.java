package com.patient.io.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.patient.io.entity.Ward;
import com.patient.io.vo.WardOverviewVO;

import java.util.List;

public interface WardService extends IService<Ward> {
    List<Ward> listActiveWards();
    List<Ward> listWards(Integer status, Boolean all);
    Ward createWard(Ward ward);
    void updateWard(Ward ward);
    void deleteWard(Long wardId);
    WardOverviewVO getWardTodayOverview(Long wardId);
}
