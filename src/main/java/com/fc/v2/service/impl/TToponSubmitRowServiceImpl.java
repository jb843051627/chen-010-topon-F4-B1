package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TToponSubmitRowServiceImpl.class);

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
        if (rows == null || rows.isEmpty()) {
            log.info("空批次，无需入库 batchNo={}", batchNo);
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            log.warn("批次行数超上限，整批不入库 batchNo={} rows={} max={}", batchNo, rows.size(), MAX_ROWS);
            return -1;
        }
        if (batchNo == null || batchNo.trim().isEmpty()) {
            log.warn("批次号缺失，整批不入库 rows={}", rows.size());
            return 0;
        }
        // 同一批次重复提交不得重复入库
        Integer exists = this.toponSubmitRowMapper.selectCount(
                new QueryWrapper<TToponSubmitRow>().eq("batch_no", batchNo));
        if (exists != null && exists.intValue() > 0) {
            log.warn("批次已提交过，不得重复入库 batchNo={}", batchNo);
            return 0;
        }
        int ok = 0;
        for (TToponSubmitRow r : rows) {
            r.setBatchNo(batchNo);
            if (isInvalid(r)) {
                // 非法行记入失败明细，保留原册内行次
                r.setStatus(Integer.valueOf(STATUS_FAIL));
                this.toponSubmitRowMapper.insert(r);
                log.warn("行校验未过，记入失败明细 batchNo={} rowNo={} itemCode={}",
                        batchNo, r.getRowNo(), r.getItemCode());
            } else {
                r.setStatus(Integer.valueOf(STATUS_OK));
                this.toponSubmitRowMapper.insert(r);
                ok++;
            }
        }
        log.info("批次提交完成 batchNo={} 总数={} 成功={}", batchNo, rows.size(), ok);
        return ok;
    }

    /** 行校验：代号不可空、数量须为正 */
    private static boolean isInvalid(TToponSubmitRow r) {
        return r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                || r.getQty() == null
                || r.getQty().compareTo(BigDecimal.ZERO) <= 0;
    }

    @Override
    public List<TToponSubmitRow> listErrors(String batchNo) {
        return this.toponSubmitRowMapper.selectList(new QueryWrapper<TToponSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).orderByAsc("row_no"));
    }
}
