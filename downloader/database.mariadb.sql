-- --------------------------------------------------------
-- Host:
-- Server version:               10.5.8-MariaDB-log - Source distribution
-- Server OS:                    Linux
-- HeidiSQL Version:             11.2.0.6290
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Dumping database structure for forum-scrape
CREATE DATABASE IF NOT EXISTS `forum-scrape` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;
USE `forum-scrape`;

-- Dumping structure for table forum-scrape.Pages
CREATE TABLE IF NOT EXISTS `Pages` (
  `id` binary(16) NOT NULL,
  `sourceId` binary(16) DEFAULT NULL,
  `siteid` binary(16) NOT NULL,
  `url` varchar(2048) NOT NULL,
  `pageType` enum('ForumList','TopicPage') NOT NULL,
  `dlstatus` enum('Queued','Download','Parse','Done','Supersede','Error') NOT NULL,
  `updated` datetime NOT NULL,
  `domain` varchar(255) NOT NULL,
  `dlStatusCode` int(4) DEFAULT NULL,
  `exception` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `url` (`url`) USING HASH
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dumping structure for table forum-scrape.Sites
CREATE TABLE IF NOT EXISTS `Sites` (
  `id` binary(16) NOT NULL,
  `url` varchar(255) NOT NULL,
  `updated` datetime NOT NULL,
  `ForumType` enum('ForkBoard','vBulletin','phpBB') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `url` (`url`) USING HASH
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Data exporting was unselected.

/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
