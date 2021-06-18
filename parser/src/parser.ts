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
        console.log("node parser.js file <file path>")
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

    let data: string;
    try {
        data = await fs.promises.readFile(path, {
            encoding: "utf8"
        })
    } catch (e) {
        if (e.code == "ENOENT") {
            data = await extractFile(ARCHIVE_CACHE_FILE, pageId);
        } else {
            throw e;
        }
    }
    return parseFile(data)
}

async function extractFile(archivePath: string, pageId: string): Promise<string> {
    // extract, read, delete (so we don't run out of space), then return
    const result = await execFile('7za', ['e', archivePath, '-otmp', `filecache/${pageId}.response`])
    const data = await fs.promises.readFile(`tmp/${pageId}.response`, {
        encoding: "utf8"
    })
    await fs.promises.rm(`tmp/${pageId}.response`)
    return data;
}

function parseFile(rawHtml: string): Result {
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
