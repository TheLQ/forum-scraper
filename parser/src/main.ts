import process from 'process';
import {mainWeb} from './modes/web';
import {mainFile} from './modes/file';
import {mainAudit} from './modes/audit';

async function main() {
  let args = process.argv;
  // strip node command and script name
  args = args.slice(2);

  const mode = args[0];
  args = args.slice(1);

  let exitCode = 0;
  switch (mode) {
    case 'server':
      exitCode = await mainWeb(args);
      break;
    case 'file':
      exitCode = await mainFile(args);
      break;
    case 'audit':
      exitCode = await mainAudit(args);
      break;
    default:
      throw new Error('unknown mode ' + mode);
  }
  return exitCode;
}
main().then(
  exitCode => {
    console.log('init done, exit ' + exitCode);
  },
  err => {
    console.error('INIT FAIL', err);
  }
);
