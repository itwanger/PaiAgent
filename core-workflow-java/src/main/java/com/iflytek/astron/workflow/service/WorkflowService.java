package com.iflytek.astron.workflow.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.workflow.controller.WorkflowController.WorkflowUpdateRequest;
import com.iflytek.astron.workflow.domain.WorkflowDSL;
import com.iflytek.astron.workflow.entity.WorkflowEntity;
import com.iflytek.astron.workflow.mapper.WorkflowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

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
     * @param workflowId workflow flow_id (e.g., "7394988637451558914")
     * @return workflow DSL
     */
    public WorkflowDSL getWorkflowDSL(String workflowId) {
        log.info("Loading workflow: {}", workflowId);
        
        LambdaQueryWrapper<WorkflowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkflowEntity::getFlowId, workflowId);
        
        WorkflowEntity entity = workflowMapper.selectOne(queryWrapper);
        
        if (entity == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        String dslData = entity.getDslData();
        log.debug("Raw workflow data: {}", dslData);
        
        // astron_console.workflow table: data field is directly {"nodes": [...], "edges": [...]}
        // No nested "data" structure
        WorkflowDSL workflowDSL = JSON.parseObject(dslData, WorkflowDSL.class);
        
        log.info("Loaded workflow: flow_id={}, nodes={}, edges={}", 
                workflowId, workflowDSL.getNodes().size(), workflowDSL.getEdges().size());
        
        return workflowDSL;
    }
    
    /**
     * Update workflow configuration
     * @param flowId workflow flow_id
     * @param request update request containing name, description, data, app_id
     */
    public void updateWorkflow(String flowId, WorkflowUpdateRequest request) {
        log.info("Updating workflow: {}", flowId);
        
        LambdaQueryWrapper<WorkflowEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WorkflowEntity::getFlowId, flowId);
        
        WorkflowEntity entity = workflowMapper.selectOne(queryWrapper);
        
        if (entity == null) {
            throw new IllegalArgumentException("Workflow not found: " + flowId);
        }
        
        boolean updated = false;
        
        if (request.getName() != null) {
            entity.setName(request.getName());
            updated = true;
        }
        
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
            updated = true;
        }
        
        if (request.getData() != null) {
            // Extract the inner "data" field if it exists
            // Console-hub sends: {"data": {"nodes": [...], "edges": [...]}, "id": "xxx", "name": "xxx"}
            // But we should save only: {"nodes": [...], "edges": [...]}
            Map<String, Object> dataMap = request.getData();
            Object innerData = dataMap.get("data");
            
            String dataJson;
            if (innerData != null && innerData instanceof Map) {
                // Has nested "data" field, extract it
                dataJson = JSON.toJSONString(innerData);
                log.debug("Extracted inner data field for storage");
            } else {
                // No nested structure, use as is
                dataJson = JSON.toJSONString(dataMap);
            }
            
            entity.setDslData(dataJson);
            updated = true;
        }
        
        if (request.getAppId() != null) {
            entity.setAppId(request.getAppId());
            updated = true;
        }
        
        if (updated) {
            entity.setUpdatedTime(LocalDateTime.now());
            int result = workflowMapper.updateById(entity);
            
            if (result == 0) {
                throw new RuntimeException("Failed to update workflow: " + flowId);
            }
            
            log.info("Workflow updated successfully: flowId={}, updated fields: name={}, description={}, data={}, appId={}", 
                    flowId, 
                    request.getName() != null,
                    request.getDescription() != null,
                    request.getData() != null,
                    request.getAppId() != null);
        } else {
            log.warn("No fields to update for workflow: {}", flowId);
        }
    }
}
