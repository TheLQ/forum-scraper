import { readResponseFile } from "./parser"
import uWS from "uWebSockets.js"

export async function mainWeb(args: string[]): Promise<number> {
    if (args.length != 1) {
        console.log("node parser.js server <pathToFileCache>")
        return 1
    }
    const filecachePath = args[0]
    console.log("running webserver with filecachePath " + filecachePath)

    const port = 3000

    uWS.App({
    }).get("/:id", async (res: any, req: any) => {
        /* Can't return or yield from here without responding or attaching an abort handler */
        res.onAborted(() => {
            res.aborted = true;
        });

        let r = await getResponse(req.getParameter(0), filecachePath)

        if (!res.aborted) {
            res.end(r);
        }
    }).listen(port, (token: any) => {
        if (token) {
            console.log('Listening to port ' + port);
        } else {
            console.log('Failed to listen to port ' + port);
        }
    });

    return 0
}

async function getResponse(id: string, filecachePath: string): Promise<string> {
    try {
        const response = await readResponseFile(filecachePath + id + ".response")
        return JSON.stringify(response)
    } catch (e) {
        return "" + e;
    }
}
