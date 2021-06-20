import uWS from 'uWebSockets.js';
import {readResponseFile} from './forums/AbstractForum';

export async function mainWeb(args: string[]): Promise<number> {
  if (args.length != 1) {
    console.log('node parser.js server [pathToFileCache]');
    return 1;
  }
  const fileCachePath = args[0];
  console.log('running webserver with fileCachePath ' + fileCachePath);

  const port = 3000;

  uWS
    .App({})
    .get('/:id', async (res, req) => {
      res.onAborted(() => {
        res.aborted = true;
      });

      // url format is /[uuid]?[baseUrl]
      const r = await getResponse(
        req.getParameter(0),
        fileCachePath,
        req.getQuery()
      );

      if (!res.aborted) {
        res.end(r);
      }
    })
    .listen(port, (token: any) => {
      if (token) {
        console.log('Listening to port ' + port);
      } else {
        console.log('Failed to listen to port ' + port);
      }
    });

  return 0;
}

async function getResponse(
  id: string,
  fileCachePath: string,
  baseUrl: string
): Promise<string> {
  try {
    const response = await readResponseFile(fileCachePath, id, baseUrl);
    return JSON.stringify(response);
  } catch (e) {
    return '' + e;
  }
}
