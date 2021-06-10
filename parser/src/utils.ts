import { CheerioAPI } from "cheerio";
import { Element, Text } from "domhandler";

export interface Result {
    loginRequired: boolean,
    pageType: PageType,
    forumType: ForumType | null,
    subpages: Subpage[],
}

interface Subpage {
    name: string,
    url: string,
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

export function assertNotNull(value: string | undefined | null): string {
    if (value == null || value == undefined) {
        throw new Error("Value is null")
    }
    return value
}

export function assertNotBlank(valueRaw: string | undefined | null): string {
    const value = assertNotNull(valueRaw).trim()
    if (value == "") {
        throw new Error("value is blank")
    }
    return value;
}

export function getBaseUrl($: CheerioAPI) {
    const baseQuery = $("head > base")
    if (baseQuery.length != 1) {
        throw new Error("cannot find base")
    }
    const baseUrl = assertNotBlank($(baseQuery).attr('href'))
    return baseUrl;
}

export function makeUrlWithBase(baseUrl: string, mainUrl: string | undefined | null): string {
    mainUrl = assertNotBlank(mainUrl)

    if (mainUrl.startsWith("http:") || mainUrl.startsWith("https:")) {
        return mainUrl
    }
    if (mainUrl.startsWith("/")) {
        mainUrl = mainUrl.substr(1)
    }
    if (!baseUrl.endsWith("/")) {
        baseUrl = baseUrl + "/"
    }
    return baseUrl + mainUrl
}