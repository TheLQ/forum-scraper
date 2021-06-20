import {ForumType, Result, SourcePage} from '../utils';
import {AbstractForum} from './AbstractForum';
import type {Element} from 'domhandler';

export class XenForo implements AbstractForum {
  detectForumType(sourcePage: SourcePage): ForumType | null {
    if (sourcePage.rawHtml.indexOf('<html id="XF"') === -1) {
      return null;
    } else {
      return ForumType.XenForo;
    }
  }

  detectLoginRequired(sourcePage: SourcePage): boolean {
    // unknown
    return false;
  }

  getPageLinks(sourcePage: SourcePage): Element[] {
    return sourcePage.$('a[qid="page-nav-other-page"]').get();
  }

  getPostElements(sourcePage: SourcePage): Element[] {
    return sourcePage.$('article[qid="post-text"]').get();
  }

  getSubforumAnchors(sourcePage: SourcePage): Element[] {
    return sourcePage.$('a[qid="forum-item-title"]').get();
  }

  getTopicAnchors(sourcePage: SourcePage): Element[] {
    return sourcePage.$('a[qid="thread-item-title"]').get();
  }

  postProcessing(sourcePage: SourcePage, result: Result): void {}
}
