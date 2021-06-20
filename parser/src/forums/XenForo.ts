import {
    assertNotBlank,
    assertNotNull,
    ForumType,
    getBaseUrl,
    makeUrlWithBase,
    PageType,
    Result,
    SourcePage
} from "../utils";

export function xenForoParse(sourcePage: SourcePage): Result | null {
    if (sourcePage.rawHtml.indexOf('<html id="XF"') == -1) {
        return null
    }

    const result: Result = {
        loginRequired: false,
        forumType: ForumType.XenForo,
        pageType: PageType.Unknown,
        subpages: []
    }
    xenForoExtract(sourcePage, result)
    return result;
}

function xenForoExtract(sourcePage: SourcePage, result: Result) {
    const rawHtml = sourcePage.rawHtml
    const $ = sourcePage.$

    // if (rawHtml.indexOf("document.forms.frmLogin.user.focus") != -1) {
    //     result.loginRequired = true;
    //     return;
    // }

    const baseUrl = getBaseUrl(sourcePage)

    // all links have this for their frontend javascript
    const forums = $('a[qid="forum-item-title"]')
    const topics = $('a[qid="thread-item-title"]')
    const posts = $('article[qid="post-text"]')
    if (forums.length != 0 || topics.length != 0) {
        result.pageType = PageType.ForumList

        // forum list
        for (const forumRaw of forums) {
            const forum = $(forumRaw)
            result.subpages.push({
                pageType: PageType.ForumList,
                url: makeUrlWithBase(baseUrl, forum.attr("href")),
                name: assertNotNull(forum.text()),
            })
        }

        // topic list
        for (const topicRaw of topics) {
            const topic = $(topicRaw)
            result.subpages.push({
                pageType: PageType.TopicPage,
                url: makeUrlWithBase(baseUrl, topic.attr("href")),
                name: assertNotNull(topic.text()),
            })
        }
    } else if (posts.length != 0) {
        result.pageType = PageType.TopicPage
    } else {
        result.pageType = PageType.Unknown
        return
    }

    // get all pages
    $('a[qid="page-nav-other-page"]').each((i, elem) => {
        console.log("find page " + elem.attribs.href)
        result.subpages.push({
            name: assertNotBlank($(elem).text()),
            url: makeUrlWithBase(baseUrl, elem.attribs.href),
            pageType: result.pageType,
        })
    })
}
