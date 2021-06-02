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

export function getTextChild(elem: Element): Text {
    const child = elem.childNodes[0]
    if (child instanceof Text) {
        return child;
    } else {
        throw new Error("unexpected child " + child)
    }
}