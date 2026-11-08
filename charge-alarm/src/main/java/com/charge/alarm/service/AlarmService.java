package com.charge.alarm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charge.alarm.entity.AlarmRecord;
import com.charge.alarm.entity.AlarmRule;
import com.charge.alarm.mapper.AlarmRecordMapper;
import com.charge.alarm.mapper.AlarmRuleMapper;
import com.charge.common.exception.BizException;
import com.charge.common.security.JwtUtil;
import com.charge.common.security.TokenConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlarmService {

    private final AlarmRuleMapper ruleMapper;
    private final AlarmRecordMapper recordMapper;
    private final JwtUtil jwtUtil;

    public AlarmService(AlarmRuleMapper ruleMapper, AlarmRecordMapper recordMapper, JwtUtil jwtUtil) {
        this.ruleMapper = ruleMapper;
        this.recordMapper = recordMapper;
        this.jwtUtil = jwtUtil;
    }

    // ---------- 告警规则 ----------

    public List<AlarmRule> listRules(Long tenantId) {
        return ruleMapper.selectList(new QueryWrapper<AlarmRule>()
                .eq("tenant_id", tenantId).orderByDesc("create_time"));
    }

    public void createRule(Long tenantId, AlarmRule rule) {
        rule.setId(null);
        rule.setTenantId(tenantId);
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        rule.setCreateTime(LocalDateTime.now());
        ruleMapper.insert(rule);
    }

    public void updateRule(Long tenantId, Long id, AlarmRule rule) {
        AlarmRule db = ruleMapper.selectById(id);
        checkTenant(tenantId, db == null ? null : db.getTenantId());
        rule.setId(id);
        rule.setTenantId(tenantId);
        ruleMapper.updateById(rule);
    }

    public void deleteRule(Long tenantId, Long id) {
        AlarmRule db = ruleMapper.selectById(id);
        checkTenant(tenantId, db == null ? null : db.getTenantId());
        ruleMapper.deleteById(id);
    }

    // ---------- 告警记录 ----------

    public Page<AlarmRecord> pageRecords(Long tenantId, int page, int size,
                                         String level, String status) {
        QueryWrapper<AlarmRecord> qw = new QueryWrapper<AlarmRecord>()
                .eq("tenant_id", tenantId)
                .eq(level != null && !level.isBlank(), "level", level)
                .eq(status != null && !status.isBlank(), "status", status)
                .orderByDesc("create_time");
        return recordMapper.selectPage(new Page<>(page, size), qw);
    }

    public void handle(Long tenantId, Long id) {
        AlarmRecord db = recordMapper.selectById(id);
        if (db == null || !tenantId.equals(db.getTenantId())) {
            throw new BizException(404, "告警记录不存在");
        }
        AlarmRecord upd = new AlarmRecord();
        upd.setId(id);
        upd.setStatus(AlarmRecord.STATUS_HANDLED);
        upd.setHandleTime(LocalDateTime.now());
        recordMapper.updateById(upd);
    }

    /**
     * 内部接口：接收设备服务（Feign）写入的故障告警。
     * 只接受 typ=service 的服务间 token，租户以 token 为准。
     */
    public void createFromService(String authorizationHeader, AlarmRecord record) {
        var claims = jwtUtil.parse(JwtUtil.stripBearer(authorizationHeader));
        if (!TokenConstants.TYPE_SERVICE.equals(claims.get(TokenConstants.CLAIM_TYPE))) {
            throw new BizException(403, "仅允许服务间调用");
        }
        Long tenantId = claims.get(TokenConstants.CLAIM_TENANT, Long.class);

        record.setId(null);
        record.setTenantId(tenantId);
        record.setStatus(AlarmRecord.STATUS_OPEN);
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    /**
     * 统计聚合（供管理端看板与 AI 日报使用）：
     * 未处理总数、按级别、按站点 Top3 —— 全部走 SQL 聚合，不把明细拉到内存里算
     */
    public Map<String, Object> stats(Long tenantId) {
        Long total = recordMapper.selectCount(
                new QueryWrapper<AlarmRecord>().eq("tenant_id", tenantId));
        Long open = recordMapper.selectCount(new QueryWrapper<AlarmRecord>()
                .eq("tenant_id", tenantId).eq("status", AlarmRecord.STATUS_OPEN));

        List<Map<String, Object>> byLevel = recordMapper.selectMaps(
                new QueryWrapper<AlarmRecord>()
                        .select("level as k", "count(*) as v")
                        .eq("tenant_id", tenantId)
                        .eq("status", AlarmRecord.STATUS_OPEN)
                        .groupBy("level"));

        List<Map<String, Object>> topStations = recordMapper.selectMaps(
                new QueryWrapper<AlarmRecord>()
                        .select("station as k", "count(*) as v")
                        .eq("tenant_id", tenantId)
                        .eq("status", AlarmRecord.STATUS_OPEN)
                        .groupBy("station")
                        .orderByDesc("v")
                        .last("LIMIT 3"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("open", open);
        result.put("byLevel", toMap(byLevel));
        result.put("topStations", topStations);
        return result;
    }

    private Map<String, Object> toMap(List<Map<String, Object>> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            m.put(String.valueOf(row.get("k")), row.get("v"));
        }
        return m;
    }

    private void checkTenant(Long tenantId, Long ownerTenantId) {
        if (ownerTenantId == null || !tenantId.equals(ownerTenantId)) {
            throw new BizException(404, "资源不存在");
        }
    }
}
