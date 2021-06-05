import cheerio from "cheerio";
import fs from "fs";
import process from "process";
import { forkBoardParse } from "./forums/ForkBoard";
import { vBulletinParse } from "./forums/vBulletin";
import { Result } from "./utils";

function main() {
    if (process.argv.length != 3) {
        console.log("node parser.js <file path>")
        process.exit(1)
    }
    const path = process.argv[2]

    readResponseFile(path)
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

// main();
