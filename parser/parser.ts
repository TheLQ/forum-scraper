import cheerio from "cheerio";
import fs from "fs";
import process from "process";
import { getTextChild, Result, PageType, ForumType } from "./utils";

if (process.argv.length != 3) {
    console.log("node parser.js <file path>")
    process.exit(1)
}
const path = process.argv[2]

const rawHTML = fs.readFileSync(path, "utf8")
const $ = cheerio.load(rawHTML, {
    xml: {
        normalizeWhitespace: true,
    },
});

const results: Result = {
    // placeholders
    type: PageType.ForumList,
    forumType: null,
    //
    subpages: []
}

// forum detection
$(".footer_credits_bar").each((i, elem) => {
    const innerText = getTextChild(elem);
    if (innerText.data.indexOf("ForkBoard") != -1) {
        results.forumType = ForumType.ForkBoard
    }
})
if (results.forumType == null) {
    throw new Error("Unknown forum type")
}

// Pages
if (results.forumType == ForumType.ForkBoard) {
    if ($("div > .post_header").length == 0) {
        results.type = PageType.ForumList
    
        // forum list
        $(".child_section .child_section_title a").each((i, elem) => {
            const innerText = getTextChild(elem);
            results.subpages.push({
                name: innerText.data,
                url: elem.attribs.href,
                type: PageType.ForumList,
            })
        })
    
        // topic list
        $(".thread_details div:first-child a").each((i, elem) => {
            const innerText = getTextChild(elem);
            results.subpages.push({
                name: innerText.data,
                url: elem.attribs.href,
                type: PageType.TopicPage,
            })
        })
    } else {
        results.type = PageType.TopicPage
    }
    
    $(".page_skip").each((i, elem) => {
        const innerText = getTextChild(elem);
        results.subpages.push({
            name: innerText.data,
            url: elem.attribs.href,
            type: results.type,
        })
    })
} else {
    throw new Error("unhandled forumtype " + results.forumType)
}


console.log(JSON.stringify(results))
