package com.paiagent.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.paiagent.engine.model.WorkflowConfig;
import com.paiagent.engine.model.WorkflowEdge;
import com.paiagent.engine.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses workflow JSON without relying on FastJSON bean cache for nested classes.
 *  JSON 字符串
        ↓ JSON.parseObject
    JSONObject
        ↓ parseNodes、parseEdges
    Java 节点和边
        ↓
    WorkflowConfig
    最后得到：
    WorkflowConfig
├── List<WorkflowNode> nodes
└── List<WorkflowEdge> edges
 */
@Component
public class WorkflowConfigParser {
    private static final String REACT_FLOW_NODE_TYPE = "workflow";

    public WorkflowConfig parse(String flowData) {
        JSONObject root = JSON.parseObject(flowData);
        WorkflowConfig config = new WorkflowConfig();
        config.setNodes(parseNodes(root.getJSONArray("nodes")));
        config.setEdges(parseEdges(root.getJSONArray("edges")));
        return config;
    }

    private List<WorkflowNode> parseNodes(JSONArray array) {
        List<WorkflowNode> nodes = new ArrayList<>();
        if (array == null) {
            return nodes;
        }

        for (Object item : array) {
            JSONObject json = (JSONObject) item;
            JSONObject dataJson = json.getJSONObject("data");
            WorkflowNode node = new WorkflowNode();
            node.setId(json.getString("id"));
            node.setType(resolveNodeType(json, dataJson));
            node.setData(parseMap(dataJson));

            JSONObject positionJson = json.getJSONObject("position");
            if (positionJson != null) {
                WorkflowNode.Position position = new WorkflowNode.Position();
                position.setX(positionJson.getDouble("x"));
                position.setY(positionJson.getDouble("y"));
                node.setPosition(position);
            }

            nodes.add(node);
        }
        return nodes;
    }

    private String resolveNodeType(JSONObject nodeJson, JSONObject dataJson) {
        String nodeType = nodeJson.getString("type");
        String dataType = dataJson != null ? dataJson.getString("type") : null;
        if ((nodeType == null || nodeType.isBlank() || REACT_FLOW_NODE_TYPE.equals(nodeType))
                && dataType != null && !dataType.isBlank()) {
            return dataType;
        }
        return nodeType;
    }

    private List<WorkflowEdge> parseEdges(JSONArray array) {
        List<WorkflowEdge> edges = new ArrayList<>();
        if (array == null) {
            return edges;
        }

        for (Object item : array) {
            JSONObject json = (JSONObject) item;
            WorkflowEdge edge = new WorkflowEdge();
            edge.setId(json.getString("id"));
            edge.setSource(json.getString("source"));
            edge.setTarget(json.getString("target"));
            edge.setSourceHandle(json.getString("sourceHandle"));
            edge.setTargetHandle(json.getString("targetHandle"));
            edges.add(edge);
        }
        return edges;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(JSONObject json) {
        if (json == null) {
            return Map.of();
        }
        return JSON.parseObject(json.toJSONString(), Map.class);
    }
}
