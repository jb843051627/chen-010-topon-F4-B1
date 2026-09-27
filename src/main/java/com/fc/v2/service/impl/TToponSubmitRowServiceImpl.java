package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponSubmitRowMapper;
import com.fc.v2.model.auto.TToponSubmitRow;
import com.fc.v2.service.ITToponSubmitRowService;

/**
 * 地名数据汇交核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TToponSubmitRowServiceImpl implements ITToponSubmitRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TToponSubmitRowMapper toponSubmitRowMapper;

    @Override
    public TToponSubmitRow selectTToponSubmitRowById(Long id) {
        return this.toponSubmitRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TToponSubmitRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TToponSubmitRow> errors = new java.util.ArrayList<TToponSubmitRow>();
        int seq = 0;
        for (TToponSubmitRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.toponSubmitRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TToponSubmitRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.toponSubmitRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TToponSubmitRow> listErrors(String batchNo) {
        return this.toponSubmitRowMapper.selectList(new QueryWrapper<TToponSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
