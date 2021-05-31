Xana Forum Scraper
---

This is a distributed scraper/spider to preserve forums like phpBB or vBulliten before they
disappear from the internet. This tries to solve the following problems

* The [Internet Archive](https://web.archive.org) has large gaps in content. This will ideally grab
  every page of every topic. Maybe we can assist them with this.
* Many users linked to 3rd party image hosts that have purged content. This will map images gathered
  from other imghost backups.
* Naive recursive wget or httrack backups grab too much data and are too slow. This focuses on
  fetching topic content quickly using distributed scrapers.
* Naive backups are not robust against various failure edge cases. This can identify errors and do
  data validation.
* (Optional) UI Frontend is not up to modern expectations. Some have tried to address this with
  plugins but not everyone installed them. Further, browsing via the Internet Archive directly isn't
  a great experience. This will allow re-parsing data into a better UI.

Goals in order of priority

* [x] Build distributed server/client download system
* [ ] Implement WARC format
* [x] Write parsers for various forum software and their custom themes.
* [ ] For posts with images, either download or map to existing images on the Internet Archive.
* [ ] Process avatars and signature images
* [ ] Write frontend browser
* [ ] Data will power a (to be written) modern forum frontend browser UI.

Data will be freely shared once a significant database is built.

Architecture
---

Currently this uses Amazon AWS. See `aws-config.md`

Saves date of download, HTTP headers, and raw byte level response.

Care is taken to avoid overloading servers with traffic. If you re-use this code please be mindful
of the target server's resources
