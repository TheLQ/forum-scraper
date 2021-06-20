import {
  assertNotBlank,
  ForumType,
  getFirstMatch,
  Result,
  SourcePage,
} from '../utils';
import {AbstractForum} from './AbstractForum';
import {Element} from 'cheerio';

export class SMF implements AbstractForum {
  detectForumType(sourcePage: SourcePage): ForumType | null {
    if (sourcePage.rawHtml.indexOf('var smf_theme_url') === -1) {
      return null;
    } else {
      return ForumType.SMF;
    }
  }

  detectLoginRequired(sourcePage: SourcePage): boolean {
    // some javascript that seems to only be on the login page
    return (
      sourcePage.rawHtml.indexOf('document.forms.frmLogin.user.focus') !== -1
    );
  }

  getPageLinks(sourcePage: SourcePage): Element[] {
    return sourcePage.$('.pagelinks .navPages').get();
  }

  getPostElements(sourcePage: SourcePage): Element[] {
    if (sourcePage.$("#messageindex").length != 0) {
      return []
    }
    return this.getMessage(sourcePage, '');
  }

  getSubforumAnchors(sourcePage: SourcePage): Element[] {
    const forums = [...sourcePage.rawHtml.matchAll(/name="(?<id>b[0-9]+)"/g)];

    // forum list
    const result: Element[] = [];
    for (const forum of forums) {
      const id = assertNotBlank(forum.groups?.id);
      // SubForum links have a convenient unique name attribute
      const elem = getFirstMatch(
        sourcePage.$(`a[name='${id}']`),
        'forum id ' + id
      );
      result.push(elem);
    }

    return result;
  }

  getTopicAnchors(sourcePage: SourcePage): Element[] {
    // both the topiclist entry and the message posts use the same id... so make sure we are on the forumlist page
    if (sourcePage.$("#messageindex").length == 0) {
      return []
    }
    return this.getMessage(sourcePage, ' a');
  }

  getMessage(sourcePage: SourcePage, extraQuery: string) {
    const posts = [...sourcePage.rawHtml.matchAll(/id="(?<id>msg_[0-9]+)"/g)];

    const result: Element[] = [];
    for (const post of posts) {
      const id = assertNotBlank(post.groups?.id);
      // newer versions use div and headers, old versions use table and css markup
      const elem = getFirstMatch(
        sourcePage.$(`#${id}${extraQuery}`),
        'post/topic id ' + id
      );
      result.push(elem);
    }
    return result;
  }

  postProcessing(sourcePage: SourcePage, result: Result): void {
    for (const subpage of result.subpages) {
      subpage.url = subpage.url.replace(/\?PHPSESSID=[A-Za-z0-9]+/, '');
    }
  }
}
