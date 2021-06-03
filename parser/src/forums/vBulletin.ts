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
    
}