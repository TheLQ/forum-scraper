import cheerio from "cheerio";
import fs from "fs";
import child_process from "child_process";
import { forkBoardParse } from "./forums/ForkBoard";
import { vBulletinParse } from "./forums/vBulletin";
import { Result } from "./utils";
import util from 'util';

const execFile = util.promisify(child_process.execFile)
const ARCHIVE_CACHE_FILE = 'archiveCache.7z';

export async function mainParser(args: string[]): Promise<number> {
    if (args.length != 2) {
        console.log("node parser.js file <file path>", args)
        return 1
    }
    const fileCachePath = args[0]
    const pageId = args[1]

    const result = await readResponseFile(fileCachePath, pageId)
    console.log(JSON.stringify(result))
    return 0
}

export async function readResponseFile(fileCachePath: string, pageId: string): Promise<Result> {
    const path = fileCachePath + "/" + pageId + ".response";

    let data: string = await fs.promises.readFile(path, {
            encoding: "utf8"
        })

    return parseFile(data)
}

function parseFile(rawHtml: string): Result {
    if (rawHtml.trim().length == 0) {
        throw new Error("EmptyResponse")
    }

    const $ = cheerio.load(rawHtml, {
        xml: {
            normalizeWhitespace: true,
        },
    });

    const parsers = [
        forkBoardParse,
        vBulletinParse,
    ]
    let results: Result | null = null;
    for (const parser of parsers) {
        results = parser(rawHtml, $);
        if (results != null) {
            break;
        }
    }
    if (results == null) {
        throw new Error("no parsers handled file")
    }
    return results;
}
