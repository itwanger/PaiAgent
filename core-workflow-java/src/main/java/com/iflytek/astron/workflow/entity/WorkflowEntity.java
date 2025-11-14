package com.iflytek.astron.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow entity - maps to workflow.flow table
 */
@Data
@TableName("flow")
public class WorkflowEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("name")
    private String name;
    
    @TableField("data")
    private String dslData;
    
    @TableField("create_at")
    private LocalDateTime createdTime;
    
    @TableField("update_at")
    private LocalDateTime updatedTime;
}
