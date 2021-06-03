import { Element, Text } from "domhandler";

export interface Result {
    pageType: PageType,
    forumType: ForumType | null,
    subpages: Subpage[],
}

interface Subpage {
    name: String,
    url: String,
    pageType: PageType,
}

// Copied from downloader/src/main/java/sh/xana/forum/server/dbutil/DatabaseStorage.java
export enum PageType {
    ForumList = "ForumList",
    TopicPage = "TopicPage",
    Unknown = "Unknown",
}

export enum ForumType {
    ForkBoard = "ForkBoard",
    vBulletin = "vBulletin",
    phpBB = "phpBB",
}

export function assertNotBlank(value: String | undefined | null): String {
    if (value == null || value == undefined) {
        throw new Error("Value is null")
    }
    const result = value.trim()
    if (result == "") {
        throw new Error("value is blank")
    }
    return result;
}
