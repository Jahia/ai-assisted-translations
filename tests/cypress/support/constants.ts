export const GENERIC_SITE_KEY = 'javascriptTestSite';
export const HYDROGEN_SITE_KEY = 'hydrogen';
export const HYDROGEN_PREPACKAGED_SITE='jar:mvn:org.jahia.samples/javascript-modules-samples-hydrogen-prepackaged/LATEST/zip!/site.zip';
export const HYDROGEN_POSTS = [
  {
    page: 'a-second-article',
    title: 'A second Article',
    subTitle: 'We love writing articles!',
    details: 'Written by Alice, Bob on March 28, 2025',
    extract: 'This is a great article',
  },
  {
    page: 'welcome-to-hydrogen-blog',
    title: 'Welcome to Hydrogen Blog',
    subTitle: 'This blog will keep you up to date with the latest news from our company',
    details: 'Written by Gautier on March 26, 2025',
    extract:
      '<b>Hydrogen</b>&nbsp;is a&nbsp;<a href="https://en.wikipedia.org/wiki/Chemical_element" title="Chemical element">chemical element</a>;',
  },
];
export const JAHIA_CONTEXT = new URL(
  Cypress.env('JAHIA_URL') ?? "http://localhost:8080"
).pathname.replace(/\/$/, "");
