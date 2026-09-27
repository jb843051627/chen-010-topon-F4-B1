package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponCheckTaskMapper;
import com.fc.v2.model.auto.TToponCheckTask;
import com.fc.v2.service.ITToponCheckTaskService;

/**
 * 定期检验派发条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TToponCheckTaskServiceImpl implements ITToponCheckTaskService {

    private static final Logger log = LoggerFactory.getLogger(TToponCheckTaskServiceImpl.class);

    /** 执行时段（墙钟小时）：[WIN_FROM, WIN_TO)，即 02:00 含 — 05:00 不含 */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TToponCheckTaskMapper toponCheckTaskMapper;

    @Override
    public TToponCheckTask selectTToponCheckTaskById(Long id) {
        return this.toponCheckTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /** 执行时段闸门：只在窗口小时 [WIN_FROM, WIN_TO) 内拾掇条目 */
    private static boolean inWindow(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= WIN_FROM && h < WIN_TO;
    }

    @Override
    public List<TToponCheckTask> listDue(Date at) {
        List<TToponCheckTask> due = new ArrayList<TToponCheckTask>();
        if (at == null || !inWindow(at)) {
            return due;
        }
        List<TToponCheckTask> all = this.toponCheckTaskMapper.selectList(new QueryWrapper<TToponCheckTask>());
        for (TToponCheckTask r : all) {
            // 尚未处理（待派）且未删除
            if (r.getDelFlag() != null && r.getDelFlag() != 0) {
                continue;
            }
            if (r.getStatus() != null && r.getStatus() != STATUS_WAIT) {
                continue;
            }
            // 到期（压线也算）：due_at <= at
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TToponCheckTask> due = listDue(at);
        if (due.isEmpty()) {
            log.info("本轮无到期条目 at={}", at == null ? "null" : ts(at));
            return 0;
        }
        int ok = 0;
        for (TToponCheckTask r : due) {
            try {
                if (r.getAmount() != null && r.getAmount().signum() >= 0) {
                    r.setStatus(Integer.valueOf(STATUS_DONE));
                    ok++;
                } else {
                    r.setStatus(Integer.valueOf(STATUS_FAIL));
                    log.warn("条目派不出去 itemNo={} amount={}", r.getItemNo(), r.getAmount());
                }
                this.toponCheckTaskMapper.updateById(r);
            } catch (Exception e) {
                // 单条撂挑子不得拖垮整轮：记下、跳过、继续
                log.error("单条处理失败，跳过继续 id={} itemNo={}", r.getId(), r.getItemNo(), e);
            }
        }
        log.info("本轮派发结束 at={} 到期={} 成功={}", ts(at), Integer.valueOf(due.size()), Integer.valueOf(ok));
        return ok;
    }
}
