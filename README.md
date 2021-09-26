# Xana Forum Scraper

This is a distributed scraper/spider to preserve forums using phpBB, vBulliten, etc before they
disappear from the internet. This tries to solve the following problems

* The [Internet Archive](https://web.archive.org) has large gaps of forum content in very deep page
  trees. This will grab every page of every topic. Hopefully we can upload our dataset there.
* Many posts linked to 3rd party image hosts that have purged content. This will map to images
  gathered from other imghost backups.
* Naive recursive wget or httrack backups grab too much data and are too slow. This focuses on
  fetching targeted topic content quickly using distributed scrapers.
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

# Architecture

Currently this uses Amazon AWS. See `aws-config.md`

Clients fetch from the server a per-domain list of URLs to download. Each client processes its list
then uploads the result to the server.

Saves date of download, HTTP headers, and raw byte level response.

Care is taken to avoid overloading servers with traffic. If you re-use this code please be mindful
of the target server's resources

## Interesting notes

Parser implementation history

* v1
  * NodeJS Typescript scraper using dom parser
  * Site-specific class/id anchor matching 
  * Problem: Expensive IPC between java and js
  * Problem: Confusing switching languages. JS determined to be unnecessary.
* v2
  * Rewrite as is in Java
  * Problem: Too complicated to use
* v3
  * Many new utils to assist grabbing elements. Added more sites
  * Use regex validated URLs
  * Problem: Element based design too rigid for other uses like auditing and url cleanup
  * Problem: Site-specific class/id are extremely variable even within same forum. Abandon idea
  * Problem: Not scalable, requiring complicated class for each site
  * Problem: Slow performance
* v4
  * Rewrite to entirely URL based. Process every link on the page
  * Use Java streams for pipeline based design
  * Problem: Requires class for each site, though less complicated
  * Problem: Steps are too specific and difficult to share
* v5
  * Rewrite to use JSON config system and more generic configurable parsing steps, 
  * Store site-specific config in database 
  * Split spider and parser functions
  