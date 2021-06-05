import { CheerioAPI } from "cheerio";
import { assertNotBlank, ForumType, PageType, Result } from "../utils";

export function vBulletinParse(rawHtml: String, $: CheerioAPI): Result | null {
    // js init function they all seem to call
    let found = rawHtml.indexOf("vBulletin_init();") != -1

    const result: Result = {
        forumType: ForumType.vBulletin,
        pageType: PageType.Unknown,
        subpages: []
    }
    vBulletinExtract(result, rawHtml, $)
    return result;
}

/**
 * vBulletin gives every topic and post's wrapping div a unique id attribute. 
 * Extract IDs with rudimentary regex on the raw HTML, 
 * then get element with DOM navigation
 */
function vBulletinExtract(result: Result, rawHtml: String, $: CheerioAPI) {
    const forums = [...rawHtml.matchAll(/id=\"(?<id>f[0-9]+)\"/g)];
    const topics = [...rawHtml.matchAll(/id=\"(?<id>thread_title_[0-9]+)\"/g)];
    const posts = [...rawHtml.matchAll(/id=\"(?<id>td_post_[0-9]+)\"/g)];
    if (forums.length != 0 || topics.length != 0) {
        result.pageType = PageType.ForumList

        // forum list
        for (const forum of forums) {
            const id = assertNotBlank(forum.groups?.id);
            const elem = $(`div[id='${id}'] h2 a, div[id='${id}'] h3 a`).first();
            if (elem.length == 0) {
                throw new Error("didn't find anything for id " + id)
            }
            try {
                result.subpages.push({
                    pageType: PageType.ForumList,
                    url: assertNotBlank($(elem).attr("href")),
                    name: assertNotBlank($(elem).text()),
                })
            } catch (e) {
                throw e;
            }

        }

        // topic list
        for (const topic of topics) {
            const id = assertNotBlank(topic.groups?.id);
            const elem = $(`a[id='${id}']`).first();
            result.subpages.push({
                pageType: PageType.TopicPage,
                url: assertNotBlank($(elem).attr("href")),
                name: assertNotBlank($(elem).text()),
            })
        }

        // TopicList uses regular page number navigation
        $(".pagenav a").each((i, elem) => {
            // skip name anchors
            if (elem.attribs.name != undefined && elem.attribs.href == undefined) {
                return;
            }
            result.subpages.push({
                name: assertNotBlank($(elem).text()),
                url: elem.attribs.href,
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
                url: assertNotBlank(next.attr("href")),
                pageType: result.pageType,
            })
        }
    } else {
        result.pageType = PageType.Unknown
        return
    }

    /*
    The marketplace module seems to use either client js or cookies for state tracking.
    But if your not a browser, it falls back tacking on the next page to end of the url
    eg page-50/page-70 to page-50/page-70/page-90 to page page-50/page-70/page-90/page-110

    Sometimes a custom id is also added (but not on my browser?), which similarly infinitely spans

    Not only is this url useless to archive, it causes "Data too long for column" SQL errors
    */
    for (const subpage of result.subpages) {
        if (subpage.url.indexOf("/marketplace/") != -1) {
            let newUrl = subpage.url

            // strip infinite search id's
            while (true) {
                newUrl = newUrl.replace(/s=[0-9a-zA-Z]{32}/, "")
                newUrl = newUrl.replace("&amp;", "")
                newUrl = newUrl.replace("&", "")
                newUrl = newUrl.replace("//", "/")

                if (newUrl != subpage.url) {
                    subpage.url = newUrl
                } else {
                    break;
                }
            }

            // strip infinite page numbers
            const pages = newUrl.match(/\/page-[0-9]+\//g)
            if (pages != null && pages.length > 1) {
                // we want the last one
                pages.pop()
                // but not the rest
                for (const page of pages) {
                    newUrl = newUrl.replace(page, "")
                }
                subpage.url = newUrl
            }
        }
    }
}