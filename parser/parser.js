import cheerio from "cheerio";
import fs from "fs";
import process from "process";

if (process.argv.length != 3) {
    console.log("node parser.js <file path>")
    process.exit(1)
}

const path = process.argv[2]
// console.log(path)

const rawHTML = fs.readFileSync(path, "UTF-8")

const $ = cheerio.load(rawHTML, {
    xml: {
        normalizeWhitespace: true,
      },
});

const results = {
    type: "",
    subpages: []
}

if ($("div > .post").length == 0) {
    results.type = "ForumList"

    // forum list
    $(".child_section .child_section_title a").each((i, elem) => {
        results.subpages.push({
            name: elem.childNodes[0].data,
            url: elem.attribs.href,
            type: "ForumList",
        })
    })
    
    // topic list
    $(".thread_details div:first-child a").each((i, elem) => {
        results.subpages.push({
            name: elem.childNodes[0].data,
            url: elem.attribs.href,
            type: "TopicPage",
        })
    }) 
} else {
    results.type = "TopicPage"
}

$(".page_skip").each((i, elem) => {
    results.subpages.push({
        name: elem.childNodes[0].data,
        url: elem.attribs.href,
        type: results.type,
    })
})

console.log(JSON.stringify(results))
