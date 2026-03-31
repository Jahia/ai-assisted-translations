/*  eslint-disable @typescript-eslint/no-explicit-any */
import { createSite, deleteSite, addNode, publishAndWaitJobEnding } from "@jahia/cypress";

export const addSimplePage = (
  parentPathOrId: string,
  pageName: string,
  pageTitle: string,
  language: string,
  template = "home",
  children = [],
  mixins = [],
  properties = [],
): any => {
  const variables = {
    parentPathOrId: parentPathOrId,
    name: pageName,
    title: pageTitle,
    primaryNodeType: "jnt:page",
    template: template,
    mixins: mixins,
    properties: [
      ...properties,
      { name: "jcr:title", value: pageTitle, language: language },
      { name: "j:templateName", type: "STRING", value: template },
    ],
    children: children,
  };
  return addNode(variables);
};

export const addEventPageAndEvents = (
  siteKey: string,
  template: string,
  pageName: string,
  thenFunction: () => void,
) => {
  return addSimplePage(`/sites/${siteKey}/home`, pageName, "Events page", "en", template, [
    {
      name: "events",
      primaryNodeType: "jnt:contentList",
    },
  ]).then(() => {
    const today = new Date();
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);

    addEvent(siteKey, {
      pageName,
      name: "event-a",
      title: "The first event",
      startDate: today,
      endDate: tomorrow,
    });
    addEvent(siteKey, {
      pageName,
      name: "event-b",
      title: "The second event",
      startDate: today,
    });
    if (thenFunction) {
      thenFunction();
    }
  });
};

export const addEvent = (siteKey: string, event) => {
  addNode({
    parentPathOrId: event.parentPath
      ? event.parentPath
      : `/sites/${siteKey}/home/${event.pageName}/events`,
    name: event.name,
    primaryNodeType: "jnt:event",
    properties: [
      { name: "jcr:title", value: event.title, language: "en" },
      { name: "startDate", type: "DATE", value: event.startDate },
      { name: "endDate", type: "DATE", value: event.endDate },
      { name: "eventsType", value: event.eventsType ? event.eventsType : "meeting" },
    ],
  });
};

export const createHydrogenSite = (siteKey: string, prepackagedSiteURL: string) => {
  cy.log("Creating sample site " + siteKey + "...");
  cy.log("Cypress prepackaged site URL", prepackagedSiteURL);

  if (prepackagedSiteURL && prepackagedSiteURL.startsWith("jar:mvn:")) {
    // the prepackaged site should be fetched from a Maven URL
    cy.runProvisioningScript({
      script: {
        fileContent: `- importSite: "${prepackagedSiteURL}"`,
        type: "application/yaml",
      },
    }).then(() => publishAndWaitJobEnding(`/sites/${siteKey}`, ["en"]));
  } else {
    // otherwise, assume it's a glob filename related to the ./artifacts/ folder
    cy.log(`Unzipping ${prepackagedSiteURL}...`);
    const prepackaged_archive_path = "META-INF/prepackagedSites/hydrogen-prepackaged.zip";
    cy.task("unzipArtifact", {
      artifactFilename: prepackagedSiteURL,
      filteredPath: prepackaged_archive_path,
    })
      .then(() => {
        cy.log(`Extracting site.zip from  ${prepackaged_archive_path}...`);
        return cy.task("unzipArtifact", {
          artifactFilename: prepackaged_archive_path,
          filteredPath: "site.zip",
        });
      })
      .then(() => {
        cy.log("Importing site.zip...");
        const site_archive_path = "../artifacts/site.zip";
        return cy.runProvisioningScript({
          script: {
            fileContent: `- importSite: "${site_archive_path}"`,
            type: "application/yaml",
          },
          files: [{ fileName: site_archive_path }],
        });
      })
      .then(() => {
        cy.log(`Publishing site '${siteKey}'...`);
        publishAndWaitJobEnding(`/sites/${siteKey}`, ["en"]);
      });
  } 
}

export const createTestSite = (siteKey: string) => {
  cy.step('Cleanup previous state: delete site', () => {
    deleteSite(siteKey);
  });

  cy.step(`Create test site: ${siteKey}`, () => {
    createSite(siteKey, {
      languages: 'en',
      templateSet: 'javascript-modules-engine-test-module',
      locale: 'en',
      serverName: 'localhost',
    });

    addSimplePage(`/sites/${siteKey}/home`, 'testPage', 'testPage', 'en', 'simple', [
      {
        name: 'pagecontent',
        primaryNodeType: 'jnt:contentList',
      },
    ]).then(() => {
      addNode({
        parentPathOrId: `/sites/${siteKey}/home/testPage/pagecontent`,
        name: 'test',
        primaryNodeType: 'javascriptExample:test',
        properties: [
          { name: 'jcr:title', value: 'test component' },
          { name: 'prop1', value: 'prop1 value' },
          { name: 'propMultiple', values: ['value 1', 'value 2', 'value 3'] },
          {
            name: 'propRichText',
            value: '<p data-testid="propRichTextValue">Hello this is a sample rich text</p>',
          },
        ],
      });
    });
  });
}
