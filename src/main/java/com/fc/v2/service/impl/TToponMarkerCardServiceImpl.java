package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponMarkerCardMapper;
import com.fc.v2.model.auto.TToponMarkerCard;
import com.fc.v2.service.ITToponMarkerCardService;

/**
 * 界桩标志补办事务卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TToponMarkerCardServiceImpl implements ITToponMarkerCardService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TToponMarkerCardMapper toponMarkerCardMapper;

    @Override
    public TToponMarkerCard selectTToponMarkerCardById(Long id) {
        return this.toponMarkerCardMapper.selectById(id);
    }

    @Override
    public List<TToponMarkerCard> selectTToponMarkerCardList(QueryWrapper<TToponMarkerCard> queryWrapper) {
        return this.toponMarkerCardMapper.selectList(queryWrapper);
    }

    @Override
    public TToponMarkerCard advance(Long id, String remark) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.toponMarkerCardMapper.updateById(r);
        return r;
    }

    @Override
    public TToponMarkerCard rollback(Long id, String remark) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.toponMarkerCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.toponMarkerCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.toponMarkerCardMapper.deleteById(id) > 0;
    }

}
