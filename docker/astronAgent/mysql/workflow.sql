-- MySQL dump 10.13  Distrib 9.3.0, for macos15.4 (arm64)
--
-- Host: localhost    Database: workflow
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
-- Current Database: `workflow`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `workflow` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `workflow`;

--
-- Table structure for table `app`
--

DROP TABLE IF EXISTS `app`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app` (
  `id` bigint NOT NULL,
  `name` varchar(30) DEFAULT NULL,
  `alias_id` varchar(64) DEFAULT NULL COMMENT '应用标识',
  `api_key` varchar(50) NOT NULL,
  `api_secret` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_tenant` tinyint DEFAULT '0' COMMENT '是否为租户app\n0: 否\n1: 是',
  `source` tinyint DEFAULT '0' COMMENT '租户归属，采用二进制位权的十进制表示。如：1: 星辰平台, 2: 开放平台, 4: AIUI',
  `actual_source` tinyint DEFAULT '0' COMMENT '应用实际归属',
  `plat_release_auth` tinyint DEFAULT '0' COMMENT '针对租户账户，提供平台授权权限。值为source或值',
  `status` tinyint DEFAULT '1' COMMENT '应用状态\n0: 禁用\n1: 启用',
  `audit_policy` tinyint DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `create_at` datetime DEFAULT NULL COMMENT '创建时间',
  `update_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `alias_id` (`alias_id`),
  KEY `idx_appid` (`alias_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='app 信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app`
--

