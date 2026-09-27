package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TToponPlateCard;

import java.util.List;

/**
 * 门楼牌号段建档卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITToponPlateCardService {

    /** 按主键查询 */
    TToponPlateCard selectTToponPlateCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TToponPlateCard> selectTToponPlateCardList(Wrapper<TToponPlateCard> queryWrapper);

    /** 新增 */
    int insertTToponPlateCard(TToponPlateCard record);

    /** 修改 */
    int updateTToponPlateCard(TToponPlateCard record);

    /** 批量删除 */
    int deleteTToponPlateCardByIds(String ids);

    /** 按主键删除 */
    int deleteTToponPlateCardById(Long id);
}
