-- Tested with sqlite 3.34.0
-- Generated from SQLiteStudio

CREATE TABLE Sites (
    id        BLOB (16)     NOT NULL
                            PRIMARY KEY
                            UNIQUE,
    url       VARCHAR (255) NOT NULL,
    updated   DATETIME      NOT NULL,
    ForumType               CHECK (ForumType IN ('ForkBoard', 'vBulletin', 'phpBB') )
                            NOT NULL
);

CREATE TABLE Pages (
    id           BLOB (16)    NOT NULL
                              UNIQUE ON CONFLICT FAIL
                              PRIMARY KEY ON CONFLICT FAIL,
    sourceId     BLOB (16),
    siteid       BLOB (16)    NOT NULL,
    url          TEXT         NOT NULL
                              UNIQUE,
    pageType     VARCHAR (10) CHECK (pageType IN ('ForumList', "TopicPage") )
                              NOT NULL,
    dlstatus     VARCHAR (10) CHECK (dlstatus IN ('Queued', 'Download', 'Parse', 'Done', 'Supersede', 'Error') )
                              NOT NULL,
    updated      DATETIME     NOT NULL,
    domain       VARCHAR (50) NOT NULL,
    dlStatusCode INTEGER,
    exception    TEXT
);
