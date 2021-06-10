import cheerio from "cheerio";
import fs from "fs";
import process from "process";
import { forkBoardParse } from "./forums/ForkBoard";
import { vBulletinParse } from "./forums/vBulletin";
import { Result } from "./utils";

export async function mainParser(args: string[]): Promise<number> {
    if (args.length != 1) {
        console.log("node parser.js file <file path>")
        return 1
    }
    const path = args[0]

    const result = await readResponseFile(path)
    console.log(JSON.stringify(result))
    return 0
}

export async function readResponseFile(path: string) {
    const data = await fs.promises.readFile(path, {
        encoding: "utf8"
    })
    return parseFile(data)
}

function parseFile(rawHtml: string) {
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
