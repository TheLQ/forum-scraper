import {Cheerio, CheerioAPI} from 'cheerio';
import {Element, Node} from 'domhandler';

export interface SourcePage {
  rawHtml: string;
  $: CheerioAPI;
  baseUrl: string;
}

export interface Result {
  loginRequired: boolean;
  pageType: PageType;
  forumType: ForumType | null;
  subpages: Subpage[];
}

interface Subpage {
  name: string;
  url: string;
  pageType: PageType;
}

// Copied from downloader/src/main/java/sh/xana/forum/server/dbutil/DatabaseStorage.java
export enum PageType {
  ForumList = 'ForumList',
  TopicPage = 'TopicPage',
  Unknown = 'Unknown',
}

export enum ForumType {
  ForkBoard = 'ForkBoard',
  vBulletin = 'vBulletin',
  phpBB = 'phpBB',
  XenForo = 'XenForo',
  SMF = 'SMF',
}

export function assertNotNull(value: string | undefined | null): string {
  if (value === null || value === undefined) {
    throw new Error('Value is null');
  }
  return value;
}

export function assertNotBlank(valueRaw: string | undefined | null): string {
  const value = assertNotNull(valueRaw).trim();
  if (value === '') {
    throw new Error('value is blank');
  }
  return value;
}

export function getBaseUrl(sourcePage: SourcePage): string {
  const baseQuery = sourcePage.$('head > base');
  if (baseQuery.length !== 0) {
    return assertNotBlank(baseQuery.last().attr('href'));
  } else {
    // fallback to baseUrl
    return sourcePage.baseUrl;
  }
}

export function makeUrlWithBase(
  baseUrl: string,
  mainUrl: string | undefined | null
): string {
  mainUrl = assertNotBlank(mainUrl);

  if (mainUrl.startsWith('http:') || mainUrl.startsWith('https:')) {
    return mainUrl;
  }
  if (mainUrl.startsWith('/')) {
    mainUrl = mainUrl.substr(1);
  }
  if (!baseUrl.endsWith('/')) {
    baseUrl = baseUrl + '/';
  }
  return baseUrl + mainUrl;
}

export function getFirstMatch(
  sourcePage: SourcePage,
  elements: Cheerio<Node>,
  errorMessage: string,
  ignoreExtra = false
): Element {
  const arr = elements.get();
  if (arr.length === 0) {
    throw new Error("didn't find anything for " + errorMessage);
  } else if (!ignoreExtra && arr.length > 1) {
    for (const node of arr) {
      console.log('FAILING NODE ', sourcePage.$(node).parent().html());
    }
    console.log('match', arr[0] == arr[1]);
    throw new Error('found too many ' + arr.length + ' for ' + errorMessage);
  }

  const first = arr[0];
  if (!(first instanceof Element)) {
    throw new Error('Found a ' + typeof arr[0] + ' not element');
  }
  return first;
}

export function anchorIsNavNotLink(elem: Element) {
  return elem.attribs.name !== undefined && elem.attribs.href === undefined;
}
