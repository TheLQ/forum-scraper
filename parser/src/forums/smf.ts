import {
    assertNotBlank,
    assertNotNull,
    ForumType,
    getBaseUrl, getFirstMatch,
    makeUrlWithBase,
    PageType,
    Result,
    SourcePage
} from "../utils";

export function smfParse(sourcePage: SourcePage): Result | null {
    // every page defines some variables at the top
    if (sourcePage.rawHtml.indexOf("var smf_theme_url") == -1) {
        return null
    }

    const result: Result = {
        loginRequired: false,
        forumType: ForumType.SMF,
        pageType: PageType.Unknown,
        subpages: []
    }
    smfExtract(sourcePage, result)
    return result;
}

/**
 * SMF gives every topic and post's wrapping div a unique id attribute.
 * Extract IDs with rudimentary regex on the raw HTML,
 * then get element with DOM navigation
 */
function smfExtract(sourcePage: SourcePage, result: Result) {
    const rawHtml = sourcePage.rawHtml
    const $ = sourcePage.$

    // some javascript that seems to only be on the login page
    if (rawHtml.indexOf("document.forms.frmLogin.user.focus") != -1) {
        result.loginRequired = true;
        return;
    }

    const baseUrl = getBaseUrl(sourcePage)

    const forums = [...rawHtml.matchAll(/name=\"(?<id>b[0-9]+)\"/g)];
    // both the topiclist entry and the message posts use the same id...
    const posts = [...rawHtml.matchAll(/id=\"(?<id>msg_[0-9]+)\"/g)];
    if (forums.length != 0 || $("#messageindex").length != 0) {
        result.pageType = PageType.ForumList

        // forum list
        for (const forum of forums) {
            const id = assertNotBlank(forum.groups?.id);
            // SubForum links have a convenient unique name attribute
            const elem = getFirstMatch($(`a[name='${id}']`), "forum id " + id);
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
        for (const post of posts) {
            const id = assertNotBlank(post.groups?.id);
            const elem = getFirstMatch($(`span[id='${id}'] a`), "topic id " + id);
            result.subpages.push({
                pageType: PageType.TopicPage,
                url: makeUrlWithBase(baseUrl, $(elem).attr("href")),
                name: assertNotNull($(elem).text()),
            })
        }
    } else if (posts.length != 0) {
        result.pageType = PageType.TopicPage
    } else {
        result.pageType = PageType.Unknown
        return
    }

    // get all pages
    $(".pagelinks .navPages").each((i, elem) => {
        console.log("find page " + elem.attribs.href)
        result.subpages.push({
            name: assertNotBlank($(elem).text()),
            url: makeUrlWithBase(baseUrl, elem.attribs.href),
            pageType: result.pageType,
        })
    })

    for (const subpage of result.subpages) {
        subpage.url = subpage.url.replace(/\?PHPSESSID=[A-Za-z0-9]+/, "")
    }
}
