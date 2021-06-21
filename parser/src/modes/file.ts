import {readResponseFile} from '../forums/AbstractForum';
import asyncPool from 'tiny-async-pool';
import fs from 'fs';

export async function mainFile(args: string[]): Promise<number> {
  if (args.length !== 3) {
    console.log('node parser.js file [fileCachePath] [pageId] [baseUrl]', args);
    return 1;
  }
  const fileCachePath = args[0];
  const pageId = args[1];
  const baseUrl = args[2];

  const result = await readResponseFile(fileCachePath, pageId, baseUrl);
  console.log(JSON.stringify(result));

  return 0;
}
