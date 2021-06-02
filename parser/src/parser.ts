import cheerio from "cheerio";
import fs from "fs";
import process from "process";
import { forkBoardParse } from "./forums/ForkBoard";
import { Result } from "./utils";

function main() {
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

    const parsers = [
        forkBoardParse
    ]
    let results: Result | null = null;
    for (const parser of parsers) {
        results = parser($);
        if (results != null) {
            break;
        }
    }
    console.log(JSON.stringify(results))
}
main();
