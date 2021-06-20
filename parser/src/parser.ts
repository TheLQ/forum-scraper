import cheerio from "cheerio";
import fs from "fs";
import { forkBoardParse } from "./forums/ForkBoard";
import { vBulletinParse } from "./forums/vBulletin";
import {Result, SourcePage} from "./utils";
import {smfParse} from "./forums/smf";
import {xenForoParse} from "./forums/XenForo";

export async function mainParser(args: string[]): Promise<number> {
    if (args.length != 3) {
        console.log("node parser.js file [fileCachePath] [pageId] [baseUrl]", args)
        return 1
    }
    const fileCachePath = args[0]
    const pageId = args[1]
    const baseUrl = args[2]

    const result = await readResponseFile(fileCachePath, pageId, baseUrl)
    console.log(JSON.stringify(result))
    return 0
}

export async function readResponseFile(fileCachePath: string, pageId: string, baseUrl: string): Promise<Result> {
    const path = fileCachePath + "/" + pageId + ".response";

    let data: string = await fs.promises.readFile(path, {
            encoding: "utf8"
        })

    return parseFile(data, baseUrl)
}

function parseFile(rawHtml: string, baseUrl: string): Result {
    if (rawHtml.trim().length == 0) {
        throw new Error("EmptyResponse")
    }

    const sourcePage: SourcePage = {
        rawHtml,
        $: cheerio.load(rawHtml, {
            xml: {
                normalizeWhitespace: true,
            },
        }),
        baseUrl,
    }

    const parsers = [
        forkBoardParse,
        vBulletinParse,
        smfParse,
        xenForoParse,
    ]
    let results: Result | null = null;
    for (const parser of parsers) {
        results = parser(sourcePage);
        if (results != null) {
            break;
        }
    }
    if (results == null) {
        throw new Error("no parsers handled file")
    }
    return results;
}
