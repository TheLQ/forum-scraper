import {
  assertNotNull,
  ForumType,
  getBaseUrl,
  makeUrlWithBase,
  PageType,
  Result,
  SourcePage,
} from '../utils';
import cheerio from 'cheerio';
import {XenForo} from './XenForo';
import type {Element} from 'domhandler';
import fs from 'fs';
import {vBulletin} from './vBulletin';

export interface AbstractForum {
  detectForumType(sourcePage: SourcePage): ForumType | null;

  detectLoginRequired(sourcePage: SourcePage): boolean;

  getPageLinks(sourcePage: SourcePage): Element[];

  getPostElements(sourcePage: SourcePage): Element[];

  getSubforumAnchors(sourcePage: SourcePage): Element[];

  getTopicAnchors(sourcePage: SourcePage): Element[];

  postProcessing(sourcePage: SourcePage, result: Result): void;
}

export async function readResponseFile(
  fileCachePath: string,
  pageId: string,
  baseUrl: string
): Promise<Result> {
  const path = fileCachePath + '/' + pageId + '.response';

  const data: string = await fs.promises.readFile(path, {
    encoding: 'utf8',
  });

  return process(data, baseUrl);
}

function process(rawHtml: string, baseUrlBackup: string): Result {
  if (rawHtml.trim().length === 0) {
    throw new Error('EmptyResponse');
  }

  const sourcePage: SourcePage = {
    rawHtml,
    $: cheerio.load(rawHtml, {
      xml: {
        normalizeWhitespace: true,
      },
    }),
    baseUrl: baseUrlBackup,
  };

  const parsers = [new XenForo(), new vBulletin()];

  const baseUrl = getBaseUrl(sourcePage);
  const $ = sourcePage.$;

  for (const parser of parsers) {
    const forumType = parser.detectForumType(sourcePage);
    if (forumType === null) {
      continue;
    }
    const result: Result = {
      loginRequired: parser.detectLoginRequired(sourcePage),
      forumType,
      pageType: PageType.Unknown,
      subpages: [],
    };

    if (result.loginRequired) {
      return result;
    }

    for (const forumRaw of parser.getSubforumAnchors(sourcePage)) {
      result.pageType = PageType.ForumList;

      const forum = $(forumRaw);
      result.subpages.push({
        pageType: PageType.ForumList,
        url: makeUrlWithBase(baseUrl, forum.attr('href')),
        name: assertNotNull(forum.text()),
      });
    }

    for (const topicRaw of parser.getTopicAnchors(sourcePage)) {
      result.pageType = PageType.ForumList;

      const topic = $(topicRaw);
      result.subpages.push({
        pageType: PageType.TopicPage,
        url: makeUrlWithBase(baseUrl, topic.attr('href')),
        name: assertNotNull(topic.text()),
      });
    }

    for (const postRaw of parser.getPostElements(sourcePage)) {
      if (
        result.pageType !== PageType.TopicPage &&
        result.pageType !== PageType.Unknown
      ) {
        throw new Error('detected both post elements and subforum/topic links');
      } else {
        result.pageType = PageType.TopicPage;
      }

      break;
    }

    for (const pageLink of parser.getPageLinks(sourcePage)) {
      result.subpages.push({
        name: $(pageLink).text(),
        url: makeUrlWithBase(baseUrl, pageLink.attribs.href),
        pageType: result.pageType,
      });
    }

    return result;
  }

  // above loop didn't find anything
  throw new Error('no parsers handled file');
}
