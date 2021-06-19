import { CheerioAPI } from "cheerio";
import {assertNotBlank, ForumType, getBaseUrl, makeUrlWithBase, PageType, Result, SourcePage} from "../utils";

export function forkBoardParse(sourcePage: SourcePage): Result | null {
    const $ = sourcePage.$

    // detect by version string in the footer
    let found = false;
    $(".footer_credits_bar").each((i, elem) => {
        if ($(elem).text().indexOf("ForkBoard") != -1) {
            found = true
        }
    })
    if (!found) {
        return null;
    }

    const result: Result = {
        loginRequired: false,
        forumType: ForumType.ForkBoard,
        pageType: PageType.Unknown,
        subpages: []
    }
    forkBoardExtract(sourcePage, result)
    return result;
}


function forkBoardExtract(sourcePage: SourcePage, result: Result) {
    const rawHtml = sourcePage.rawHtml
    const $ = sourcePage.$

    const baseUrl = getBaseUrl(sourcePage)

    const subforums = $(".child_section .child_section_title a")
    const threads = $(".thread_details div:first-child a")
    if (subforums.length != 0 || threads.length != 0 || rawHtml.indexOf("<a href=\"/thread_new.php?section_id=") != -1) {
        result.pageType = PageType.ForumList
    
        // forum list
        subforums.each((i, elem) => {
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: makeUrlWithBase(baseUrl, elem.attribs.href),
                pageType: PageType.ForumList,
            })
        })
    
        // topic list
        threads.each((i, elem) => {
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: makeUrlWithBase(baseUrl, elem.attribs.href),
                pageType: PageType.TopicPage,
            })
        })
    } else if (rawHtml.indexOf("<a href=\"/post_new.php?thread_id=") != -1) {
        // Note $(".post_body").length != 0 doesn't work because completely empty threads are allowed to exist...
        // Presumably the user or post is deleted but the thread remains.
        // So match the "reply to thread" link
        result.pageType = PageType.TopicPage
    } else {
        result.pageType = PageType.Unknown
        return
    }
    
    // Process pages
    $(".page_skip").each((i, elem) => {
        result.subpages.push({
            name: assertNotBlank($(elem).text()),
            url: makeUrlWithBase(baseUrl, elem.attribs.href),
            pageType: result.pageType,
        })
    })
}
