package com.fc.v2.service.impl;

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

    @Override
    public TToponNameBill approve(Long id, String approver, String comment) {
        TToponNameBill r = this.toponNameBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        r.setNodeNo(Integer.valueOf((r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1));
        r.setStatus(Integer.valueOf(r.getNodeNo() >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING));
        this.toponNameBillMapper.updateById(r);
        return r;
    }

    @Override
    public TToponNameBill reject(Long id, String approver, String comment) {
        TToponNameBill r = this.toponNameBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        this.toponNameBillMapper.updateById(r);
        return r;
    }

    @Override
    public TToponNameBill rollback(Long id, String comment) {
        TToponNameBill r = this.toponNameBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        this.toponNameBillMapper.updateById(r);
        return r;
    }
}
