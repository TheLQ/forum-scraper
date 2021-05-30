import { Element, Text } from "domhandler";

export interface Result {
    type: PageType,
    forumType: ForumType | null,
    subpages: Subpage[],
}

interface Subpage {
    name: String,
    url: String,
    type: PageType,
}

// Copied from downloader/src/main/java/sh/xana/forum/server/dbutil/DatabaseStorage.java
export enum PageType {
    ForumList,
    TopicPage,
}

export enum ForumType {
    ForkBoard,
    vBulletin,
    phpBB,
}

export function getTextChild(elem: Element): Text {
    const child = elem.childNodes[0]
    if (child instanceof Text) {
        return child;
    } else {
        throw new Error("unexpected child " + child)
    }
}