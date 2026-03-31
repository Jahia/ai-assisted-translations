import {
  addNode,
  createSite,
  deleteSite,
  enableModule,
  publishAndWaitJobEnding,
} from "@jahia/cypress";
import { addSimplePage } from "../../utils/helpers";

const testData = {
  translations: {
    en: {
      simple: "Hello !",
      composed: "This is a composed string: {{placeholder}}",
    },
    fr: {
      simple: "Salut !",
      composed: "Ceci est un test composé: {{placeholder}}",
    },
    fr_LU: {
      simple: "Salut !",
      composed: "Ceci est un test composé: {{placeholder}}",
    },
    de: {
      simple: "Hallo !",
      composed: "Dies ist eine zusammengesetzte Zeichenfolge: {{placeholder}}",
    },
  },
};

describe("Test i18n", () => {
  const createSiteWithContent = (
      siteKey: string,
      childrenNodes: {
        name: string;
        primaryNodeType: string;
        properties?: { name: string; value: string; language: string }[];
      }[],
  ) => {
    deleteSite(siteKey); // cleanup from previous test runs
    createSite(siteKey, {
      languages: "en,fr_LU,fr,de",
      templateSet: "javascript-modules-engine-test-module",
      locale: "en",
      serverName: "localhost",
    });
    enableModule("hydrogen", siteKey); // allow adding components from the "hydrogen" module

    addSimplePage(`/sites/${siteKey}/home`, "testPageI18N", "Test i18n en", "en", "simple", [
      {
        name: "pagecontent",
        primaryNodeType: "jnt:contentList",
      },
    ]).then(() => {
      cy.apollo({
        variables: {
          pathOrId: `/sites/${siteKey}/home/testPageI18N`,
          properties: [
            { name: "jcr:title", value: "Test i18n fr_LU", language: "fr_LU" },
            { name: "jcr:title", value: "Test i18n fr", language: "fr" },
            { name: "jcr:title", value: "Test i18n de", language: "de" },
          ],
        },
        mutationFile: "graphql/setProperties.graphql",
      });

      childrenNodes.forEach((childNode) => {
        addNode({
          parentPathOrId: `/sites/${siteKey}/home/testPageI18N/pagecontent`,
          name: childNode.name,
          primaryNodeType: childNode.primaryNodeType,
          properties: childNode.properties ?? [],
        });
      });
    });

    publishAndWaitJobEnding(`/sites/${siteKey}/home/testPageI18N`, ["en", "fr_LU", "fr", "de"]);
  };

  it("Test I18n values in various workspace/locales and various type of usage SSR/hydrate/rendered client side", () => {
    const siteKey = "javascriptI18NTestSite";
    createSiteWithContent(siteKey, [
      { name: "test", primaryNodeType: "javascriptExample:testI18n" },
    ]);

    cy.login();
    ["default"].forEach((workspace) => {
      ["en", "fr_LU", "fr", "de"].forEach((locale) => {
        cy.visit(`/cms/render/${workspace}/${locale}/sites/${siteKey}/home/testPageI18N.html`);
      });
      cy.get('[data-testid="getSiteLocales"]').should("contain", "de,en,fr,fr_LU");
    });
    cy.logout();

    deleteSite(siteKey);
  });

  const testI18n = (
      locale: string,
      mainSelector: string,
      placeholderIntialValue: string,
      placeholderShouldBeDynamic: boolean,
  ) => {
    cy.get(`${mainSelector} div[data-testid="i18n-simple"]`).should(
        "contain",
        testData.translations[locale].simple,
    );
    cy.get(`${mainSelector} div[data-testid="i18n-placeholder"]`).should(
        "contain",
        testData.translations[locale].composed.replace("{{placeholder}}", placeholderIntialValue),
    );

    const newPlaceholderValue =
        "Updated placeholder to test dynamic client side placeholder in i18n translation !";
    cy.get(`${mainSelector} input[data-testid="i18n-edit-placeholder"]`).clear();
    cy.get(`${mainSelector} input[data-testid="i18n-edit-placeholder"]`).type(newPlaceholderValue);
    if (placeholderShouldBeDynamic) {
      // Check that the placeholder is dynamic and it been have updated and translation is still good
      cy.get(`${mainSelector} div[data-testid="i18n-placeholder"]`).should(
          "contain",
          testData.translations[locale].composed.replace("{{placeholder}}", newPlaceholderValue),
      );
    } else {
      // Check that the placeholder didn't change, the component is not dynamic (not hydrated, not rendered client side)
      cy.get(`${mainSelector} div[data-testid="i18n-placeholder"]`).should(
          "contain",
          testData.translations[locale].composed.replace("{{placeholder}}", placeholderIntialValue),
      );
    }
  };
});
