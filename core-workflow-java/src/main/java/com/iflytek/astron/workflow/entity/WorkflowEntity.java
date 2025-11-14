package com.iflytek.astron.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow entity - maps to astron_console.workflow table
 */
@Data
@TableName("workflow")
public class WorkflowEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("name")
    private String name;
    
    @TableField("description")
    private String description;
    
    @TableField("app_id")
    private String appId;
    
    @TableField("data")
    private String dslData;
    
    @TableField("create_time")
    private LocalDateTime createdTime;
    
    @TableField("update_time")
    private LocalDateTime updatedTime;
}
