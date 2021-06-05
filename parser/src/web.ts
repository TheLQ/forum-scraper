import { readResponseFile } from "./parser"

import fastifyImport from 'fastify';
const fastify = fastifyImport({ logger: true })

async function main() {
    if (process.argv.length != 3) {
        console.log("node parser.js <pathToFileCache>")
        process.exit(1)
    }
    const filecachePath = process.argv[2]

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
    try {
        await fastify.listen(3000)
    } catch (err) {
        fastify.log.error(err)
        process.exit(1)
    }
}
main()
