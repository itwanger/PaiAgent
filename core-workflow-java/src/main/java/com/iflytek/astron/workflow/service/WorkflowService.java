package com.iflytek.astron.workflow.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.workflow.domain.WorkflowDSL;
import com.iflytek.astron.workflow.entity.WorkflowEntity;
import com.iflytek.astron.workflow.mapper.WorkflowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Workflow service
 * Handles workflow storage and retrieval
 */
@Slf4j
@Service
public class WorkflowService {
    
    private final WorkflowMapper workflowMapper;
    
    public WorkflowService(WorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }
    
    /**
     * Get workflow DSL by workflow ID
     * @param workflowId workflow ID (e.g., "184736" or "7394988637451558914")
     * @return workflow DSL
     */
    public WorkflowDSL getWorkflowDSL(String workflowId) {
        log.info("Loading workflow: {}", workflowId);
        
        LambdaQueryWrapper<WorkflowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkflowEntity::getId, Long.parseLong(workflowId));
        
        WorkflowEntity entity = workflowMapper.selectOne(queryWrapper);
        
        if (entity == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        String dslData = entity.getDslData();
        
        // Parse nested "data" structure
        com.alibaba.fastjson2.JSONObject outerJson = JSON.parseObject(dslData);
        com.alibaba.fastjson2.JSONObject innerData = outerJson.getJSONObject("data");
        
        WorkflowDSL workflowDSL = innerData.to(WorkflowDSL.class);
        
        log.info("Loaded workflow: id={}, nodes={}, edges={}", 
                workflowId, workflowDSL.getNodes().size(), workflowDSL.getEdges().size());
        
        return workflowDSL;
    }
}
