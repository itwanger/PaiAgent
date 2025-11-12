package com.iflytek.astron.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.workflow.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Workflow mapper
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
}
