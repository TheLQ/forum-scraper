import {Cheerio, CheerioAPI, Element} from "cheerio";

export interface SourcePage {
    rawHtml: string,
    $: CheerioAPI,
    baseUrl: string,
}

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
    XenForo = "XenForo",
    SMF = "SMF",
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

export function getBaseUrl(sourcePage: SourcePage): string {
    const baseQuery = sourcePage.$("head > base")
    if (baseQuery.length != 0) {
        return assertNotBlank(baseQuery.last().attr('href'));
    } else {
        // fallback to baseUrl
        return sourcePage.baseUrl
    }
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

export function getFirstMatch(element: Cheerio<Element>, errorMessage: string) {
    if (element.length == 0) {
        throw new Error("didn't find anything for " + errorMessage)
    } else if (element.length > 1) {
        throw new Error("found too many " +element.length + " for " + errorMessage)
    }
    return element.first();
}
