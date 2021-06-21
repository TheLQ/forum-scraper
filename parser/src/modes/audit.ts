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
  const filesLength = files.length;
  console.log('file ', files[0]);
  console.log('length ', filesLength);
  const results = await asyncPool(10, files, async rawFileName => {
    try {
      const index = files.indexOf(rawFileName);
      if (index % 100 === 0) {
        console.log(`${index} of ${filesLength}`);
      }
      if (rawFileName.indexOf('.response') == -1) {
        return;
      }
      const newPageId = rawFileName.substr(0, rawFileName.indexOf('.'));
      let result;
      try {
        result = await readResponseFile(
          fileCachePath,
          newPageId,
          'https://BIG_AUDIT/'
        );
      } catch (e) {
        if (e.message == 'EmptyResponse') {
          return;
        } else {
          throw e;
        }
      }

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
