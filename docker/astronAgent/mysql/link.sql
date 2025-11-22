-- MySQL dump 10.13  Distrib 9.3.0, for macos15.4 (arm64)
--
-- Host: localhost    Database: spark-link
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
-- Current Database: `spark-link`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `spark-link` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `spark-link`;

--
-- Table structure for table `tools_schema`
--

DROP TABLE IF EXISTS `tools_schema`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tools_schema` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `app_id` varchar(32) DEFAULT NULL COMMENT '应用ID',
  `tool_id` varchar(32) DEFAULT NULL COMMENT '工具ID',
  `name` varchar(128) DEFAULT NULL COMMENT '工具名称',
  `description` varchar(512) DEFAULT NULL COMMENT '工具描述',
  `open_api_schema` text COMMENT 'open api schema，json格式',
  `create_at` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_at` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  `mcp_server_url` varchar(255) DEFAULT NULL COMMENT 'mcp_server_url',
  `schema` text COMMENT 'schema,json格式',
  `version` varchar(32) NOT NULL DEFAULT 'DEF_VER' COMMENT '版本号',
  `is_deleted` bigint NOT NULL DEFAULT '0' COMMENT '是否已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_tool_version` (`tool_id`,`version`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具数据库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tools_schema`
--

LOCK TABLES `tools_schema` WRITE;
/*!40000 ALTER TABLE `tools_schema` DISABLE KEYS */;
INSERT INTO `tools_schema` VALUES (1,'680ab54f','tool@8b2262bef821000','超拟人合成','用户上传一段话，选择特色发音人，生成一段更拟人的语音','{\"info\": {\"title\": \"agentBuilder toolset\", \"version\": \"1.0.0\", \"x-is-official\": false}, \"openapi\": \"3.1.0\", \"paths\": {\"/aitools/v1/smarttts\": {\"post\": {\"description\": \"用户上传一段话，选择特色发音人，生成一段更拟人的语音\", \"operationId\": \"超拟人合成-46EXFdLW\", \"requestBody\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"vcn\": {\"default\": \"x5_lingfeiyi_flow\", \"description\": \"特色发音人，目前可选（x5_lingfeiyi_flow）\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}, \"text\": {\"description\": \"需要合成的文本\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}, \"speed\": {\"default\": 50, \"description\": \"语速：0对应默认语速的1/2，100对应默认语速的2倍; 默认值50\", \"type\": \"integer\", \"x-display\": true, \"x-from\": 2}}, \"required\": [\"vcn\", \"text\", \"speed\"], \"type\": \"object\"}}}, \"required\": true}, \"responses\": {\"200\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"code\": {\"description\": \"状态码\", \"type\": \"integer\", \"x-display\": true}, \"data\": {\"description\": \"结果\", \"properties\": {\"voice_url\": {\"description\": \"音频下载url\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\", \"x-display\": true}, \"message\": {\"description\": \"操作消息\", \"type\": \"string\", \"x-display\": true}, \"sid\": {\"description\": \"会话id\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\"}}}, \"description\": \"success\"}}, \"summary\": \"超拟人合成\"}}}, \"servers\": [{\"description\": \"a server description\", \"url\": \"http://localhost:18668\"}]}','2025-10-24 10:00:00.000000','2025-11-22 09:49:29.788278',NULL,NULL,'V1.0',0),(2,'680ab54f','tool@8b226f7d7421000','文生图','根据输入的内容生成与内容有关的图片','{\"info\": {\"title\": \"agentBuilder toolset\", \"version\": \"1.0.0\", \"x-is-official\": false}, \"openapi\": \"3.1.0\", \"paths\": {\"/aitools/v1/image_generate\": {\"post\": {\"description\": \"根据输入的内容生成与内容有关的图片\", \"operationId\": \"文生图-hrOgFpJ8\", \"requestBody\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"width\": {\"default\": 1024, \"description\": \"宽度分辨率，支持以下分辨率：512x512, 640x360, 640x480, 640x640, 680x512, 512x680, 768x768, 720x1280, 1280x720, 1024x1024\", \"type\": \"integer\", \"x-display\": true, \"x-from\": 2}, \"prompt\": {\"description\": \"图片描述信息\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}, \"height\": {\"default\": 1024, \"description\": \"高度分辨率，支持以下分辨率：512x512, 640x360, 640x480, 640x640, 680x512, 512x680, 768x768, 720x1280, 1280x720, 1024x1024\", \"type\": \"integer\", \"x-display\": true, \"x-from\": 2}}, \"required\": [\"width\", \"height\", \"prompt\"], \"type\": \"object\"}}}, \"required\": true}, \"responses\": {\"200\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"code\": {\"description\": \"状态码\", \"type\": \"integer\", \"x-display\": true}, \"data\": {\"description\": \"结果\", \"properties\": {\"image_url\": {\"description\": \"图片下载地址\", \"type\": \"string\", \"x-display\": true}, \"image_url_md\": {\"description\": \"图片下载地址markdown格式\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\", \"x-display\": true}, \"message\": {\"description\": \"操作消息\", \"type\": \"string\", \"x-display\": true}, \"sid\": {\"description\": \"会话id\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\"}}}, \"description\": \"success\"}}, \"summary\": \"文生图\"}}}, \"servers\": [{\"description\": \"a server description\", \"url\": \"http://core-aitools:18668\"}]}','2025-10-24 10:00:00.000000','2025-10-24 10:00:00.000000',NULL,NULL,'V1.0',0),(3,'680ab54f','tool@8b2277329821000','图片理解','用户输入一张图片和问题，从而识别出图片中的对象、场景等信息回答用户的问题','{\"info\": {\"title\": \"agentBuilder toolset\", \"version\": \"1.0.0\", \"x-is-official\": false}, \"openapi\": \"3.1.0\", \"paths\": {\"/aitools/v1/image_understanding\": {\"post\": {\"description\": \"用户输入一张图片和问题，从而识别出图片中的对象、场景等信息回答用户的问题\", \"operationId\": \"图片理解-Qo66kqwh\", \"requestBody\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"question\": {\"description\": \"问题\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}, \"image_url\": {\"description\": \"图片\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}}, \"required\": [\"question\", \"image_url\"], \"type\": \"object\"}}}, \"required\": true}, \"responses\": {\"200\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"code\": {\"description\": \"状态码\", \"type\": \"integer\", \"x-display\": true}, \"data\": {\"description\": \"结果\", \"properties\": {\"content\": {\"description\": \"回答内容\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\", \"x-display\": true}, \"message\": {\"description\": \"操作消息\", \"type\": \"string\", \"x-display\": true}, \"sid\": {\"description\": \"会话id\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\"}}}, \"description\": \"success\"}}, \"summary\": \"图片理解\"}}}, \"servers\": [{\"description\": \"a server description\", \"url\": \"http://core-aitools:18668\"}]}','2025-10-24 10:00:00.000000','2025-10-24 10:00:00.000000',NULL,NULL,'V1.0',0),(4,'680ab54f','tool@8b2282136021000','OCR','识别图片或PDF文件中的文字内容，目前支持PDF,PNG,JPG','{\"info\": {\"title\": \"agentBuilder toolset\", \"version\": \"1.0.0\", \"x-is-official\": false}, \"openapi\": \"3.1.0\", \"paths\": {\"/aitools/v1/ocr\": {\"post\": {\"description\": \"识别图片或PDF文件中的文字内容，目前支持PDF,PNG,JPG\", \"operationId\": \"OCR-9dRrb94M\", \"requestBody\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"file_url\": {\"description\": \"图片或pdf文件的url地址\", \"type\": \"string\", \"x-display\": true, \"x-from\": 2}, \"page_end\": {\"default\": -1, \"description\": \"当传入的是pdf链接，表示页码结束范围，-1表示全部页码，从0开始；图片链接不影响该值输入\", \"type\": \"integer\", \"x-display\": true, \"x-from\": 2}, \"page_start\": {\"default\": -1, \"description\": \"当传入的是pdf链接，表示页码开始范围，-1表示全部页码，从0开始；图片链接不影响该值输入\", \"type\": \"integer\", \"x-display\": true, \"x-from\": 2}}, \"required\": [\"file_url\"], \"type\": \"object\"}}}, \"required\": true}, \"responses\": {\"200\": {\"content\": {\"application/json\": {\"schema\": {\"properties\": {\"code\": {\"description\": \"状态码\", \"type\": \"integer\", \"x-display\": true}, \"data\": {\"description\": \"识别结果\", \"items\": {\"properties\": {\"content\": {\"description\": \"页面内容\", \"items\": {\"properties\": {\"source_data\": {\"description\": \"源数据\", \"type\": \"string\", \"x-display\": true}, \"name\": {\"description\": \"名称\", \"type\": \"string\", \"x-display\": true}, \"value\": {\"description\": \"内容\", \"type\": \"string\", \"x-display\": true}}, \"required\": [], \"type\": \"object\"}, \"type\": \"array\", \"x-display\": true}, \"file_index\": {\"description\": \"页码\", \"type\": \"integer\", \"x-display\": true}}, \"required\": [], \"type\": \"object\"}, \"type\": \"array\", \"x-display\": true}, \"message\": {\"description\": \"操作信息\", \"type\": \"string\", \"x-display\": true}}, \"type\": \"object\"}}}, \"description\": \"success\"}}, \"summary\": \"OCR\"}}}, \"servers\": [{\"description\": \"a server description\", \"url\": \"http://core-aitools:18668\"}]}','2025-10-24 10:00:00.000000','2025-10-24 10:00:00.000000',NULL,NULL,'V1.0',0);
/*!40000 ALTER TABLE `tools_schema` ENABLE KEYS */;
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
