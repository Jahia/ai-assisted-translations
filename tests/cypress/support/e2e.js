// ***********************************************************
// This example support/index.js is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// Import commands.js using ES2015 syntax:
import './commands';
import { createHydrogenSite, createTestSite } from '../utils/helpers';
import { deleteSite } from "@jahia/cypress";
import { GENERIC_SITE_KEY, HYDROGEN_SITE_KEY, HYDROGEN_PREPACKAGED_SITE } from "./constants";

require('cypress-terminal-report/src/installLogsCollector')({
  xhr: {
    printHeaderData: true,
    printRequestData: true,
  },
  enableExtendedCollector: true,
  collectTypes: [
    'cons:log',
    'cons:info',
    'cons:warn',
    'cons:error',
    'cy:log',
    'cy:xhr',
    'cy:request',
    'cy:intercept',
    'cy:command',
  ],
});

require('@jahia/cypress/dist/support/registerSupport').registerSupport();
Cypress.on('uncaught:exception', () => {
  // Returning false here prevents Cypress from failing the test
  return false;
});

before('Create test site', () => {
  // use separate hooks for hydrogen and generic sites to avoid creating unnecessary data
  // if (Cypress.spec.relative.includes('hydrogen-tutorial')) {
  //   deleteSite(HYDROGEN_SITE_KEY);
  //   createHydrogenSite(HYDROGEN_SITE_KEY, HYDROGEN_PREPACKAGED_SITE);
  // } else {
  //   deleteSite(GENERIC_SITE_KEY);
  //   createTestSite(GENERIC_SITE_KEY);
  // }
});
