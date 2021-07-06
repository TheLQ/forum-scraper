-- mysqldump  -r forum-scrape.sql --hex-blob forum-scrape
-- mysql --default-character-set=utf8mb4 -e "SOURCE forum-scrape.sql;" forum-scrape

-- MySQL dump 10.14  Distrib 5.5.68-MariaDB, for Linux (aarch64)
--
-- Host: xanadb.cuxmft1hqhxv.us-east-2.rds.amazonaws.com    Database: forum-scrape
-- ------------------------------------------------------
-- Server version	10.5.8-MariaDB-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `PageRedirects`
--

DROP TABLE IF EXISTS `PageRedirects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PageRedirects` (
  `pageId` tinyblob NOT NULL,
  `redirectUrl` varchar(250) COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `index` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Pages`
--

DROP TABLE IF EXISTS `Pages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Pages` (
  `pageId` binary(16) NOT NULL,
  `sourcePageId` binary(16) DEFAULT NULL,
  `siteid` binary(16) NOT NULL,
  `pageUrl` varchar(250) COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `pageType` enum('ForumList','TopicPage','Unknown') COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `dlstatus` enum('Queued','Download','Parse','Done','Supersede','Error') COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `pageUpdated` datetime NOT NULL,
  `domain` varchar(255) COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `dlStatusCode` int(11) DEFAULT NULL,
  `exception` text COLLATE utf8mb4_unicode_520_ci DEFAULT NULL,
  PRIMARY KEY (`pageId`) USING BTREE,
  UNIQUE KEY `url` (`pageUrl`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Sites`
--

DROP TABLE IF EXISTS `Sites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Sites` (
  `siteId` binary(16) NOT NULL,
  `siteUrl` varchar(255) COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `siteUpdated` datetime NOT NULL,
  `ForumType` enum('ForkBoard','vBulletin','phpBB') COLLATE utf8mb4_unicode_520_ci NOT NULL,
  PRIMARY KEY (`siteId`) USING BTREE,
  UNIQUE KEY `url` (`siteUrl`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-06-19 22:51:05

-- source https://stackoverflow.com/a/58015720/342518
-- and https://mariadb.com/kb/en/guiduuid-performance/

DROP FUNCTION IF EXISTS `BIN_TO_UUID`;
DROP FUNCTION IF EXISTS `UUID_TO_BIN`;

DELIMITER $$

CREATE FUNCTION BIN_TO_UUID(b BINARY(16))
RETURNS CHAR(36)
LANGUAGE SQL  DETERMINISTIC  CONTAINS SQL  SQL SECURITY INVOKER
BEGIN
   DECLARE hexStr CHAR(32);
   SET hexStr = HEX(b);
   RETURN LOWER(CONCAT(
        SUBSTR(hexStr, 1, 8), '-',
        SUBSTR(hexStr, 9, 4), '-',
        SUBSTR(hexStr, 13, 4), '-',
        SUBSTR(hexStr, 17, 4), '-',
        SUBSTR(hexStr, 21)
    ));
END$$


CREATE FUNCTION UUID_TO_BIN(uuid CHAR(36))
RETURNS BINARY(16)
LANGUAGE SQL  DETERMINISTIC  CONTAINS SQL  SQL SECURITY INVOKER
BEGIN
  RETURN UNHEX(CONCAT(
  SUBSTRING(uuid, 1, 8),
  SUBSTRING(uuid, 10, 4),
  SUBSTRING(uuid, 15, 4),
  SUBSTRING(uuid, 20, 4),
  SUBSTRING(uuid, 25))
  );
END$$

DELIMITER ;