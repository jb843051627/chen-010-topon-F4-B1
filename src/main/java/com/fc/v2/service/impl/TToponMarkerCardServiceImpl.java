package com.fc.v2.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TToponMarkerCardServiceImpl.class);

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
        if (isDeleted(r) || isTerminal(r)) {
            log.warn("卡已办结或已删除，不得再推进 id={}", id);
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(Integer.valueOf(next));
        // 挪到顶格即落定封存，状态栏不得蹲在原地
        r.setStatus(Integer.valueOf(next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE));
        r.setLastAction(remark);
        this.toponMarkerCardMapper.updateById(r);
        log.info("推进一档 id={} stage={} status={}", id, r.getStage(), r.getStatus());
        return r;
    }

    @Override
    public TToponMarkerCard rollback(Long id, String remark) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isDeleted(r)) {
            log.warn("卡已删除，不得回退 id={}", id);
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        r.setStage(Integer.valueOf(Math.max(st - 1, 0)));
        // 与推进的封存核齐：退回即回到在办
        r.setStatus(Integer.valueOf(STATUS_ACTIVE));
        r.setLastAction(remark);
        this.toponMarkerCardMapper.updateById(r);
        log.info("回退一档 id={} stage={}", id, r.getStage());
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TToponMarkerCard r = this.toponMarkerCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        if (isDeleted(r) || isTerminal(r)) {
            log.warn("卡已办结或已删除，不得再改 id={}", id);
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
        if (isDeleted(r) || isTerminal(r)) {
            log.warn("卡已办结或已删除，不得删除 id={}", id);
            return false;
        }
        // 逻辑删除：记录留痕可查
        r.setDelFlag(Integer.valueOf(1));
        return this.toponMarkerCardMapper.updateById(r) > 0;
    }

    private static boolean isTerminal(TToponMarkerCard r) {
        return r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL;
    }

    private static boolean isDeleted(TToponMarkerCard r) {
        return r.getDelFlag() != null && r.getDelFlag().intValue() != 0;
    }

}
