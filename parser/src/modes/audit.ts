import fs from 'fs';
import asyncPool from 'tiny-async-pool';
import {readResponseFile} from '../forums/AbstractForum';

export async function mainAudit(args: string[]) {
  if (args.length !== 1) {
    console.log('node parser.js audit [fileCachePath]', args);
    return 1;
  }
  const fileCachePath = args[0];

  console.log('BIG PARSE');
  const files = await fs.promises.readdir(fileCachePath);
  console.log('file ', files[0]);
  console.log('length ', files.length);
  const results = await asyncPool(10, files, async rawFileName => {
    try {
      if (rawFileName.indexOf('.response') == -1) {
        return;
      }
      const newPageId = rawFileName.substr(0, rawFileName.indexOf('.'));
      const result = await readResponseFile(
        fileCachePath,
        newPageId,
        'https://BIG_AUDIT/'
      );
      if (JSON.stringify(result) == '{ff}') {
        console.log('true');
      }
    } catch (e) {
      e.message += ' - failed in ' + rawFileName;
      throw e;
    }
  });

  return 0;
}
