import {
    addNode, Button,
    createSite,
    createUser,
    deleteSite, Dropdown,
    enableModule,
    getComponent,
    getComponentByRole,
    grantRoles
} from '@jahia/cypress';
import {TranslateEditor} from '@jahia/jcontent-cypress/dist/page-object/translateEditor';
import {ContentEditor, JContent, JContentPageBuilder} from '@jahia/jcontent-cypress/dist/page-object';
import {Field, SmallTextField} from '@jahia/jcontent-cypress/dist/page-object/fields';
import {Menu} from "@jahia/cypress";

describe('translate action tests', () => {
    const siteKey = 'translateSite';
    const oneLangSite = 'oneLangSite';
    const editorLogin = {username: 'translateEditor', password: 'password'};

    const parentPath = `/sites/${siteKey}/contents`;
    const name = 'translate-field-test';

    before('test setup', () => {
        createSite(siteKey, {
            languages: 'en,fr,de',
            templateSet: 'dx-base-demo-templates',
            serverName: 'localhost',
            locale: 'en'
        });

        enableModule('qa-module', siteKey);
        enableModule('ai-assisted-translations', siteKey);

        addNode({
            parentPathOrId: `/sites/${siteKey}/home`,
            primaryNodeType: 'jnt:contentList',
            name: 'area-main',
            children: [],
            mixins: [],
            properties: []
        }).then(() => {
        addNode({
            parentPathOrId: `/sites/${siteKey}/home/area-main`,
            primaryNodeType: 'qant:allFields',
            name,
            properties: [
                {name: 'smallText', value: 'a little text to be translated by our new ai assisted translation tool.', language: 'en'}
            ]
        })});

        addNode({
            parentPathOrId: `/sites/${siteKey}/contents`,
            primaryNodeType: 'qant:allFields',
            name,
            properties: [
                {name: 'smallText', value: 'smallText in English', language: 'en'},
                {name: 'textarea', value: 'textarea in English', language: 'en'}
            ]
        });

        createSite(oneLangSite, {
            languages: 'en',
            templateSet: 'dx-base-demo-templates',
            serverName: 'localhost',
            locale: 'en'
        });

        createUser(editorLogin.username, editorLogin.password);
        grantRoles(`/sites/${siteKey}`, ['editor'], editorLogin.username, 'USER');
    });

    after('test cleanup', () => {
        // deleteSite(siteKey);
        // deleteSite(oneLangSite);
    });

    beforeEach(() => {
        cy.loginAndStoreSession();
    });

    it('cannot open translate dialog if content has only one language', () => {
        const jcontent = JContent.visit(oneLangSite, 'en', 'pages/home');
        jcontent.getHeader().get().find('button[data-sel-role=\'requestTranslationAiAssistedForOnePage\']').should('not.exist');
    });

    it.only('can open translate dialog on page', () => {
        cy.log('Checking we can open teh dialog to translate all properties')
        const jcontent = JContent.visit(siteKey, 'fr', 'pages/home');
        jcontent.getHeaderActionButton('requestTranslationAiAssistedForOnePage').should('exist').click();
        cy.get('div[data-sel-role=\'translate-language-dialog\']').as('translateDialog').should('be.visible');
        const dropdown = getComponentByRole(Dropdown,'from-language-selector');
        dropdown.get().click();
        let menu = getComponent(Menu, dropdown);
        menu.get().find('.moonstone-menuItem').should('have.length', 3);
        menu.select('English')
        getComponentByRole(Button, 'translate-button').should('not.be.disabled').click();
        cy.get('@translateDialog').should('not.exist');
        const pageBuilder = new JContentPageBuilder(jcontent);
        pageBuilder.refresh();
        cy.wait(1000); // Wait for the translation to be applied
        let module = pageBuilder.getModule(`/sites/${siteKey}/home/area-main/${name}`, false);
        module.click();
        module.getBox().assertIsClicked()
        module.doubleClick();
        const contentEditor = ContentEditor.getContentEditor();
        contentEditor.getField(SmallTextField, 'qant:allFields_smallText').checkValue('un petit texte à traduire par notre nouvel outil de traduction assistée par IA.');
        contentEditor.cancel();

    });

    it.skip('can open translate dialog on pages and has the correct settings', () => {
        const translateEditor = TranslateEditor.visitPage(siteKey, 'en', 'pages/home', 'home');

        cy.log('Verify source language is default to English');
        translateEditor.getSourceLanguageSwitcher().isSelectedLang('en');
        translateEditor.getTranslateLanguageSwitcher().isNotSelectedLang('en');

        cy.log('Verify source column fields are read-only');
        translateEditor.getSourceFields()
            .each($field => new Field(cy.wrap($field)).isReadOnly());

        cy.log('Verify shared languages on translate column are read-only');
        translateEditor.getTranslateFields().filter('[data-sel-i18n="false"]')
            .each($field => new Field(cy.wrap($field)).isReadOnly());

        cy.log('Verify shared languages on source column do not contain translate fields button');
        translateEditor.getSourceFields().filter('[data-sel-i18n="false"]')
            .each($field => new Field(cy.wrap($field)).getTranslateFieldAction().should('not.exist'));

        cy.log('Verify translate fields are `disabled` on empty');
        [
            'htmlHead_jcr:description',
            'htmlHead_seoKeywords'
        ].forEach(fieldName => {
            translateEditor.getSourceField(SmallTextField, fieldName)
                .getTranslateFieldAction()
                .should('have.attr', 'disabled');
        });

        cy.log('Verify all sections are expanded by default');
        translateEditor.getSection('content').shouldBeExpanded();
        translateEditor.getSection('seo').shouldBeExpanded();
    });
});
