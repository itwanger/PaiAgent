-- MySQL dump 10.13  Distrib 9.3.0, for macos15.4 (arm64)
--
-- Host: localhost    Database: workflow_java
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `workflow_java`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `workflow_java` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `workflow_java`;

--
-- Table structure for table `node_execution_log`
--

DROP TABLE IF EXISTS `node_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_id` varchar(64) NOT NULL COMMENT '工作流执行ID',
  `node_id` varchar(128) NOT NULL COMMENT '节点ID',
  `node_type` varchar(50) NOT NULL COMMENT '节点类型',
  `input_data` json DEFAULT NULL COMMENT '节点输入数据',
  `output_data` json DEFAULT NULL COMMENT '节点输出数据',
  `status` varchar(20) DEFAULT 'RUNNING' COMMENT '节点状态：RUNNING, SUCCESS, FAILED',
  `error_message` text COMMENT '错误信息',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行时长（毫秒）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点执行日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `node_execution_log`
--

LOCK TABLES `node_execution_log` WRITE;
/*!40000 ALTER TABLE `node_execution_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `node_execution_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workflow`
--

DROP TABLE IF EXISTS `workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` varchar(64) NOT NULL COMMENT '工作流ID',
  `name` varchar(255) NOT NULL COMMENT '工作流名称',
  `description` text COMMENT '工作流描述',
  `version` varchar(32) DEFAULT 'v1.0.0' COMMENT '版本号',
  `dsl_data` json NOT NULL COMMENT 'DSL 定义（包含 nodes 和 edges）',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `workflow_id` (`workflow_id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workflow`
--

LOCK TABLES `workflow` WRITE;
/*!40000 ALTER TABLE `workflow` DISABLE KEYS */;
INSERT INTO `workflow` VALUES (1,'184736','AI 播客生成工作流','将文本改写为播客风格并生成语音','v1.0.0','{\"edges\": [{\"sourceNodeId\": \"node-start::001\", \"targetNodeId\": \"node-llm::002\"}, {\"sourceNodeId\": \"node-llm::002\", \"targetNodeId\": \"node-plugin::003\"}, {\"sourceNodeId\": \"node-plugin::003\", \"targetNodeId\": \"node-end::004\"}], \"nodes\": [{\"id\": \"node-start::001\", \"data\": {\"outputs\": [{\"name\": \"user_input\", \"type\": \"string\"}]}, \"type\": \"node-start\"}, {\"id\": \"node-llm::002\", \"data\": {\"outputs\": [{\"name\": \"llm_output\", \"type\": \"string\"}], \"nodeParam\": {\"prompt\": \"改写为播客风格：{{node-start::001.user_input}}\", \"modelId\": 1}}, \"type\": \"node-llm\"}, {\"id\": \"node-plugin::003\", \"data\": {\"outputs\": [{\"name\": \"voice_url\", \"type\": \"string\"}], \"nodeParam\": {\"vcn\": \"x5_lingfeiyi_flow\", \"speed\": 50, \"pluginId\": \"tool@8b2262bef821000\"}}, \"type\": \"node-plugin\"}, {\"id\": \"node-end::004\", \"data\": {\"nodeParam\": {\"template\": \"<audio preload=\\\"none\\\" controls><source src=\\\"{{node-plugin::003.voice_url}}\\\" type=\\\"audio/mpeg\\\"></audio>\", \"outputMode\": 1}}, \"type\": \"node-end\"}]}',1,'system','2025-11-14 20:03:39','2025-11-14 20:03:39',0);
/*!40000 ALTER TABLE `workflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workflow_execution`
--

DROP TABLE IF EXISTS `workflow_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_id` varchar(64) NOT NULL COMMENT '执行ID',
  `workflow_id` varchar(64) NOT NULL COMMENT '工作流ID',
  `status` varchar(20) DEFAULT 'RUNNING' COMMENT '执行状态：RUNNING, SUCCESS, FAILED',
  `input_data` json DEFAULT NULL COMMENT '输入数据',
  `output_data` json DEFAULT NULL COMMENT '输出数据',
  `error_message` text COMMENT '错误信息',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行时长（毫秒）',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `execution_id` (`execution_id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流执行历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workflow_execution`
--

LOCK TABLES `workflow_execution` WRITE;
/*!40000 ALTER TABLE `workflow_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `workflow_execution` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-22 11:27:42
