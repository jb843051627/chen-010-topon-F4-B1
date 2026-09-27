package com.fc.v2.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TToponNameBillMapper;
import com.fc.v2.model.auto.TToponNameBill;
import com.fc.v2.service.ITToponNameBillService;

/**
 * 地名命名更名逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TToponNameBillServiceImpl implements ITToponNameBillService {

    private static final Logger log = LoggerFactory.getLogger(TToponNameBillServiceImpl.class);

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TToponNameBillMapper toponNameBillMapper;

    @Override
    public TToponNameBill selectTToponNameBillById(Long id) {
        return this.toponNameBillMapper.selectById(id);
    }

    /** 签批/否决同一把尺：签批人不可缺、单须在核 */
    private TToponNameBill signable(Long id, String approver) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TToponNameBill r = this.toponNameBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus().intValue() != STATUS_RUNNING) {
            log.warn("单已落定，不再受签 id={} status={}", id, r.getStatus());
            return null;
        }
        return r;
    }

    @Override
    public TToponNameBill approve(Long id, String approver, String comment) {
        TToponNameBill r = signable(id, approver);
        if (r == null) {
            return null;
        }
        int signCount = (r.getSignCount() == null ? 0 : r.getSignCount().intValue()) + 1;
        int need = r.getNeedCount() == null ? 1 : r.getNeedCount().intValue();
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode().intValue();
        if (mode == MODE_OR || signCount >= need) {
            // 任一人即过，或两名点齐：进一层，本层签数清零
            int node = (r.getNodeNo() == null ? 0 : r.getNodeNo().intValue()) + 1;
            r.setNodeNo(Integer.valueOf(node));
            r.setSignCount(Integer.valueOf(0));
            r.setStatus(Integer.valueOf(node >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING));
        } else {
            // 点齐未满：原地等下一票
            r.setSignCount(Integer.valueOf(signCount));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        this.toponNameBillMapper.updateById(r);
        log.info("签批落印 id={} approver={} node={} signCount={} status={}",
                id, approver, r.getNodeNo(), r.getSignCount(), r.getStatus());
        return r;
    }

    @Override
    public TToponNameBill reject(Long id, String approver, String comment) {
        TToponNameBill r = signable(id, approver);
        if (r == null) {
            return null;
        }
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.toponNameBillMapper.updateById(r);
        log.info("否决打回 id={} approver={}", id, approver);
        return r;
    }

    @Override
    public TToponNameBill rollback(Long id, String comment) {
        TToponNameBill r = this.toponNameBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        // 与核讫/打回核齐：退回即回到在核，本层签数重来
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        this.toponNameBillMapper.updateById(r);
        log.info("退回上一环节 id={} node={}", id, r.getNodeNo());
        return r;
    }
}
