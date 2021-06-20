import { CheerioAPI } from "cheerio";
import {
    anchorIsNavNotLink,
    assertNotBlank,
    assertNotNull,
    ForumType,
    getBaseUrl,
    makeUrlWithBase,
    PageType,
    Result,
    SourcePage
} from "../utils";

export function vBulletinParse(sourcePage: SourcePage): Result | null {
    // js init function they all seem to call
    if (sourcePage.rawHtml.indexOf("vBulletin_init();") == -1) {
        return null
    }

    const result: Result = {
        loginRequired: false,
        forumType: ForumType.vBulletin,
        pageType: PageType.Unknown,
        subpages: []
    }
    vBulletinExtract(sourcePage, result)
    return result;
}

/**
 * vBulletin gives every topic and post's wrapping div a unique id attribute. 
 * Extract IDs with rudimentary regex on the raw HTML, 
 * then get element with DOM navigation
 */
function vBulletinExtract(sourcePage: SourcePage, result: Result) {
    const rawHtml = sourcePage.rawHtml
    const $ = sourcePage.$

    if (rawHtml.indexOf("<!-- permission error message - user not logged in -->") != -1) {
        result.loginRequired = true;
        return;
    }

    const baseUrl = getBaseUrl(sourcePage)

    const forums = [...rawHtml.matchAll(/id=\"(?<id>f[0-9]+)\"/g)];
    const topics = [...rawHtml.matchAll(/id=\"(?<id>thread_title_[0-9]+)\"/g)];
    const posts = [...rawHtml.matchAll(/id=\"(?<id>td_post_[0-9]+)\"/g)];
    if (forums.length != 0 || topics.length != 0) {
        result.pageType = PageType.ForumList

        // forum list
        for (const forum of forums) {
            const id = assertNotBlank(forum.groups?.id);
            // newer versions use div and headers, old versions use table and css markup
            let elem = $(`div[id='${id}'] h2 a, div[id='${id}'] h3 a, td[id='${id}'] a`);
            if (elem.length == 0) {
                throw new Error("didn't find anything for id " + id)
            }
            elem = elem.first()
            try {
                result.subpages.push({
                    pageType: PageType.ForumList,
                    url: makeUrlWithBase(baseUrl, elem.attr("href")),
                    // topic can be blank, vBulliten bug?
                    name: assertNotNull(elem.text()),
                })
            } catch (e) {
                throw e;
            }
        }

        // topic list
        for (const topic of topics) {
            const id = assertNotBlank(topic.groups?.id);
            let elem = $(`a[id='${id}']`);
            if (elem.length == 0) {
                throw new Error("didn't find anything for id " + id)
            }
            elem = elem.first()
            result.subpages.push({
                pageType: PageType.TopicPage,
                url: makeUrlWithBase(baseUrl, $(elem).attr("href")),
                // topic can be blank, vBulliten bug?
                name: assertNotNull($(elem).text()),
            })
        }

        // TopicList uses regular page number navigation
        $(".pagenav a").each((i, elem) => {
            if (anchorIsNavNotLink(elem)) {
                return
            }
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: makeUrlWithBase(baseUrl, elem.attribs.href),
                pageType: result.pageType,
            })
        })
    } else if (posts.length != 0) {
        result.pageType = PageType.TopicPage

        // infinite scroll only gives us page by page...
        const next = $("link[rel='next']").first()
        if (next.length != 0) {
            // topic has multiple pages
            result.subpages.push({
                name: "",
                url: makeUrlWithBase(baseUrl, next.attr("href")),
                pageType: result.pageType,
            })
        } else {
            // try classic non-infinite scroll
            const pages = $(".pagenav a")
            for (const page of pages) {
                if (anchorIsNavNotLink(page)) {
                    continue
                }
                result.subpages.push({
                    name: "",
                    url: makeUrlWithBase(baseUrl, $(page).attr("href")),
                    pageType: result.pageType,
                })

            }
        }


    } else {
        result.pageType = PageType.Unknown
        return
    }

    for (const subpage of result.subpages) {
        let newUrl = subpage.url

        /*
        The marketplace module seems to use either client js or cookies for state tracking.
        But if your not a browser, it falls back tacking on the next page to end of the url
        eg page-50/page-70 to page-50/page-70/page-90 to page page-50/page-70/page-90/page-110

        Sometimes a custom id is also added (but not on my browser?), which similarly infinitely spans

        Not only is this url useless to archive, it causes "Data too long for column" SQL errors
        */

        // strip infinite search id's
        // note this exists on both marketplace ForumList and even topic 
        while (true) {
            // match s=[32 character hex id]
            newUrl = newUrl.replace(/s=[0-9a-zA-Z]{32}&*/, "")
            // match double directory separator // but not the http:// one
            newUrl = newUrl.replace(/(?<!http[s]*:)\/\//g, "/")

            // match first duplicate of /page-50/page-70/
            const pages = newUrl.match(/(?<first>\/page-[0-9]+\/)page-[0-9]+\//)
            const firstPage = pages?.groups?.first
            if (firstPage !== undefined) {
                newUrl = newUrl.replace(firstPage, "/")
            }

            if (newUrl != subpage.url) {
                subpage.url = newUrl
            } else {
                break;
            }
        }

        /*
        There multi-page plugin uses ?ispreloading magic url
        */
        subpage.url = subpage.url.replace("?ispreloading=1", "")
    }
}
