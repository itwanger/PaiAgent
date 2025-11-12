package com.iflytek.astron.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow entity
 */
@Data
@TableName("workflow")
public class WorkflowEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("workflow_id")
    private String workflowId;
    
    @TableField("dsl_data")
    private String dslData;
    
    @TableField("created_time")
    private LocalDateTime createdTime;
    
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
