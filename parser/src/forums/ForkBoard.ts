import {ForumType, Result, SourcePage} from '../utils';
import {AbstractForum} from './AbstractForum';
import type {Element} from 'domhandler';

export class ForkBoard implements AbstractForum {
  detectForumType(sourcePage: SourcePage): ForumType | null {
    // detect by version string in the footer
    let found = false;
    for (const elem of sourcePage.$('.footer_credits_bar')) {
      if (sourcePage.$(elem).text().indexOf('ForkBoard') !== -1) {
        found = true;
      }
    }
    if (!found) {
      return null;
    } else {
      return ForumType.ForkBoard;
    }
  }

  detectLoginRequired(sourcePage: SourcePage): boolean {
    return false;
  }

  getPageLinks(sourcePage: SourcePage): Element[] {
    return sourcePage.$('.page_skip').get();
  }

  getPostElements(sourcePage: SourcePage): Element[] {
    return sourcePage.$('.post_container').get();
  }

  getSubforumAnchors(sourcePage: SourcePage): Element[] {
    // rawHtml.indexOf('<a href="/thread_new.php?section_id=') != -1
    // Presumably the user or post is deleted but the thread remains.
    // So match the "reply to thread" link
    return sourcePage.$('.child_section .child_section_title a').get();
  }

  getTopicAnchors(sourcePage: SourcePage): Element[] {
    // rawHtml.indexOf('<a href="/post_new.php?thread_id=') != -1
    return sourcePage.$('.thread_details div:first-child a').get();
  }

  postProcessing(sourcePage: SourcePage, result: Result): void {}
}