LOCK TABLES `app` WRITE;
/*!40000 ALTER TABLE `app` DISABLE KEYS */;
INSERT INTO `app` VALUES (1,'星辰','680ab54f','7b709739e8da44536127a333c7603a83','NjhmY2NmM2NkZDE4MDFlNmM5ZjcyZjMy','星辰',1,1,1,1,1,0,1,1,'2025-09-20 14:10:48','2025-09-20 14:10:51'),(2,'星辰','f740451b','ebaf9daded8d3b2cbe9a648247a87934','ZGE0YjQ3YjNjMmEwZGZmMDEwYzI0M2Q1',NULL,0,1,1,0,1,0,NULL,NULL,'2025-11-22 10:42:34','2025-11-22 10:42:34');
/*!40000 ALTER TABLE `app` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_source`
--

DROP TABLE IF EXISTS `app_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_source` (
  `id` bigint NOT NULL,
  `source` tinyint NOT NULL COMMENT '租户归属，采用二进制位权的十进制表示。如：1: 星辰平台, 2: 开放平台, 4: AIUI',
  `source_id` varchar(32) NOT NULL COMMENT '租户源ID',
  `description` varchar(16) NOT NULL,
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_source`
--

LOCK TABLES `app_source` WRITE;
/*!40000 ALTER TABLE `app_source` DISABLE KEYS */;
INSERT INTO `app_source` VALUES (1,1,'admin','星辰','2025-10-11 09:21:11','2025-10-11 09:21:11');
/*!40000 ALTER TABLE `app_source` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flow`
--

DROP TABLE IF EXISTS `flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow` (
  `id` bigint NOT NULL,
  `group_id` bigint DEFAULT '0',
  `name` varchar(128) NOT NULL COMMENT '协议名称',
  `data` mediumtext COMMENT '编排标准协议',
  `release_data` mediumtext COMMENT '发布后的数据',
  `description` varchar(1024) DEFAULT NULL,
  `version` varchar(128) DEFAULT '' COMMENT '协议版本',
  `release_status` tinyint DEFAULT NULL COMMENT '发布状态或值',
  `app_id` varchar(255) DEFAULT NULL COMMENT 'app_id',
  `source` tinyint DEFAULT '0' COMMENT '来源',
  `tag` int DEFAULT NULL COMMENT '标记工作流标签 0：无标签；1：对照组',
  `create_by` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `create_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_group_id_version` (`group_id`,`version`),
  KEY `idx_flow_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flow`
--

LOCK TABLES `flow` WRITE;
/*!40000 ALTER TABLE `flow` DISABLE KEYS */;
INSERT INTO `flow` VALUES (7397621744825311234,7397621744825311232,'test_workflow_123','{}','{}','Test workflow description','-1',0,'680ab54f',1,0,0,NULL,'2025-11-21 21:07:45','2025-11-21 21:07:45'),(7397621763502645250,7397621763502645248,'test_workflow_123','{}','{}','Test workflow','-1',0,'680ab54f',1,0,0,NULL,'2025-11-21 21:07:50','2025-11-21 21:07:50'),(7397626912753176578,7397626912753176576,'自定义1763731694228','{}','{}','','-1',0,'680ab54f',1,0,0,NULL,'2025-11-21 21:28:17','2025-11-21 21:28:17'),(7397627509556498435,7397627509556498433,'AI 播客20251121213039','{\"data\": {\"edges\": [{\"sourceNodeId\": \"node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783\", \"targetNodeId\": \"spark-llm::0a562a15-21d6-45db-97b0-521ece84bc69\"}, {\"sourceNodeId\": \"spark-llm::0a562a15-21d6-45db-97b0-521ece84bc69\", \"targetNodeId\": \"plugin::e1df3ec1-6ed2-4ef9-8681-7764286a395b\"}, {\"sourceNodeId\": \"plugin::e1df3ec1-6ed2-4ef9-8681-7764286a395b\", \"targetNodeId\": \"node-end::cda617af-551e-462e-b3b8-3bb9a041bf88\"}], \"nodes\": [{\"data\": {\"inputs\": [], \"nodeMeta\": {\"aliasName\": \"\\u5f00\\u59cb\", \"nodeType\": \"\\u57fa\\u7840\\u8282\\u70b9\"}, \"nodeParam\": {\"appId\": \"f740451b\", \"apiKey\": \"ebaf9daded8d3b2cbe9a648247a87934\", \"apiSecret\": \"ZGE0YjQ3YjNjMmEwZGZmMDEwYzI0M2Q1\"}, \"outputs\": [{\"id\": \"0918514b-72a8-4646-8dd9-ff4a8fc26d44\", \"name\": \"AGENT_USER_INPUT\", \"required\": true, \"schema\": {\"description\": \"\\u7528\\u6237\\u672c\\u8f6e\\u5bf9\\u8bdd\\u8f93\\u5165\\u5185\\u5bb9\", \"type\": \"string\"}}]}, \"id\": \"node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783\"}, {\"data\": {\"inputs\": [{\"fileType\": \"\", \"id\": \"bfd129e8-694c-4168-9870-17464a5f2f05\", \"name\": \"audio_url\", \"schema\": {\"type\": \"string\", \"value\": {\"content\": {\"id\": \"8e665ab7-5239-4856-9526-f8f07ca1d769\", \"nodeId\": \"plugin::e1df3ec1-6ed2-4ef9-8681-7764286a395b\", \"name\": \"data.voice_url\"}, \"type\": \"ref\"}}}], \"nodeMeta\": {\"aliasName\": \"\\u7ed3\\u675f\", \"nodeType\": \"\\u57fa\\u7840\\u8282\\u70b9\"}, \"nodeParam\": {\"template\": \"<audio preload=\\\"none\\\" controls>\\n            <source src=\\\"{{audio_url}}\\\" type=\\\"audio/mpeg\\\">\\n        </audio>\", \"streamOutput\": false, \"outputMode\": 1, \"templateErrMsg\": \"\", \"appId\": \"f740451b\", \"apiKey\": \"ebaf9daded8d3b2cbe9a648247a87934\", \"apiSecret\": \"ZGE0YjQ3YjNjMmEwZGZmMDEwYzI0M2Q1\"}, \"outputs\": []}, \"id\": \"node-end::cda617af-551e-462e-b3b8-3bb9a041bf88\"}, {\"data\": {\"inputs\": [{\"fileType\": \"\", \"id\": \"d6bd20f4-55d7-416b-9991-c565a919fbca\", \"name\": \"input\", \"schema\": {\"type\": \"string\", \"value\": {\"content\": {\"id\": \"0918514b-72a8-4646-8dd9-ff4a8fc26d44\", \"nodeId\": \"node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783\", \"name\": \"AGENT_USER_INPUT\"}, \"type\": \"ref\"}}}], \"nodeMeta\": {\"nodeType\": \"\\u57fa\\u7840\\u8282\\u70b9\", \"aliasName\": \"\\u5927\\u6a21\\u578b_1\"}, \"nodeParam\": {\"auditing\": \"default\", \"template\": \"# \\u89d2\\u8272\\n\\u4f60\\u662f\\u6c89\\u9ed8\\u738b\\u4e8c\\uff0c\\u4e00\\u4e2a\\u5634\\u4e0a\\u8d2b\\u3001\\u5fc3\\u91cc\\u660e\\u767d\\u7684\\u6280\\u672f\\u535a\\u4e3b\\u3002\\u73b0\\u5728\\u4f60\\u4e3b\\u6301\\u4e00\\u6863\\u53eb\\u300c\\u738b\\u4e8c\\u7535\\u53f0\\u300d\\u7684\\u8282\\u76ee\\uff0c\\u8fd9\\u8282\\u76ee\\u561b\\uff0c\\u4e3b\\u6253\\u4e00\\u4e2a\\u2014\\u2014\\u6709\\u70b9\\u5e72\\u8d27\\u3001\\u6709\\u70b9\\u5e9f\\u8bdd\\uff0c\\u4f46\\u7edd\\u4e0d\\u65e0\\u804a\\u3002\\n# \\u4efb\\u52a1\\n\\u628a\\u7528\\u6237\\u63d0\\u4f9b\\u7684\\u539f\\u59cb\\u5185\\u5bb9\\u6539\\u7f16\\u6210\\u9002\\u5408\\u5355\\u53e3\\u76f8\\u58f0\\u6216\\u64ad\\u5ba2\\u8282\\u76ee\\u98ce\\u683c\\u7684\\u9010\\u5b57\\u7a3f\\u3002\\n\\u8981\\u50cf\\u7535\\u53f0\\u804a\\u5929\\u90a3\\u6837\\u81ea\\u7136\\uff0c\\u6709\\u8282\\u594f\\u3001\\u6709\\u60c5\\u7eea\\u3001\\u6709\\u70b9\\u6897\\u3002\\n# \\u6ce8\\u610f\\u70b9\\n\\u786e\\u4fdd\\u8bed\\u8a00\\u53e3\\u8bed\\u5316\\uff0c\\u50cf\\u771f\\u5728\\u8ddf\\u542c\\u4f17\\u5520\\u55d1\\u3002\\n\\u4e13\\u4e1a\\u672f\\u8bed\\u8981\\u7528\\u201c\\u4eba\\u8bdd\\u201d\\u89e3\\u91ca\\uff0c\\u8d8a\\u901a\\u4fd7\\u8d8a\\u597d\\u3002\\n\\u6574\\u4f53\\u8282\\u594f\\u8f7b\\u677e\\u70b9\\uff0c\\u6709\\u70b9\\u5e7d\\u9ed8\\uff0c\\u6709\\u70b9\\u6e29\\u5ea6\\uff0c\\u542c\\u7740\\u50cf\\u670b\\u53cb\\u804a\\u5929\\uff0c\\u4e0d\\u50cf\\u8001\\u5e08\\u8bb2\\u8bfe\\u3002\\n\\u4fdd\\u6301\\u5bf9\\u8bdd\\u7684\\u81ea\\u7136\\u8fc7\\u6e21\\uff0c\\u522b\\u8ba9\\u542c\\u4f17\\u89c9\\u5f97\\u8df3\\u3002\\n\\u8f93\\u51fa\\u65f6\\u53ea\\u8981\\u53e3\\u64ad\\u7a3f\\uff0c\\u4e0d\\u8981\\u52a0\\u683c\\u5f0f\\uff0c\\u4e0d\\u8981\\u5199\\u63d0\\u793a\\u5185\\u5bb9\\u3002\\n# \\u793a\\u4f8b \\n\\u6b22\\u8fce\\u6536\\u542c\\u738b\\u4e8c\\u7535\\u53f0\\uff0c\\u54b1\\u8fd9\\u8282\\u76ee\\u554a\\uff0c\\u4e0d\\u8bb2\\u5927\\u9053\\u7406\\uff0c\\u4e5f\\u4e0d\\u88c5\\u6df1\\u6c89\\u3002\\n\\u4eca\\u5929\\u8fd9\\u8bdd\\u9898\\u5462\\uff0c\\u6709\\u70b9\\u610f\\u601d\\u2014\\u2014\\u4fdd\\u8bc1\\u542c\\u5b8c\\u4f60\\u4f1a\\u60f3\\uff0c\\u5367\\u69fd\\uff0c\\u539f\\u6765\\u8fd8\\u80fd\\u8fd9\\u4e48\\u60f3\\u3002\\n\\u6765\\uff0c\\u522b\\u78e8\\u53fd\\uff0c\\u76f4\\u63a5\\u5f00\\u6574\\u3002\\n# \\u539f\\u59cb\\u5185\\u5bb9\\uff1a{{input}}\", \"respFormat\": 0, \"appId\": \"f740451b\", \"enableChatHistoryV2\": {\"isEnabled\": false, \"rounds\": 1}, \"templateErrMsg\": \"\", \"modelId\": 1, \"isThink\": false, \"multiMode\": false, \"modelName\": \"deepseek-chat\", \"modelEnabled\": true, \"llmIdErrMsg\": \"\", \"source\": \"openai\", \"extraParams\": {\"temperature\": 1}, \"uid\": \"admin\", \"llmId\": 593066024, \"domain\": \"deepseek-chat\", \"serviceId\": \"deepseek-chat\", \"url\": \"https://api.deepseek.com/v1/chat/completions\", \"apiKey\": \"sk-43acb864f0604d23ae1329c14c1d1c0b\", \"apiSecret\": \"\"}, \"outputs\": [{\"id\": \"ab640894-93f3-4395-ab9c-792c4dda60b0\", \"name\": \"output\", \"schema\": {\"description\": \"\", \"type\": \"string\"}}]}, \"id\": \"spark-llm::0a562a15-21d6-45db-97b0-521ece84bc69\"}, {\"data\": {\"inputs\": [{\"id\": \"58228829-b73b-4be0-b5c6-641893c0589f\", \"name\": \"vcn\", \"required\": true, \"schema\": {\"type\": \"string\", \"value\": {\"content\": \"x5_lingfeiyi_flow\", \"type\": \"literal\"}}}, {\"fileType\": \"\", \"id\": \"8fff2262-36f4-4655-b95d-f0de5da4a2cf\", \"name\": \"text\", \"required\": true, \"schema\": {\"type\": \"string\", \"value\": {\"content\": {\"id\": \"ab640894-93f3-4395-ab9c-792c4dda60b0\", \"nodeId\": \"spark-llm::0a562a15-21d6-45db-97b0-521ece84bc69\", \"name\": \"output\"}, \"type\": \"ref\"}}}, {\"id\": \"5d9f763f-8c86-46a3-a08e-e5736bb1e2a4\", \"name\": \"speed\", \"required\": true, \"schema\": {\"type\": \"integer\", \"value\": {\"content\": \"50\", \"type\": \"literal\"}}}], \"nodeMeta\": {\"nodeType\": \"\\u5de5\\u5177\", \"aliasName\": \"\\u8d85\\u62df\\u4eba\\u5408\\u6210_1\"}, \"nodeParam\": {\"appId\": \"f740451b\", \"code\": \"\", \"pluginId\": \"tool@8b2262bef821000\", \"operationId\": \"\\u8d85\\u62df\\u4eba\\u5408\\u6210-46EXFdLW\", \"toolDescription\": \"\\u7528\\u6237\\u4e0a\\u4f20\\u4e00\\u6bb5\\u8bdd\\uff0c\\u9009\\u62e9\\u7279\\u8272\\u53d1\\u97f3\\u4eba\\uff0c\\u751f\\u6210\\u4e00\\u6bb5\\u66f4\\u62df\\u4eba\\u7684\\u8bed\\u97f3\", \"version\": \"V1.0\", \"uid\": \"8fded410-c458-4c5b-b894-c6b49b01870a\", \"businessInput\": [], \"apiKey\": \"ebaf9daded8d3b2cbe9a648247a87934\", \"apiSecret\": \"ZGE0YjQ3YjNjMmEwZGZmMDEwYzI0M2Q1\"}, \"outputs\": [{\"id\": \"3b4e95a6-b21b-441e-883e-f68ca4af0b33\", \"name\": \"code\", \"schema\": {\"type\": \"integer\"}}, {\"id\": \"f9501bb0-0472-4182-8d91-cdd81fc4320d\", \"name\": \"message\", \"schema\": {\"type\": \"string\"}}, {\"id\": \"ee930f27-66ed-43cb-aec1-6d2277de4d9c\", \"name\": \"sid\", \"schema\": {\"type\": \"string\"}}, {\"id\": \"7459d171-3ad8-4d53-a244-fedf6778e2e3\", \"name\": \"data\", \"schema\": {\"properties\": {\"voice_url\": {\"type\": \"string\"}}, \"type\": \"object\"}}]}, \"id\": \"plugin::e1df3ec1-6ed2-4ef9-8681-7764286a395b\"}]}, \"description\": \"\\u975e\\u5e38\\u725b\\u903c\", \"id\": \"7397627509556498435\", \"name\": \"AI \\u64ad\\u5ba220251121213039\", \"version\": \"v3.0.0\"}','{}','非常牛逼','-1',0,'f740451b',1,0,0,NULL,'2025-11-21 21:30:40','2025-11-22 10:08:42');
/*!40000 ALTER TABLE `flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `license`
--

DROP TABLE IF EXISTS `license`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `license` (
  `id` bigint NOT NULL,
  `app_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '授权状态\n0: 禁用\n1: 启用',
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `lic_uk_appid_gid` (`app_id`,`group_id`),
  KEY `idx_app_id_group_id` (`app_id`,`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `license`
--

LOCK TABLES `license` WRITE;
/*!40000 ALTER TABLE `license` DISABLE KEYS */;
/*!40000 ALTER TABLE `license` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workflow_node_history`
--

DROP TABLE IF EXISTS `workflow_node_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow_node_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `node_id` varchar(255) NOT NULL,
  `uid` varchar(255) DEFAULT NULL,
  `chat_id` varchar(255) DEFAULT NULL,
  `raw_question` mediumtext,
  `raw_answer` mediumtext,
  `create_time` datetime NOT NULL,
  `flow_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `chat_id` (`chat_id`),
  KEY `node_id` (`node_id`),
  KEY `uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workflow_node_history`
--

LOCK TABLES `workflow_node_history` WRITE;
/*!40000 ALTER TABLE `workflow_node_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `workflow_node_history` ENABLE KEYS */;
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
