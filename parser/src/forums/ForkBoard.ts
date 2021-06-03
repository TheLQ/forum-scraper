import { CheerioAPI } from "cheerio";
import { assertNotBlank, ForumType, PageType, Result } from "../utils";

export function forkBoardParse(rawHtml: String, $: CheerioAPI): Result | null {
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
        forumType: ForumType.ForkBoard,
        pageType: PageType.Unknown,
        subpages: []
    }
    forkBoardExtract(result, $)
    return result;
}


function forkBoardExtract(result: Result, $: CheerioAPI) {
    if ($("div > .post_header").length == 0) {
        result.pageType = PageType.ForumList
    
        // forum list
        $(".child_section .child_section_title a").each((i, elem) => {
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: elem.attribs.href,
                pageType: PageType.ForumList,
            })
        })
    
        // topic list
        $(".thread_details div:first-child a").each((i, elem) => {
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: elem.attribs.href,
                pageType: PageType.TopicPage,
            })
        })
    } else if ($(".post_body").length == 0) {
        result.pageType = PageType.TopicPage
    } else {
        result.pageType = PageType.Unknown
        return
    }
    
    // Process pages
    $(".page_skip").each((i, elem) => {
        result.subpages.push({
            name: assertNotBlank($(elem).text()),
            url: elem.attribs.href,
            pageType: result.pageType,
        })
    })
}