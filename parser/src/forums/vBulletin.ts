import {
  anchorIsNavNotLink,
  assertNotBlank,
  ForumType,
  getFirstMatch,
  Result,
  SourcePage,
} from '../utils';
import {AbstractForum} from './AbstractForum';
import type {Element} from 'domhandler';

export class vBulletin implements AbstractForum {
  detectForumType(sourcePage: SourcePage): ForumType | null {
    // js init function they all seem to call
    if (sourcePage.rawHtml.indexOf('vBulletin_init();') === -1) {
      return null;
    } else {
      return ForumType.vBulletin;
    }
  }

  detectLoginRequired(sourcePage: SourcePage): boolean {
    return (
      sourcePage.rawHtml.indexOf(
        '<!-- permission error message - user not logged in -->'
      ) !== -1
    );
  }

  getPageLinks(sourcePage: SourcePage): Element[] {
    const result: Element[] = [];

    for (const elem of sourcePage.$('.pagenav a')) {
      if (anchorIsNavNotLink(elem)) {
        continue;
      }
      result.push(elem);
    }

    // infinite scroll
    for (const elem of sourcePage.$("link[rel='next']")) {
      if (anchorIsNavNotLink(elem)) {
        continue;
      }
      result.push(elem);
    }

    return result;
  }

  getPostElements(sourcePage: SourcePage): Element[] {
    const posts = [
      ...sourcePage.rawHtml.matchAll(/id="(?<id>td_post_[0-9]+)"/g),
    ];

    const result: Element[] = [];
    for (const post of posts) {
      const id = assertNotBlank(post.groups?.id);
      // newer versions use div and headers, old versions use table and css markup
      const elem = getFirstMatch(
        sourcePage,
        sourcePage.$(`#${id}`),
        'post id ' + id
      );
      result.push(elem);
    }
    return result;
  }

  getSubforumAnchors(sourcePage: SourcePage): Element[] {
    const forums = [...sourcePage.rawHtml.matchAll(/id="(?<id>f[0-9]+)"/g)];

    // forum list
    const result: Element[] = [];
    for (const forum of forums) {
      const id = assertNotBlank(forum.groups?.id);
      // newer versions use div and headers, old versions use table and css markup
      // cannot just use ID matching because sponsored links are underneath the parent div/td
      // also ignore extra matches since it may match subforums
      const elem = getFirstMatch(
        sourcePage,
        sourcePage.$(
          `div[id='${id}'] h2 a, div[id='${id}'] h3 a, td[id='${id}'] a`
        ),
        'forum id ' + id,
        true
      );
      result.push(elem);
    }

    return result;
  }

  getTopicAnchors(sourcePage: SourcePage): Element[] {
    const topics = [
      ...sourcePage.rawHtml.matchAll(/id="(?<id>thread_title_[0-9]+)"/g),
    ];

    // topic list
    const result: Element[] = [];
    for (const topic of topics) {
      const id = assertNotBlank(topic.groups?.id);
      const elem = getFirstMatch(
        sourcePage,
        sourcePage.$(`#${id}`),
        'topic id ' + id
      );
      result.push(elem);
    }
    return result;
  }

  postProcessing(sourcePage: SourcePage, result: Result): void {
    for (const subpage of result.subpages) {
      /*
      The marketplace module seems to use either client js or cookies for state tracking.
      But if your not a browser, it falls back tacking on the next page to end of the url
      eg page-50/page-70 to page-50/page-70/page-90 to page page-50/page-70/page-90/page-110

      Sometimes a custom id is also added (but not on my browser?), which similarly infinitely spans

      Not only is this url useless to archive, it causes "Data too long for column" SQL errors
      */
      let newUrl = subpage.url;
      while (true) {
        // match s=[32 character hex id]
        newUrl = newUrl.replace(/s=[0-9a-zA-Z]{32}&*/, '');
        // match double directory separator // but not the http:// one
        newUrl = newUrl.replace(/(?<!http[s]*:)\/\//g, '/');

        // match first duplicate of /page-50/page-70/
        const pages = newUrl.match(/(?<first>\/page-[0-9]+\/)page-[0-9]+\//);
        const firstPage = pages?.groups?.first;
        if (firstPage !== undefined) {
          newUrl = newUrl.replace(firstPage, '/');
        }

        if (newUrl != subpage.url) {
          subpage.url = newUrl;
        } else {
          break;
        }
      }

      /*
      The infinite scroll plugin uses a ?ispreloading magic url
      */
      subpage.url = subpage.url.replace('?ispreloading=1', '');
    }
  }
}
