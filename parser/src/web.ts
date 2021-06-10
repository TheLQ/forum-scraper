import { readResponseFile } from "./parser"

import fastifyImport from 'fastify';
const fastify = fastifyImport({ logger: true })

export async function mainWeb(args: string[]): Promise<number> {
    if (args.length != 1) {
        console.log("node parser.js server <pathToFileCache>")
        return 1
    }
    const filecachePath = args[0]
    console.log("running webserver with filecachePath " + filecachePath)

    // Declare a route
    fastify.route({
        method: 'GET',
        url: '/:id',
        handler: async (request, reply) => {
            try {
                const params = request.params as any;
                const id = params.id
                const response = await readResponseFile(filecachePath + id + ".response")
                return response
            } catch (e) {
                return "" + e;
            }
        }
    })

    // Run the server!
    await fastify.listen(3000)
    return 0
}
